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
package types
package OdfTypes

import java.lang.{Iterable => JavaIterable}

import scala.collection.mutable.{Map => MutableMap}
import scala.collection.JavaConverters._
import scala.language.existentials

import parsing.xmlGen.xmlTypes._

object OdfTreeCollection {
  def apply[T](): OdfTreeCollection[T] = Vector()
  def empty[T]: OdfTreeCollection[T] = Vector()
  def apply[T](elems: T*): OdfTreeCollection[T] = Vector(elems:_*)
  def fromIterable[T](elems: Iterable[T]): OdfTreeCollection[T] = elems.toVector
  def toJava[T](c: OdfTreeCollection[T]): java.util.List[T] = c.toBuffer.asJava
  def fromJava[T](i: java.lang.Iterable[T]): OdfTreeCollection[T] = fromIterable(i.asScala)
  import scala.language.implicitConversions
  implicit def seqToOdfTreeCollection[E](s: Iterable[E]): OdfTreeCollection[E] = OdfTreeCollection.fromIterable(s)
}

/** Sealed base trait defining all shared members of OdfNodes*/
sealed trait OdfNode {
  /** Member for storing path of OdfNode */
  def path: Path
  /** Member for storing description for OdfNode */
  def description: Option[OdfDescription]
  /** Method for searching OdfNode from O-DF Structure */
  def get(path: Path): Option[OdfNode]
  def createAncestors : OdfObjects = OdfTypes.createAncestors(this)
}

/** Class presenting O-DF Objects structure*/
case class OdfObjects(
  objects: OdfTreeCollection[OdfObject] = OdfTreeCollection(),
  version: Option[String] = None) extends OdfObjectsImpl(objects, version) with OdfNode {

  /** Method for searching OdfNode from O-DF Structure */
  def get(path: Path) : Option[OdfNode] = {
    if( path == this.path ) return Some(this)
    //HeadOption is because of values being OdfTreeCollection of OdfObject
    val grouped = objects.groupBy(_.path).mapValues{_.headOption.getOrElse(throw new Exception("Pathless Object was grouped."))}
    grouped.get(path) match {
      case None => 
        grouped.get(path.take(2)) match{
          case None => 
            None
          case Some(obj: OdfObject) =>
            obj.get(path)
       }
      case Some(obj) => Some(obj)
    }
  }

  def valuesRemoved: OdfObjects = this.copy(objects = objects map (_.valuesRemoved))
  def metaDatasRemoved: OdfObjects = this.copy(objects = objects map (_.metaDatasRemoved))
  def allMetaDatasRemoved: OdfObjects = this.copy(objects = objects map (_.allMetaDatasRemoved))
  def descriptionsRemoved: OdfObjects = this.copy(objects = objects map (_.descriptionsRemoved))
  //def withValues: (Path, Seq[OdfValue[Any]]) => OdfObjects = { case (p, v) => withValues(p, v) } 
  def withValues(p: Path, v: Seq[OdfValue[Any]]): OdfObjects = {
    this.copy(
      objects = objects map (o => if (o.path.isAncestor(p)) o.withValues(p,v) else o)
    )
  }
  def withValues(pathValuesPairs: Map[Path,OdfTreeCollection[OdfValue[Any]]]): OdfObjects = {
    this.copy(
      objects = objects map {
        case o : OdfObject=> 
          if (pathValuesPairs.keys.exists( p => o.path.isAncestor(p))) 
            o.withValues(pathValuesPairs) 
          else o
      }
    )
  }

  def update(
    spath: Path,
    values:               OdfTreeCollection[OdfValue[Any]] = OdfTreeCollection(),
    description:          Option[OdfDescription] = None,
    metaData:             Option[OdfMetaData] = None
  ): OdfObjects ={
    this.copy(
      objects = objects map (o => if (o.path.isAncestor(spath)) o.update(spath, values, description, metaData) else o)
    )
  }

  def update(
    spath: Path,
    description: Option[OdfDescription] 
  ): OdfObjects ={
    this.copy(
      objects = objects map (o => if (o.path.isAncestor(spath)) o.update(spath, description) else o)
    )
  }


  lazy val infoItems = getInfoItems(this)
  lazy val paths = infoItems map (_.path)

  /**
   * Returns Object nodes that have metadata-like information.
   * Includes nodes that have type or description
   */
  lazy val objectsWithMetadata = getOdfNodes(this) collect {
      case o @ OdfObject(_, _, _, _, desc, typeVal) if desc.isDefined || typeVal.isDefined => o
    } 

}

/** Class presenting O-DF Object structure*/
case class OdfObject(
  id: OdfTreeCollection[QlmID],
  path: Path,
  infoItems: OdfTreeCollection[OdfInfoItem] = OdfTreeCollection(),
  objects: OdfTreeCollection[OdfObject] = OdfTreeCollection(),
  description: Option[OdfDescription] = None,
  typeValue: Option[String] = None
  ) extends OdfObjectImpl(id, path, infoItems, objects, description, typeValue) with OdfNode with Serializable{


  def get(path: Path) : Option[OdfNode] = path match{
      case this.path => Some(this)
      case default : Path =>
    val haspaths = infoItems.toSeq.map{ item => item : OdfNode} ++ objects.toSeq.map{ item => item : OdfNode}
    val grouped = haspaths.groupBy(_.path).mapValues{_.headOption.getOrElse(OdfObjects())}
    grouped.get(path) match {
      case None => 
        grouped.get(path.take(this.path.length + 1)) match{
          case None => 
            None
          case Some(obj: OdfObject) =>
            obj.get(path)
          case Some(obj: OdfInfoItem) =>
            None
          case Some(obj: OdfObjects) =>
            None
       }
      case Some(obj: OdfObject) => Some(obj)
      case Some(obj: OdfObjects) =>
            None
      case Some(obj: OdfInfoItem) => Some(obj)
    }
  
  }
  def valuesRemoved: OdfObject = this.copy(
        objects   = objects map (_.valuesRemoved),
        infoItems = infoItems map (_.valuesRemoved)
      )
  def metaDatasRemoved: OdfObject = this.copy(
        objects   = objects map (_.metaDatasRemoved),
        infoItems = infoItems map (_.metaDatasRemoved)
      )
  def allMetaDatasRemoved: OdfObject = this.copy(
        objects   = objects map (_.allMetaDatasRemoved),
        infoItems = infoItems map (_.allMetaDatasRemoved),
        description = None
      )
  def descriptionsRemoved: OdfObject = this.copy(
        objects   = objects map (_.descriptionsRemoved),
        infoItems = infoItems map (_.descriptionsRemoved),
        description = None
      )
  def withValues(p: Path, v: Seq[OdfValue[Any]]): OdfObject = {
    val nextPath: Path = p.toSeq.tail
    require(nextPath.nonEmpty, s"Tried to set values for Object $path: $p -> $v")

    this.copy(
      objects = objects map (o => if (o.path == nextPath) o.withValues(p, v) else o),
      infoItems = infoItems map (i => if (i.path == nextPath) i.withValues(v) else i) 
    ) 
  }
  def withValues(pathValuesPairs: MutableMap[Path,OdfTreeCollection[OdfValue[Any]]]): OdfObject =  this.withValues(pathValuesPairs.toMap)
  def withValues(pathValuesPairs: Map[Path,OdfTreeCollection[OdfValue[Any]]]): OdfObject = {
    this.copy(
      objects = objects map (
        o => 
          if( pathValuesPairs.keys.exists( p => o.path.isAncestor(p) )) 
            o.withValues(pathValuesPairs) 
          else o),
      infoItems = infoItems map (
        i => 
          if( pathValuesPairs.keys.exists(p => i.path == p)) 
            i.withValues( pathValuesPairs.getOrElse(i.path, OdfTreeCollection.empty) )
          else i) 
    )
  }
  def update(
    spath: Path,
    values:               OdfTreeCollection[OdfValue[Any]] = OdfTreeCollection(),
    description:          Option[OdfDescription] = None,
    metaData:             Option[OdfMetaData] = None
  ): OdfObject ={
    this.copy(
      objects = objects map (o => if (o.path.isAncestor(spath)) o.update(spath, values, description, metaData) else o),
      infoItems = infoItems map (i => if (i.path == spath) i.update( values, description, metaData) else i) 
    ) 
  }
  def update(
    spath: Path,
    description:          Option[OdfDescription] 
  ): OdfObject ={
    this.copy(
      objects = objects map (o => if (o.path.isAncestor(spath)) o.update(spath, description) else o),
      infoItems = infoItems map (i => if (i.path == spath) i.update(  description = description) else i),
      description = if( this.path == spath ) description else this.description
    ) 
  }

}
  
/** Class presenting O-DF InfoItem structure*/
case class OdfInfoItem(
    path: Path,
    values: OdfTreeCollection[OdfValue[Any]] = OdfTreeCollection(),
    description: Option[OdfDescription] = None,
    metaData: Option[OdfMetaData] = None)
  extends OdfInfoItemImpl(path, values, description, metaData) with OdfNode {
  require(path.length > 2,
    s"OdfInfoItem should have longer than two segment path (use OdfObjects for <Objects>): Path($path)")
  def get(path: Path): Option[OdfNode] = if (path == this.path) Some(this) else None
  def valuesRemoved: OdfInfoItem = if (values.nonEmpty) this.copy(values = OdfTreeCollection()) else this
  def descriptionsRemoved: OdfInfoItem = if (description.nonEmpty) this.copy(description = None) else this
  def metaDatasRemoved: OdfInfoItem = if (metaData.nonEmpty) this.copy(metaData = None) else this
  def allMetaDatasRemoved: OdfInfoItem = this.copy(metaData = None, description = None)
  def withValues(v: Seq[OdfValue[Any]]): OdfInfoItem = this.copy(values = OdfTreeCollection(v:_*))
  def update(
    values:               OdfTreeCollection[OdfValue[Any]] = OdfTreeCollection(),
    description:          Option[OdfDescription] = None,
    metaData:             Option[OdfMetaData] = None
  ): OdfInfoItem ={
    this.copy( 
      values = if( values.nonEmpty ) values else this.values,
      description = if( description.nonEmpty ) description else this.description,
      metaData = if( metaData.nonEmpty ) metaData else this.metaData
    )
  }

  /** 
   * Method for reducing values to newest
   */
  def withNewest : OdfInfoItem ={
    this.copy( values = this.values.sortBy{ v => v.timestamp.getTime }.lastOption.toVector )
  }

}

/** Class presenting O-DF description element*/
case class OdfDescription(
    value: String,
    lang: Option[String] = None) {
  implicit def asDescription : Description= Description(value, lang, Map.empty)
}

