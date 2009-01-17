/* NSC -- new Scala compiler
 * Copyright 2007-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: NoPhase.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.tools.nsc

object NoPhase extends Phase(null) {
  def name = "<no phase>"
  def run { throw new Error("NoPhase.run") }
}
