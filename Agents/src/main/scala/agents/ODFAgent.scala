package agents

import agentSystem._ 
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.{Cancellable, Props}
import parsing.OdfParser
import types.Path
import types.Path._
import types.OdfTypes._
import types.OmiTypes.{WriteRequest, ResponseRequest, OmiResult, Results}
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable}
import scala.collection.mutable.{Queue => MutableQueue}
import scala.xml._
import scala.util.{Random, Try, Success, Failure}
import java.util.concurrent.TimeUnit
import java.util.Date
import java.sql.Timestamp;
import java.io.File
import com.typesafe.config.Config

object ODFAgent extends PropsCreator{
  def props(config: Config) : Props = Props( new ODFAgent(config) )
}
// Scala XML contains also parsing package
class ODFAgent( override val config: Config) extends ScalaInternalAgent {
   val interval : FiniteDuration= config.getDuration("interval", TimeUnit.SECONDS).seconds
   val odfQueue : MutableQueue[OdfObjects]= MutableQueue()
  
  import scala.concurrent.ExecutionContext.Implicits._
  case class Update()
	
  val rnd: Random = new Random()
  def date = new java.util.Date();

  private val  updateSchelude : MutableQueue[Cancellable] = MutableQueue.empty
  def start = {
    val file =  new File(config.getString("file"))
    if( file.exists() && file.canRead() ){
      val xml = XML.loadFile(file)
      OdfParser.parse( xml) match {
        case Left( errors ) =>
          val msg = errors.mkString("\n")
          log.warning(s"Odf has errors, $name could not be configured.")
          log.debug(msg)
          StartFailed(msg, None)
        case Right(odfObjects) =>
        odfQueue.enqueue(odfObjects)
        // Schelude update and save job, for stopping
        // Will send Update message to self every interval
        updateSchelude.enqueue(context.system.scheduler.schedule(
          Duration(0, SECONDS),
          interval,
          self,
          Update()
        ))
        CommandSuccessful()
      }
    } else {
      val msg = s"File $config did not exists or could not read it. $name could not be configured."
      log.warning(msg)
      StartFailed(msg, None)
    }
  }

   def update() : Unit = {
    log.debug(s"$name pushing data.")
    odfQueue.dequeueFirst{o: OdfObjects => true}
    .foreach{
      objects =>
      val infoItems = getInfoItems(objects)

      // Collect metadata 
      val objectsWithMetaData = getOdfNodes(objects) collect {
        case o @ OdfObject(_, _, _, _, desc, typeVal) if desc.isDefined || typeVal.isDefined => o
      }   
      val updated = infoItems.map{ infoItem => 
          val newVal = infoItem.path.lastOption match {
            case Some( name ) => 
            infoItem.values.lastOption match {
              case Some(oldVal: OdfDoubleValue) => genValue(name, oldVal.value)
              case Some( oldVal ) => -1000.0
              case None =>  -1000.0
            }
            case None => -1000.0
          }
        infoItem.copy( values =Vector(OdfValue(
          newVal.toString, 
          "xs:double",
          new Timestamp( date.getTime() )
        )))
      }
      val allNodes = updated ++ objectsWithMetaData
      val newObjects = allNodes.map(createAncestors(_)).foldLeft(OdfObjects())(_.union(_))
      
      implicit val timeout = Timeout(interval)
      val write = WriteRequest( newObjects, None, interval)
      val result = (agentSystem ? ResponsibilityRequest(name, write)).mapTo[ResponseRequest]
      result.onComplete{
        case Success( response: ResponseRequest )=>
          response.results.foreach{ 
            case wr: Results.Success =>
              // This sends debug log message to O-MI Node logs if
              // debug level is enabled (in logback.xml and application.conf)
              log.debug(s"$name wrote paths successfully.")
            case ie: OmiResult => 
              log.warning(s"Something went wrong when $name writed, $ie")
          }
            case Failure( t: Throwable) => 
              // This sends debug log message to O-MI Node logs if
              // debug level is enabled (in logback.xml and application.conf)
              log.warning(s"$name's write future failed, error: $t")
      }
      odfQueue.enqueue(newObjects)
    } 
  }

  override  def receive  = {
    case Start() => sender() ! start 
    case Restart() => sender() ! restart 
    case Stop() => sender() ! stop 
    case Update() => update()
  }
   def stop = {
    updateSchelude.dequeueFirst{ j => true } match{
      //If agent has scheluded update, cancel job
      case Some(job) =>
      job.cancel() 
      //Check if job was cancelled
      job.isCancelled  match {
      case true =>
        CommandSuccessful()
      case false =>
        StopFailed("Failed to stop agent.", None)
    }
    case None => 
        CommandSuccessful()
  }}
  
  private def genValue(sensorType: String, oldval: Double ) : String = {
    val newval = (sensorType match {
      case "temperature" => between( 18, oldval + Random.nextGaussian * 0.3, 26)
      case "light" => between(100, oldval + Random.nextGaussian, 2500)
      case "co2" => between(400, oldval + 20 * Random.nextGaussian, 1200)
      case "humidity" => between(40, oldval + Random.nextGaussian, 60)
      case "pir" => Random.nextInt % 2
      case _ => Random.nextInt % 2
    })
    f"$newval%.1f".replace(',', '.')
  }
  private def between( begin: Double, value: Double, end: Double ) : Double = {
    (begin <= value, value <= end) match {
      case (false, true) =>
        begin
      case (true, true) =>
        value
      case (true, false) =>
        end
      case (false, false) =>
        Double.NaN
    }
  }
}
