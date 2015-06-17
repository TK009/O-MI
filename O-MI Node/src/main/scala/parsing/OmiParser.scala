package parsing

import types._
import types.OmiTypes._
import types.OdfTypes._
import java.sql.Timestamp
import xmlGen.xmlTypes
import scala.xml._
import scala.util.Try
import scala.collection.mutable.Map
import java.text.SimpleDateFormat
import javax.xml.transform.stream.StreamSource
import scala.xml.Utility.trim
import org.xml.sax.SAXException
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList

/** Parsing object for parsing messages with O-MI protocol*/
object OmiParser extends Parser[OmiParseResult] {

   protected override def schemaPath = new StreamSource(getClass.getClassLoader().getResourceAsStream("omi.xsd"))

  /**
   * Public method for parsing the xml string into OmiParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-MI format.
   *  @return OmiParseResults
   */
  def parse(xml_msg: String): OmiParseResult = {
    /*Convert the string into scala.xml.Elem. If the message contains invalid XML, send correct ParseError*/
    val root = Try(
      XML.loadString(xml_msg)).getOrElse(
        return Left(Iterable(ParseError("OmiParser: Invalid XML"))))
    val schema_err = schemaValitation(root)
    if (schema_err.nonEmpty)
      return Left(schema_err.map { pe: ParseError => ParseError("OmiParser: " + pe.msg) })

    val envelope = xmlGen.scalaxb.fromXML[xmlTypes.OmiEnvelope](root)
    envelope.omienvelopeoption.value match {
      case read: xmlTypes.ReadRequest => parseRead(read, envelope.ttl)
      case write: xmlTypes.WriteRequest => parseWrite(write, envelope.ttl)
      case cancel: xmlTypes.CancelRequest => parseCancel(cancel, envelope.ttl)
      case response: xmlTypes.ResponseListType => parseResponse(response, envelope.ttl)
    }
  }
  private def parseRead(read: xmlTypes.ReadRequest, ttl: Double): OmiParseResult = {
    if (read.msg.isEmpty) {
      Right(Iterable(
        PollRequest(
          ttl,
          uriToStringOption(read.callback),
          read.requestId.map { id => id.value.toInt })))
    } else {
      val odf = parseMsg(read.msg, read.msgformat)
      val errors = OdfTypes.getErrors(odf)

      if (errors.nonEmpty)
        return Left(errors)

      if (read.interval.isEmpty) {
        Right(Iterable(
          ReadRequest(
            ttl,
            odf.right.get,
            gcalendarToTimestampOption(read.begin),
            gcalendarToTimestampOption(read.end),
            read.newest,
            read.oldest,
            uriToStringOption(read.callback))))
      } else {
        Right(Iterable(
          SubscriptionRequest(
            ttl,
            read.interval.get,
            odf.right.get,
            read.newest,
            read.oldest,
            uriToStringOption(read.callback))))
      }
    }
  }

  private def parseWrite(write: xmlTypes.WriteRequest, ttl: Double): OmiParseResult = {
    val odf = parseMsg(write.msg, write.msgformat)
    val errors = OdfTypes.getErrors(odf)

    if (errors.nonEmpty)
      return Left(errors)
    else
      Right(Iterable(
        WriteRequest(
          ttl,
          odf.right.get,
          uriToStringOption(write.callback))))
  }

  private def parseCancel(cancel: xmlTypes.CancelRequest, ttl: Double): OmiParseResult = {
    Right(Iterable(
      CancelRequest(
        ttl,
        cancel.requestId.map { id => id.value.toInt }.toIterable
      )
    ))
  }
  private def parseResponse(response: xmlTypes.ResponseListType, ttl: Double): OmiParseResult = {
    Right(Iterable(
      ResponseRequest(
        response.result.map {
          case result =>

            OmiResult(
              result.returnValue.value,
              result.returnValue.returnCode,
              result.returnValue.description,
              if (result.requestId.nonEmpty) {
                asJavaIterable(Iterable(result.requestId.get.value.toInt))
              } else {
                asJavaIterable(Iterable.empty[Int])
              },
              if (result.msg.isEmpty)
                None
              else {
                val odf = parseMsg(result.msg, result.msgformat)
                val errors = OdfTypes.getErrors(odf)
                if (errors.nonEmpty)
                  return Left(errors)
                else
                  Some(odf.right.get)
              })
        }.toIterable
      )
    ))
  }

  private def parseMsg(msg: Option[xmlGen.scalaxb.DataRecord[Any]], format: Option[String]): OdfParseResult = {
    if (msg.isEmpty)
      return Left(Iterable(ParseError("OmiParser: No msg element found in write request.")))
    
    if (format.isEmpty) 
      return Left(Iterable(ParseError("OmiParser: Missing msgformat attribute.")))

    val data = msg.get.as[Elem]
    format.get match {
      case "odf" =>
        val odf = (data \ "Objects")
        odf.headOption match {
          case Some(head) =>
            parseOdf(head)
          case None =>
            Left(Iterable(ParseError("No Objects child found in msg.")))
        }
      case _ =>
        Left(Iterable(ParseError("Unknown msgformat attribute")))
    }
  }
  private def parseOdf(node: Node): OdfParseResult = OdfParser.parse(node)
  private def gcalendarToTimestampOption(gcal: Option[javax.xml.datatype.XMLGregorianCalendar]): Option[Timestamp] = gcal match {
    case None => None
    case Some(cal) => Some(new Timestamp(cal.toGregorianCalendar().getTimeInMillis()));
  }
  private def uriToStringOption(opt: Option[java.net.URI]): Option[String] = opt match {
    case None => None
    case Some(uri) => Some(uri.toString)
  }
}


