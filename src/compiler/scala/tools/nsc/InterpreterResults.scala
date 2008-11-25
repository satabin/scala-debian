/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: InterpreterResults.scala 14416 2008-03-19 01:17:25Z mihaylov $

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
