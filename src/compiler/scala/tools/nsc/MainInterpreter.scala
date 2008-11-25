/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author Lex Spoon
 */
// $Id: MainInterpreter.scala 9687 2007-01-23 15:54:05Z michelou $

package scala.tools.nsc

/** A command-line wrapper for the interpreter */
object MainInterpreter {
  def main(args: Array[String]) {
    (new InterpreterLoop).main(args)
  }
}
