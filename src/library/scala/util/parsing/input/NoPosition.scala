/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: NoPosition.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.util.parsing.input

/** Undefined position 
 *
 * @author Martin Odersky, Adriaan Moors
 */
object NoPosition extends Position {
  def line = 0
  def column = 0
  override def toString = "<undefined position>"
  override def longString = toString
  def lineContents = ""
}
