package types


import java.lang.{Iterable => JavaIterable}

import parsing.xmlGen.xmlTypes._

import scala.collection.JavaConverters._
import scala.language.existentials
import OdfTypes._
/**
 * Package containing classes presenting O-MI request interanlly. 
 *
 */
package object OmiTypes  {
  type  OmiParseResult = Either[Iterable[ParseError], Iterable[OmiRequest]]
  def getPaths(request: OdfRequest): Seq[Path] = getLeafs(request.odf).map{ _.path }.toSeq
}
/**
 * Package containing classes presenting O-DF format internally and helper methods for them
 *
 */
package object OdfTypes {
  type OdfParseResult = Either[JavaIterable[ParseError], OdfObjects]

  /**
   * Collection type to be used as all children members in odf tree types
   */
  type OdfTreeCollection[T] = Vector[T]

  def unionOption[T](a: Option[T], b: Option[T])(f: (T,T) => T): Option[T] = {
    (a,b) match{
        case (Some(a), Some(b)) => Some(f(a,b))
        case (None, Some(b)) => Some(b)
        case (Some(a), None) => Some(a)
        case (None, None) => None
    }
  }

  
  /** Helper method for getting all leaf nodes of O-DF Structure */
  def getLeafs(obj: OdfObject): OdfTreeCollection[OdfNode] = {
    if (obj.infoItems.isEmpty && obj.objects.isEmpty)
      OdfTreeCollection(obj)
    else
      obj.infoItems ++ obj.objects.flatMap {
        subobj =>
          getLeafs(subobj)
      }
  }
  def getLeafs(objects: OdfObjects): OdfTreeCollection[OdfNode] = {
    if (objects.objects.nonEmpty)
      objects.objects.flatMap {
        obj => getLeafs(obj)
      }
    else OdfTreeCollection(objects)
  }
  /** Helper method for getting all OdfNodes found in given OdfNodes. Basically get list of all nodes in tree.  */
  def getOdfNodes(hasPaths: OdfNode*): Seq[OdfNode] = {
    hasPaths.flatMap {
      case info: OdfInfoItem => Seq(info)
      case obj:  OdfObject   => Seq(obj) ++ getOdfNodes((obj.objects.toSeq ++ obj.infoItems.toSeq): _*)
      case objs: OdfObjects  => Seq(objs) ++ getOdfNodes(objs.objects.toSeq: _*)
    }.toSeq
  }

  /** Helper method for getting all OdfInfoItems found in OdfObjects */
  def getInfoItems( objects: OdfObjects ) : OdfTreeCollection[OdfInfoItem] = {
    getLeafs(objects).collect{ case info: OdfInfoItem => info}
  }

  def getInfoItems( _object: OdfObject ) : Vector[OdfInfoItem] = {
    getLeafs(_object).collect{ case info: OdfInfoItem => info}

    /*nodes.flatMap {
   }.toVector*/
  }
  def getInfoItems( nodes: OdfNode*) : Vector[OdfInfoItem] ={
    nodes.flatMap{
      case info: OdfInfoItem => Vector(info)
      case obj: OdfObject    => getInfoItems(obj)
      case objs: OdfObjects  => getInfoItems(objs)
    }.toVector
  }

  /**
   * Generates odf tree containing the ancestors of given object up to the root Objects level.
   */
  @annotation.tailrec
  def createAncestors(last: OdfNode): OdfObjects = {
    val parentPath = last.path.dropRight(1)

    last match {
      case info: OdfInfoItem =>
        val parent = OdfObject(OdfTreeCollection(QlmID(parentPath.last)), parentPath, OdfTreeCollection(info), OdfTreeCollection())
        createAncestors(parent)

      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(OdfTreeCollection(obj))
        else {
          val parent = OdfObject(OdfTreeCollection(QlmID(parentPath.last)),parentPath, OdfTreeCollection(), OdfTreeCollection(obj))
          createAncestors(parent)
        }

      case objs: OdfObjects =>
        objs
    }
  }
  /** Method for generating parent OdfNode of this instance */
  def getParent(child: OdfNode): OdfNode = {
    val parentPath = child.path.dropRight(1)
    child match {
      case info: OdfInfoItem =>
        val parent = OdfObject(OdfTreeCollection(), parentPath, OdfTreeCollection(info), OdfTreeCollection())
        parent
      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(OdfTreeCollection(obj))
        else {
          val parent = OdfObject(OdfTreeCollection(), parentPath, OdfTreeCollection(), OdfTreeCollection(obj))
          parent
        }

      case objs: OdfObjects =>
        objs

    }
  }

  def getPathValuePairs( objs: OdfObjects ) : OdfTreeCollection[(Path,OdfValue)]={
    getInfoItems(objs).flatMap{ infoitem => infoitem.values.map{ value => (infoitem.path, value)} }
  }
}