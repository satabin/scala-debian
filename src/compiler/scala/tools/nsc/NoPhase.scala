/* NSC -- new Scala compiler
 * Copyright 2007-2008 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: NoPhase.scala 11938 2007-06-07 11:22:41Z michelou $

package scala.tools.nsc

object NoPhase extends Phase(null) {
  def name = "<no phase>"
  def run { throw new Error("NoPhase.run") }
}
