/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: Set.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc.util

/** A common class for lightweight sets.
 */
abstract class Set[T <: AnyRef] {

  def findEntry(x: T): T

  def addEntry(x: T): Unit

  def elements: Iterator[T]

  def contains(x: T): Boolean =
    findEntry(x) ne null

  def toList = elements.toList

}
