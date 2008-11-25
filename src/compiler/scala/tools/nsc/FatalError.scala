/* NSC -- new Scala compiler
 * Copyright 2005-2006 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: FatalError.scala 8279 2006-07-28 12:46:51Z michelou $

package scala.tools.nsc

case class FatalError(msg: String) extends Exception(msg)
