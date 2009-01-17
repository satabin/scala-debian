/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: QNode.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.xml

/**
 * This object provides an extractor method to match a qualified node with its namespace URI
 *
 * @author  Burak Emir
 * @version 1.0
 */
object QNode {

  def unapplySeq(n:Node) = Some (Tuple4(n.scope.getURI(n.prefix), n.label, n.attributes, n.child))

}
