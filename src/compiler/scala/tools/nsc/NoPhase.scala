/* NSC -- new Scala compiler
 * Copyright 2007-2010 LAMP/EPFL
 * @author  Martin Odersky
 */

package scala.tools.nsc

object NoPhase extends Phase(null) {
  def name = "<no phase>"
  def run { throw new Error("NoPhase.run") }
}
