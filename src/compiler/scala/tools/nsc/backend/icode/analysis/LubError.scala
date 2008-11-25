/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id: LubError.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc.backend.icode.analysis

class LubError(a: Any, b: Any, msg: String) extends Exception {
  override def toString() = "Lub error: " + msg + a + b
}
