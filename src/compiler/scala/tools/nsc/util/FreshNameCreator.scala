/* NSC -- new Scala compiler
 * Copyright 2005-2006 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: FreshNameCreator.scala 14714 2008-04-19 11:41:05Z mcdirmid $

package scala.tools.nsc.util

import scala.collection.mutable.HashMap

trait FreshNameCreator {
  /** do not call before after type checking ends. Use newName(Position,String) instead. */
  @deprecated def newName(prefix : String) : String
  /** do not call before after type checking ends. Use newName(Position) instead. */
  @deprecated def newName() : String
  def newName(pos : util.Position, prefix : String) : String
  def newName(pos : util.Position) : String
}
object FreshNameCreator {
  class Default extends FreshNameCreator {

  protected var counter = 0
  protected val counters = new HashMap[String, Int]

  /**
   * Create a fresh name with the given prefix. It is guaranteed
   * that the returned name has never been returned by a previous
   * call to this function (provided the prefix does not end in a digit).
   */
  def newName(prefix: String): String = {
    val safePrefix = prefix.replace('<', '$').replace('>', '$')
    val count = counters.getOrElse(safePrefix, 0) + 1
    counters(safePrefix) = count
    safePrefix + count
  }
  def newName(pos : util.Position, prefix : String) = newName(prefix)
  def newName(pos : util.Position) = newName()

  def newName(): String = {
    counter = counter + 1
    "$" + counter + "$"
  }
}
}
