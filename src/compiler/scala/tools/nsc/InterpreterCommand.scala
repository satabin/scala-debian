/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: InterpreterCommand.scala 15427 2008-06-24 12:45:25Z odersky $

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
