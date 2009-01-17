/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: FatalError.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc

case class FatalError(msg: String) extends Exception(msg)
