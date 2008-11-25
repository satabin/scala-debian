/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Unparsed.scala 14555 2008-04-08 15:42:54Z washburn $


package scala.xml

/** An XML node for unparsed content. It will be output verbatim, all bets
 *  are off regarding wellformedness etc.
 *
 * @author Burak Emir
 * @param _data content in this node, may not be null.
 */
case class Unparsed(_data: String) extends Atom[String](_data) {

  if (null == data)
    throw new java.lang.NullPointerException("tried to construct Unparsed with null")

  final override def equals(x: Any) = x match {
    case s:String   => s == data
    case s:Text     => data == s.data
    case s:Unparsed => data == s.data
    case s:Atom[_]  => data == s.data
    case _ => false
  }

  /** returns text, with some characters escaped according to XML spec */
  override def toString(sb: StringBuilder) = sb append data

}
