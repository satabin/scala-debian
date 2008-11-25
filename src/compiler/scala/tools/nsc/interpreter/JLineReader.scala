/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author Stepan Koltsov
 */
// $Id: JLineReader.scala 16289 2008-10-18 15:51:30Z washburn $


package scala.tools.nsc.interpreter
import java.io.File

/** Reads from the console using JLine */
class JLineReader extends InteractiveReader {
  val consoleReader = {
    val history = try {
      new jline.History(new File(System.getProperty("user.home"), ".scala_history"))
    } catch {
      // do not store history if error
      case _ => new jline.History()
    }
    val r = new jline.ConsoleReader()
    r.setHistory(history)
    r.setBellEnabled(false)
    r
  }
  def readOneLine(prompt: String) = consoleReader.readLine(prompt) 
  val interactive = true
}

