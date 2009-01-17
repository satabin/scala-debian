/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author Stepan Koltsov
 */
// $Id: SimpleReader.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc.interpreter
import java.io.{BufferedReader, PrintWriter}


/** Reads using standard JDK API */
class SimpleReader(
  in: BufferedReader, 
  out: PrintWriter, 
  val interactive: Boolean)
extends InteractiveReader {
  def this() = this(Console.in, new PrintWriter(Console.out), true)

  def readOneLine(prompt: String) = {
    if (interactive) {
      out.print(prompt)
      out.flush()
    }
    in.readLine()
  }
}
