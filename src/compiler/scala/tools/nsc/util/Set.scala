/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: Set.scala 12005 2007-06-13 12:28:07Z michelou $

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
