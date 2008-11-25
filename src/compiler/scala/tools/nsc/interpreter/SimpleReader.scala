/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author Stepan Koltsov
 */
// $Id: SimpleReader.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc.interpreter
import java.io.{BufferedReader, PrintWriter}


/** Reads using standard JDK API */
class SimpleReader(
  in: BufferedReader, 
  out: PrintWriter, 
  val interactive: Boolean)
extends InteractiveReader {
  def this() = this(Console.in, new PrintWriter(Console.out), true)

  def readLine(prompt: String) = {
    if (interactive) {
      out.print(prompt)
      out.flush()
    }
    in.readLine()
  }
}
