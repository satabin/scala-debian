/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: EvalLoop.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.tools.nsc

trait EvalLoop {

  def prompt: String

  def loop(action: (String) => Unit) {
    Console.print(prompt)
    try {
      val line = Console.readLine
      if (line.length() > 0) {
        action(line)
        loop(action)
      }
    }
    catch {
      case _: java.io.EOFException => //nop
    }
  }

}
