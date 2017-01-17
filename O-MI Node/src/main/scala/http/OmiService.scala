/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package http

import java.nio.file.{Files, Paths}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, ExecutionContext, TimeoutException}
import scala.util.{Failure, Success, Try}
import scala.xml.{XML,NodeSeq}

import org.slf4j.LoggerFactory

import akka.util.ByteString
import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers._
import akka.http.scaladsl.marshalling.{Marshaller, ToResponseMarshallable}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directive0
import akka.stream.scaladsl._
import akka.stream._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.ws

import accessControl.AuthAPIService
import http.Authorization._
import parsing.OmiParser
import responses.{
  RequestHandler,
  RemoveSubscription,
  CallbackHandler,
  RESTHandler,
  RESTRequest,
  OmiRequestHandlerBase
}
import responses.CallbackHandler._
import types.OmiTypes._
import types.OmiTypes.Callback._
import types.{ParseError, Path}
import database.SingleStores

trait OmiServiceAuthorization
  extends ExtensibleAuthorization
     with LogPermissiveRequestBeginning // Log Permissive requests
     with IpAuthorization         // Write and Response requests for configured server IPs
     with SamlHttpHeaderAuth      // Write and Response requests for configured saml eduPersons
     with AllowNonPermissiveToAll // basic requests: Read, Sub, Cancel
     with AuthApiProvider         // Easier java api for authorization
     with LogUnauthorized         // Log everything else

/**
 * Actor that handles incoming http messages
 */
class OmiServiceImpl(
  protected val system : ActorSystem,
  protected val materializer: ActorMaterializer,
  protected val subscriptionManager : ActorRef,
  val settings : OmiConfigExtension,
  val singleStores : SingleStores,
  protected val requestHandler : OmiRequestHandlerBase,
  protected val callbackHandler : CallbackHandler
  )
     extends {
       // Early initializer needed (-- still doesn't seem to work)
       override val log = LoggerFactory.getLogger(classOf[OmiService])
  } with OmiService {

  //example auth API service code in java directory of the project
  //registerApi(new AuthAPIService())


}


/**
 * this trait defines our service behavior independently from the service actor
 */
trait OmiService
     extends CORSSupport
     with WebSocketOMISupport
     with OmiServiceAuthorization
     {


  protected def log: org.slf4j.Logger
  protected def requestHandler : OmiRequestHandlerBase
  protected def callbackHandler : CallbackHandler
  protected val system : ActorSystem
  import system.dispatcher

  //Get the files from the html directory; http://localhost:8080/html/form.html
  //this version words with 'sbt run' and 're-start' as well as the packaged version
  val staticHtml = if(Files.exists(Paths.get("./html"))){
    getFromDirectory("./html")
  } else getFromDirectory("O-MI Node/html")
  //val staticHtml = getFromResourceDirectory("html")


  /** Some trickery to extract the _decoded_ uri path: */
  def pathToString: Uri.Path => String = {
    case Uri.Path.Empty              => ""
    case Uri.Path.Slash(tail)        => "/"  + pathToString(tail)
    case Uri.Path.Segment(head, tail)=> head + pathToString(tail)
  }

  // Change default to xml mediatype and require explicit type for html
  val htmlXml = ScalaXmlSupport.nodeSeqMarshaller(MediaTypes.`text/html`)
  implicit val xmlCT = ScalaXmlSupport.nodeSeqMarshaller(MediaTypes.`text/xml`)

  // should be removed?
  val helloWorld = get {
     val document = { 
        <html>
        <body>
          <h1>Say hello to <i>O-MI Node service</i>!</h1>
          <ul>
            <li><a href="Objects">Url Data Discovery /Objects: Root of the hierarchy</a>
              <p>
                With url data discovery you can discover or request Objects,
                 InfoItems and values with HTTP Get request by giving some existing
                 path to the O-DF xml hierarchy.
              </p>
            </li>
            <li><a href="html/webclient/index.html">O-MI Test Client WebApp</a>
              <p>
                You can test O-MI requests here with the help of this webapp.
              </p>
            </li>
            <li><a href="html/ImplementationDetails.html">Implementation details, request-response examples</a>
              <p>
                Here you can view examples of the requests this project supports.
                These are tested against our server with <code>http.SystemTest</code>.
              </p>
            </li>
          </ul>
        </body>
        </html>
    }

    // XML is marshalled to `text/xml` by default
    complete(ToResponseMarshallable(document)(htmlXml))
  }

  val getDataDiscovery =
    path(Remaining) { uriPath =>
      get {
        // convert to our path type (we don't need very complicated functionality)
        val pathStr = uriPath // pathToString(uriPath)
        val path = Path(pathStr)

        RESTHandler.handle(path)(singleStores) match {
          case Some(Left(value)) =>
            complete(value)
          case Some(Right(xmlData)) =>
            complete(xmlData)
          case None =>            {
            log.debug(s"Url Discovery fail: org: [$pathStr] parsed: [$path]")

            // TODO: Clean this code
            complete(
              ToResponseMarshallable(
              <error>No object found</error>
              )(
                fromToEntityMarshaller(StatusCodes.NotFound)(xmlCT)
              )
            )
          }
        }
      }
    }

  def handleRequest(
    hasPermissionTest: PermissionTest,
    requestString: String,
    currentConnectionCallback: Option[Callback] = None,
    remote: RemoteAddress
  ): Future[NodeSeq] = {
    try {

      //val eitherOmi = OmiParser.parse(requestString)


      val originalReq = RawRequestWrapper(requestString, Some(remote))
      val ttlPromise = Promise[ResponseRequest]()
      originalReq.ttl match {
        case ttl: FiniteDuration => ttlPromise.completeWith(
          akka.pattern.after(ttl, using = system.scheduler) {
            log.info(s"TTL timed out after $ttl");
            Future.successful(Responses.TimeOutError())
          }
        )
        case _ => //noop
      }

      val responseF: Future[ResponseRequest] = hasPermissionTest(originalReq) match {
        case Success(req: RequestWrapper) => { // Authorized
           req.parsed match {
            case Right(requests) =>
              val unwrappedRequest = req.unwrapped // NOTE: Be careful when implementing multi-request messages
              unwrappedRequest match {
                case Success(request : OmiRequest) =>
                  defineCallbackForRequest(request, currentConnectionCallback).flatMap{
                    case request: OmiRequest => handleRequest( request )
                  }.recover{
                    case e: TimeoutException => Responses.TimeOutError(Some(e.getMessage()))
                    case e: IllegalArgumentException => Responses.InvalidRequest(Some(e.getMessage()))
                    case icb : InvalidCallback => Responses.InvalidCallback(icb.callback,Some(icb.message))
                    case t : Throwable =>
                      log.error("Internal Server Error: ",t)
                      Responses.InternalError(t)
                  }
                case Failure(t : Throwable)=>
                  log.error("Internal Server Error: ",t)
                  Future.successful( Responses.InternalError(t) )
              }
            case Left(errors) => { // Parsing errors found

              log.warn(s"${requestString}")
              log.warn("Parse Errors: {}", errors.mkString(", "))

              val errorResponse = Responses.ParseErrors(errors.toVector)

              Future.successful(errorResponse)
            }
          }
        }
        case Failure(e: UnauthorizedEx) => // Unauthorized
          Future.successful(Responses.Unauthorized())
        case Failure(pe: ParseError) =>
          val errorResponse = Responses.ParseErrors(Vector(pe))
          Future.successful(errorResponse)
        case Failure(ex) =>
          Future.successful(Responses.InternalError(ex))
      }



      // if timeoutfuture completes first then timeout is returned
      Future.firstCompletedOf(Seq(responseF, ttlPromise.future)) map {

        case response : ResponseRequest =>
          // check the error code for logging
          val statusO = response.results.map{ result => result.returnValue.returnCode}
          if (statusO exists (_ != "200")){
            log.warn(s"Error code $statusO with following request:\n${requestString}")
          }

          response.asXML // return
      }

    } catch {

      case ex: IllegalArgumentException => {
        log.debug(ex.getMessage)
        Future.successful(Responses.InvalidRequest(Some(ex.getMessage)).asXML)
      }
      case ex: Throwable => { // Catch fatal errors for logging
        log.error("Fatal server error", ex)
        throw ex
      }
    }
  }

  def handleRequest(request : OmiRequest ): Future[ResponseRequest ]= {
    request match {
      // Part of a fix to stop response request infinite loop (server and client sending OK to others' OK)
      case respRequest: ResponseRequest if respRequest.results.forall{ result => result.odf.isEmpty } =>
        Future.successful( Responses.NoResponse() ) 
      case sub: SubscriptionRequest => requestHandler.handle(sub)
      case other : OmiRequest => 
        request.callback match {
          case None => requestHandler.handle(other)
          case Some(callback: RawCallback) => 
            Future.successful(
              Responses.InvalidCallback(
                callback,
                Some("Callback 0 not supported with http/https try using ws(websocket) instead")
              )
            )
          case Some(callback: DefinedCallback) => {
            requestHandler.handle(other)  map { response =>
              callbackHandler.sendCallback( callback, response )
            }
            Future.successful(
              Responses.Success(description = Some("OK, callback job started"))
            )
          }
        }
    }
  }

  def defineCallbackForRequest(
    request: OmiRequest,
    currentConnectionCallback: Option[Callback] 
  ): Future[OmiRequest] = request.callback match {
    case None  => Future.successful( request )
    case Some(definedCallback : DefinedCallback ) => Future.successful( request )
    case Some(RawCallback("0")) if currentConnectionCallback.nonEmpty=>
      Future.successful( request.withCallback(  currentConnectionCallback ) )
    case Some(RawCallback("0")) if currentConnectionCallback.isEmpty=>
      Future.failed( InvalidCallback(RawCallback("0"), "Callback 0 not supported with http/https try using ws(websocket) instead" ) )
    case Some( RawCallback(address))  =>
      val cbTry = callbackHandler.createCallbackAddress(address)
      val result = cbTry.map{ 
        case callback =>
          request.withCallback( Some( callback ) )
      }.recoverWith{ 
        case throwable : Throwable =>
          Try{ throw InvalidCallback(RawCallback(address), throwable.getMessage(), throwable  ) }
      }
      Future.fromTry(result ) 
  }

  /** 
   * Receives HTTP-POST directed to root with o-mi xml as body. (Non-standard convenience feature)
   */
  val postXMLRequest = post {// Handle POST requests from the client
    makePermissionTestFunction() { hasPermissionTest =>
      entity(as[String]) {requestString =>   // XML and O-MI parsed later
        extractClientIP { user =>
          //val xmlH = XML.loadString("""<?xml version="1.0" encoding="UTF-8"?>""" )
          val response = handleRequest(hasPermissionTest, requestString, remote = user) //.map{ ns => xmlH ++ ns }
          //val marshal = ToResponseMarshallable(response)(Marshaller.futureMarshaller(xmlCT))
          complete(response)
        }
      }
    }
  }

  /**
   * Receives POST at root with O-MI compliant msg parameter.
   */
  val postFormXMLRequest = post {
    makePermissionTestFunction() { hasPermissionTest =>
      formFields("msg".as[String]) {requestString =>
        extractClientIP { user =>
          //val xmlH = XML.loadString("""<?xml version="1.0" encoding="UTF-8"?>""" )
          val response = handleRequest(hasPermissionTest, requestString, remote = user) //.map{ ns => xmlH ++ ns }
          val marshal = ToResponseMarshallable(response)(Marshaller.futureMarshaller(xmlCT))
          complete(response)
        }
      }
    }
  }

  // Combine all handlers
  val myRoute = corsEnabled {
    path("") {
      webSocketUpgrade ~
      postFormXMLRequest ~
      postXMLRequest ~
      helloWorld
    } ~
    pathPrefix("html") {
      staticHtml
    } ~
    pathPrefixTest("Objects") {
      getDataDiscovery
    }
  }
}

/**
 * This trait implements websocket support for O-MI message handling using akka-http
 */
trait WebSocketOMISupport { self: OmiService =>
  protected def system : ActorSystem
  protected implicit def materializer: ActorMaterializer
  protected def subscriptionManager : ActorRef
  import system.dispatcher
  type InSink = Sink[ws.Message, _]
  type OutSource = Source[ws.Message, SourceQueueWithComplete[ws.Message]]

  def webSocketUpgrade = //(implicit r: RequestContext): Directive0 =
    makePermissionTestFunction() { hasPermissionTest =>
      extractUpgradeToWebSocket {wsRequest =>
        extractClientIP { ip =>

          val (inSink, outSource) = createInSinkAndOutSource(hasPermissionTest, ip)
          complete(
            wsRequest.handleMessagesWithSinkSource(inSink, outSource)
          )
        }
      }
    }

  // Queue howto: http://loicdescotte.github.io/posts/play-akka-streams-queue/
  // T is the source type
  // M is the materialization type, here a SourceQueue[String]
  private def peekMatValue[T, M](src: Source[T, M]): (Source[T, M], Future[M]) = {
    val p = Promise[M]
    val s = src.mapMaterializedValue { m =>
      p.trySuccess(m)
      m
    }
    (s, p.future)
  }

  // akka.stream
  protected def createInSinkAndOutSource( hasPermissionTest: PermissionTest, user: RemoteAddress): (InSink, OutSource) = {
    val queueSize = settings.websocketQueueSize
    val (outSource, futureQueue) =
      peekMatValue(Source.queue[ws.Message](queueSize, OverflowStrategy.fail))

    // keepalive? http://doc.akka.io/docs/akka/2.4.8/scala/stream/stream-cookbook.html#Injecting_keep-alive_messages_into_a_stream_of_ByteStrings

    def queueSend(futureResponse: Future[NodeSeq]): Future[QueueOfferResult] = {
      val result = for {
        response <- futureResponse
        if (response.nonEmpty)

          queue <- futureQueue

        // TODO: check what happens when sending empty String
        resultMessage = ws.TextMessage(response.toString)
        queueResult <- queue offer resultMessage
      } yield {queueResult}

      def removeRelatedSub() = {
        futureResponse.map{ 
          response =>
            val ids = (response \\ "requestID").map{ 
              node =>
                node.text.toLong
            }
            ids.foreach{ 
              id =>
                subscriptionManager ! RemoveSubscription(id)
            }
        }
      }
      result onComplete {
        case Success(QueueOfferResult.Enqueued) => // Ok
        case Success(e: QueueOfferResult) => // Others mean failure
          log.warn(s"WebSocket response queue failed, reason: $e")
          removeRelatedSub()
        case Failure(e) => // exceptions
          log.warn("WebSocket response queue failed, reason: ", e)
          removeRelatedSub()
      }
      result
    }
    val connectionIdentifier = futureQueue.hashCode
    def sendHandler = (response: ResponseRequest ) => queueSend(Future(response.asXML)) map {_ => ()}
    val wsConnection = CurrentConnection(connectionIdentifier, sendHandler)
    def createZeroCallback = callbackHandler.createCallbackAddress("0",Some(wsConnection)).toOption

    val stricted = Flow.fromFunction[ws.Message,Future[String]]{
      case textMessage: ws.TextMessage =>
        textMessage.textStream.runFold("")(_+_)
      case msg: ws.Message => Future successful ""
    }
    val msgSink = Sink.foreach[Future[String]]{ future: Future[String]  => 
      future.flatMap{ 
        case requestString: String =>
        val futureResponse: Future[NodeSeq] = handleRequest(hasPermissionTest, requestString, createZeroCallback, user)
        queueSend(futureResponse)
      }
    }

    val inSink = stricted.to(msgSink)
    (inSink, outSource)
  }

}
