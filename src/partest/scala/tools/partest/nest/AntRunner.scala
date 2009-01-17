/*                     __                                               *\
**     ________ ___   / /  ___     Scala Parallel Testing               **
**    / __/ __// _ | / /  / _ |    (c) 2007-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: AntRunner.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.tools.partest.nest

import scala.actors.Actor._

import java.io.File
import java.net.URLClassLoader

import org.apache.tools.ant.Task
import org.apache.tools.ant.types.{Path, Reference, FileSet}

class AntRunner extends DirectRunner {
  
  val fileManager = new FileManager {
    
    var JAVACMD: String = "java"
    var JAVAC_CMD: String = "javac"
    var CLASSPATH: String = _
    var EXT_CLASSPATH: String = _
    var LATEST_LIB: String = _

  }
  
  def reflectiveRunTestsForFiles(kindFiles: Array[File], kind: String): Int = {
    val (succs, fails) = runTestsForFiles(kindFiles.toList, kind)
    succs << 16 | fails
  }
  
}
