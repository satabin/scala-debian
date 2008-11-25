/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author Stepan Koltsov
 */
// $Id: JLineReader.scala 15390 2008-06-17 17:44:30Z odersky $


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
  def readLine(prompt: String) = consoleReader.readLine(prompt)
  val interactive = true
}

