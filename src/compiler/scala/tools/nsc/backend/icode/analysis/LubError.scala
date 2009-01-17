/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id: LubError.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc.backend.icode.analysis

class LubError(a: Any, b: Any, msg: String) extends Exception {
  override def toString() = "Lub error: " + msg + a + b
}
