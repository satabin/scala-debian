/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: InterpreterCommand.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc

/** A command line for the interpreter.
 *
 *  @author  Lex Spoon
 *  @version 1.0
 */
class InterpreterCommand(arguments: List[String], error: String => Unit)
extends CompilerCommand(arguments, new Settings(error), error, false) {
  override val cmdName = "scala"
  override lazy val fileEnding = ".scalaint"
}
