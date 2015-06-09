package parsing
package Types

import xmlGen._
import xml.XML
import java.sql.Timestamp
import java.lang.Iterable
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList


object OdfTypes{

  sealed trait OdfElement

  case class OdfObjects(
    objects:              Iterable[OdfObject] = asJavaIterable(Seq.empty[OdfObject]),
    version:              Option[String] = None
  ) extends OdfElement {
    def combine( another: OdfObjects ): OdfObjects ={
      val uniques : Seq[OdfObject]  = ( 
        objects.filterNot( 
          obj => another.objects.toSeq.exists( 
            aobj => aobj.path  == obj.path 
          ) 
      ).toSeq ++ 
      another.objects.filterNot(
        aobj => objects.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
      )
      val sames = (objects.toSeq ++ another.objects.toSeq).filterNot(
        obj => uniques.exists(
          uobj => uobj.path == obj.path
        )
      ).groupBy(_.path)
      OdfObjects(
        sames.map{ case (path:Path, sobj: Seq[OdfObject]) =>
          assert(sobj.length == 2)
          sobj.head.combine(sobj.last)
        }.toSeq ++ uniques,
        (version, another.version) match{
          case (Some(a), Some(b)) => Some(a)
          case (None, Some(b)) => Some(b)
          case (Some(a), None) => Some(a)
          case (None, None) => None
        }
      )
    }
  }

  def OdfObjectsAsObjectsType( objects: OdfObjects ) : ObjectsType ={
    ObjectsType(
      Object = objects.objects.map{
        obj: OdfObject => 
        OdfObjectAsObjectType( obj )
      }.toSeq,
      objects.version
    )
  }
  
  case class OdfObject(
    path:                 Path,
    infoItems:            Iterable[OdfInfoItem],
    objects:              Iterable[OdfObject],
    description:          Option[OdfDescription] = None,
    typeValue:            Option[String] = None
  ) extends OdfElement with HasPath {
    def combine( another: OdfObject ) : OdfObject = {
      assert( path == another.path )
      val uniqueInfos = ( 
        infoItems.filterNot( 
          obj => another.infoItems.toSeq.exists( 
            aobj => aobj.path  == obj.path 
          ) 
      ).toSeq ++ 
      another.infoItems.filterNot(
        aobj => infoItems.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
      )
      val sameInfos = (infoItems.toSeq ++ another.infoItems.toSeq).filterNot(
        obj => uniqueInfos.exists(
          uobj => uobj.path == obj.path
        )
      ).groupBy(_.path)
      val uniqueObjs = ( 
        objects.filterNot( 
          obj => another.objects.toSeq.exists( 
            aobj => aobj.path  == obj.path 
          ) 
      ).toSeq ++ 
      another.objects.filterNot(
        aobj => objects.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
      )
      val sameObjs = (objects.toSeq ++ another.objects.toSeq).filterNot(
        obj => uniqueObjs.exists(
          uobj => uobj.path == obj.path
        )
      ).groupBy(_.path)
      OdfObject(
        path, 
        sameInfos.map{ case (path:Path, sobj: Seq[OdfInfoItem]) =>
          assert(sobj.length == 2)
          sobj.head.combine(sobj.last)
        }.toSeq ++ uniqueInfos,
        sameObjs.map{ case (path:Path, sobj: Seq[OdfObject]) =>
          assert(sobj.length == 2)
          sobj.head.combine(sobj.last)
        }.toSeq ++ uniqueObjs,
        (description, another.description) match{
          case (Some(a), Some(b)) => Some(a)
          case (None, Some(b)) => Some(b)
          case (Some(a), None) => Some(a)
          case (None, None) => None
        },
        (typeValue, another.typeValue) match{
          case (Some(a), Some(b)) => Some(a)
          case (None, Some(b)) => Some(b)
          case (Some(a), None) => Some(a)
          case (None, None) => None
        }
      )
    }
  }

  def OdfObjectAsObjectType(obj: OdfObject) : ObjectType = {
    ObjectType(
      Seq( QlmID(
          obj.path.last,
          attributes = Map.empty
      )),
      InfoItem = obj.infoItems.map{ 
        info: OdfInfoItem =>
          OdfInfoItemAsInfoItemType( info )
        }.toSeq,
      Object = obj.objects.map{ 
        subobj: OdfObject =>
        OdfObjectAsObjectType( subobj )
      }.toSeq,
      attributes = Map.empty
    )
  }

  case class OdfInfoItem(
    path:                 Types.Path,
    values:               Iterable[OdfValue],
    description:          Option[OdfDescription] = None,
    metaData:             Option[OdfMetaData] = None
  ) extends OdfElement with HasPath {
    def combine(another: OdfInfoItem) : OdfInfoItem ={
      assert(path == another.path)
      OdfInfoItem(
        path,
        (values ++ another.values).toSeq.distinct,
        (description, another.description) match{
          case (Some(a), Some(b)) => Some(a)
          case (None, Some(b)) => Some(b)
          case (Some(a), None) => Some(a)
          case (None, None) => None
        },
        (metaData, another.metaData) match{
          case (Some(a), Some(b)) => Some(a)
          case (None, Some(b)) => Some(b)
          case (Some(a), None) => Some(a)
          case (None, None) => None
        }
      )
    }
  }

  def OdfInfoItemAsInfoItemType(info: OdfInfoItem) : InfoItemType = {
    InfoItemType(
      name = info.path.last,
      value = info.values.map{ 
        value : OdfValue =>
        ValueType(
          value.value,
          value.typeValue,
          unixTime = Some(value.timestamp.get.getTime/1000),
          attributes = Map.empty
        )
      },
      MetaData = 
        if(info.metaData.nonEmpty)
          Some( scalaxb.fromXML[MetaData]( XML.loadString( info.metaData.get.data ) ) )
        else 
          None
      ,
      attributes = Map.empty
    )
  }
  case class OdfMetaData(
    data:                 String
  ) extends OdfElement

  case class OdfValue(
    value:                String,
    typeValue:            String = "",
    timestamp:            Option[Timestamp] = None
  ) extends OdfElement

  case class OdfDescription(
    value:                String,
    lang:                 Option[String] = None
  ) extends OdfElement
  
  type  OdfParseResult = Either[Iterable[ParseError], OdfObjects]
  def getObjects( odf: OdfParseResult ) : Iterable[OdfObject] = 
    odf match{
      case Right(objs: OdfObjects) => objs.objects
      case _ => asJavaIterable(Seq.empty[OdfObject])
    }
  def getErrors( odf: OdfParseResult ) : Iterable[ParseError] = 
    odf match {
      case Left(pes: Iterable[ParseError]) => pes
      case _ => asJavaIterable(Seq.empty[ParseError])
    }

  def getLeafs(objects: OdfObjects ) : Iterable[HasPath] = {
    def getLeafs(obj: OdfObject ) : Iterable[HasPath] = {
      if(obj.infoItems.isEmpty && obj.objects.isEmpty)
        scala.collection.Iterable(obj)
      else 
        obj.infoItems ++ obj.objects.flatMap{          
          subobj =>
            getLeafs(subobj)
        } 
    }
    objects.objects.flatMap{
      obj => getLeafs(obj)
    }
  }
  trait HasPath {
    def path: Path
  }
  
  def fromPath( last: HasPath) : OdfObjects = {
    var path = last.path.dropRight(1)
    var obj = last match{
      case info: OdfInfoItem =>
        OdfObject( path, asJavaIterable(Seq(info)), asJavaIterable(Seq.empty) )  
      case obj: OdfObject =>
        OdfObject( path, asJavaIterable(Seq.empty), asJavaIterable(Seq(obj)))  
    }
    while( path.length > 1){
      path = path.dropRight(1)
      obj = OdfObject(  path, asJavaIterable(Seq.empty ), asJavaIterable( Seq( obj ) ) )
    }
    OdfObjects( asJavaIterable( Seq( obj ) ) )
  }
}
