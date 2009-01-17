/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: EntityRef.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.xml

/** The class <code>EntityRef</code> implements an XML node for entity
 *  references.
 *
 * @author  Burak Emir
 * @version 1.0
 * @param   text the text contained in this node.
 */
case class EntityRef(entityName: String) extends SpecialNode {

  final override def typeTag$: Int = -5

  /** structural equality */
  override def equals(x: Any): Boolean = x match {
    case EntityRef(x) => x.equals(entityName)
    case _ => false
  }

  /** the constant "#ENTITY"
   */
  def label = "#ENTITY"

  override def hashCode() = entityName.hashCode()

  /** ...
   */
  override def text = entityName match {
    case "lt"   => "<"
    case "gt"   => ">"
    case "amp"  => "&"
    case "apos" => "'"
    case "quot" => "\""
    case _ => val sb = new StringBuilder(); toString(sb).toString() 
  }

  /** Appends "&amp; entityName;" to this string buffer.
   *
   *  @param  sb the string buffer.
   *  @return the modified string buffer <code>sb</code>.
   */
  override def toString(sb: StringBuilder) = 
    sb.append("&").append(entityName).append(";")

}
