/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: InterpreterResults.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc

object InterpreterResults {

  /** A result from interpreting one line of input. */
  abstract sealed class Result

  /** The line was interpreted successfully. */
  case object Success extends Result

  /** The line was erroneous in some way. */
  case object Error extends Result
  
  /** The input was incomplete.  The caller should request more
   *  input.
   */
  case object Incomplete extends Result

}
