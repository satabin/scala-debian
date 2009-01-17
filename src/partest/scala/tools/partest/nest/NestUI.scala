/* NEST (New Scala Test)
 * @author Philipp Haller
 */

// $Id: NestUI.scala 16103 2008-09-15 17:43:28Z phaller $

package scala.tools.partest.nest

import java.io.PrintWriter

object NestUI {

  val NONE = 0
  val SOME = 1
  val MANY = 2

  private var _outline = ""
  private var _success = ""
  private var _failure = ""
  private var _warning = ""
  private var _default = ""

  def initialize(number: Int) = number match {
    case MANY =>
      _outline = Console.BOLD + Console.BLACK
      _success = Console.BOLD + Console.GREEN
      _failure = Console.BOLD + Console.RED
      _warning = Console.BOLD + Console.YELLOW
      _default = Console.RESET
    case SOME =>
      _outline = Console.BOLD + Console.BLACK
      _success = Console.RESET
      _failure = Console.BOLD + Console.BLACK
      _warning = Console.BOLD + Console.BLACK
      _default = Console.RESET
    case _ =>
  }

  def outline(msg: String) = print(_outline + msg + _default)
  def outline(msg: String, wr: PrintWriter) = synchronized {
    wr.print(_outline + msg + _default)
  }
  
  def success(msg: String) = print(_success  + msg + _default)
  def success(msg: String, wr: PrintWriter) = synchronized {
    wr.print(_success + msg + _default)
  }

  def failure(msg: String) = print(_failure  + msg + _default)
  def failure(msg: String, wr: PrintWriter) = synchronized {
    wr.print(_failure + msg + _default)
  }

  def warning(msg: String) = print(_warning  + msg + _default)
  def warning(msg: String, wr: PrintWriter) = synchronized {
    wr.print(_warning + msg + _default)
  }

  def normal(msg: String) = print(_default + msg)
  def normal(msg: String, wr: PrintWriter) = synchronized {
    wr.print(_default + msg)
  }

  def usage() {
    println("Usage: NestRunner [<options>] [<testfile> ..] [<resfile>]")
    println("  <testfile>: list of files ending in '.scala'")
    println("  <resfile>: a file not ending in '.scala'")
    println("  <options>:")
    println
    println("  Test categories:")
    println("    --all        run all tests")
    println("    --pos        run compilation tests (success)")
    println("    --neg        run compilation tests (failure)")
    println("    --run        run interpreter and backend tests")
    println("    --jvm        run JVM backend tests")
    println("    --jvm5       run JVM backend tests (-target:jvm-1.5)")
    println("    --res        run resident compiler tests")
    println("    --script     run script runner tests")
    println("    --shootout   run shootout tests")
    println
    println("  Other options:")
    println("    --pack       pick compiler/library in build/pack, and run all tests")
    println("    --four       pick compiler/library in build/four-pack, and run all tests")
    println("    --show-log   show log")
    println("    --show-diff  show diff between log and check file")
    println("    --failed     run only those tests that failed during the last run")
    println("    --verbose    show progress information")
    println("    --buildpath  set (relative) path to build jars")
    println("                 ex.: --buildpath build/pack")
    println("    --classpath  set (absolute) path to build classes")
    println("    --srcpath    set (relative) path to test source files")
    println("                 ex.: --srcpath pending")
    println
    println("version 0.9.2")
    println("maintained by Philipp Haller (EPFL)")
    exit(1)
  }


  var _verbose = false

  def verbose(msg: String) {
    if (_verbose) {
      outline("debug: ")
      println(msg)
    }
  }

}
