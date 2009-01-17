/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author Lex Spoon
 */
// $Id: MainInterpreter.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc

/** A command-line wrapper for the interpreter */
object MainInterpreter {
  def main(args: Array[String]) {
    (new InterpreterLoop).main(args)
  }
}
