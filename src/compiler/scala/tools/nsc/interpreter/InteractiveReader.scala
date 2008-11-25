/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author Stepan Koltsov
 */
// $Id: InteractiveReader.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc.interpreter

/** Reads lines from an input stream */
trait InteractiveReader {
  def readLine(prompt: String): String
  val interactive: Boolean
}



object InteractiveReader {
  /** Create an interactive reader.  Uses JLine if the
   *  library is available, but otherwise uses a 
   *  SimpleReader. */
  def createDefault(): InteractiveReader = {
    try {
      new JLineReader
    } catch {
      case e =>
        //out.println("jline is not available: " + e) //debug
	new SimpleReader()
    }
  }


}
