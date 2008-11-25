/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SpecialNode.scala 10951 2007-05-03 12:43:50Z michelou $


package scala.xml

/** <p>
 *    <code>SpecialNode</code> is a special XML node which
 *    represents either text (PCDATA), a comment, a PI, or an entity ref.
 *  </p>
 *  <p>
 *    SpecialNodes also play the role of XMLEvents for pull-parsing.
 *  </p>
 *
 *  @author Burak Emir
 */
abstract class SpecialNode extends Node with pull.XMLEvent {

  /** always empty */
  final override def attributes = Null

  /** always Node.EmptyNamespace */
  final override def namespace = null

  /** always empty */
  final def child = Nil

  /** append string representation to the given stringbuffer */
  def toString(sb: StringBuilder): StringBuilder

}
