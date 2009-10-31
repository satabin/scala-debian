/* NEST (New Scala Test)
 * Copyright 2007-2009 LAMP/EPFL
 * @author Philipp Haller
 */

// $Id: CompileManager.scala 18847 2009-10-01 07:38:11Z phaller $

package scala.tools.partest.nest

import scala.tools.nsc.{Global, Settings, CompilerCommand, FatalError}
import scala.tools.nsc.reporters.{Reporter, ConsoleReporter}

import java.io.{File, BufferedReader, PrintWriter, FileReader, FileWriter, StringWriter}

class ExtConsoleReporter(override val settings: Settings, reader: BufferedReader, var writer: PrintWriter) extends ConsoleReporter(settings, reader, writer) {
  def this(settings: Settings) = {
    this(settings, Console.in, new PrintWriter(new FileWriter("/dev/null")))
  }
  def hasWarnings: Boolean = WARNING.count != 0
}

abstract class SimpleCompiler {
  def compile(out: Option[File], files: List[File], kind: String, log: File): Boolean
}

class TestSettings(fileMan: FileManager) extends {
  override val bootclasspathDefault =
    System.getProperty("sun.boot.class.path", "") + File.pathSeparator +
    fileMan.LATEST_LIB
  override val extdirsDefault =
    System.getProperty("java.ext.dirs", "")
} with Settings(x => ())

class DirectCompiler(val fileManager: FileManager) extends SimpleCompiler {
  def newGlobal(settings: Settings, reporter: Reporter): Global =
    new Global(settings, reporter)

  def newGlobal(settings: Settings, logWriter: FileWriter): Global = {
    val rep = new ExtConsoleReporter(settings,
                                     Console.in,
                                     new PrintWriter(logWriter))
    rep.shortname = true
    newGlobal(settings, rep)
  }

  def newSettings = {
    val settings = new TestSettings(fileManager)
    settings.deprecation.value = true
    settings.nowarnings.value = false
    settings.encoding.value = "iso-8859-1"
    settings
  }

  def newReporter(sett: Settings) = new ExtConsoleReporter(sett,
                                                           Console.in,
                                                           new PrintWriter(new StringWriter))

  def compile(out: Option[File], files: List[File], kind: String, log: File): Boolean = {
    val testSettings = newSettings
    val logWriter = new FileWriter(log)
    
    // check whether there is a ".flags" file
    val testBase = {
      val logBase = fileManager.basename(log.getName)
      logBase.substring(0, logBase.length-4)
    }
    val argsFile = new File(log.getParentFile, testBase+".flags")
    val argString = if (argsFile.exists) {
      val fileReader = new FileReader(argsFile)
      val reader = new BufferedReader(fileReader)
      val options = reader.readLine()
      reader.close()
      options
    } else ""
    val allOpts = fileManager.SCALAC_OPTS+" "+argString
    NestUI.verbose("scalac options: "+allOpts)
    val args = List.fromArray(allOpts.split("\\s"))
    val command = new CompilerCommand(args, testSettings, x => {}, false)
    val global = newGlobal(command.settings, logWriter)
    val testRep: ExtConsoleReporter = global.reporter.asInstanceOf[ExtConsoleReporter]

    val test: TestFile = kind match {
      case "pos"      => PosTestFile(files(0), fileManager, out.isEmpty)
      case "neg"      => NegTestFile(files(0), fileManager, out.isEmpty)
      case "run"      => RunTestFile(files(0), fileManager, out.isEmpty)
      case "jvm"      => JvmTestFile(files(0), fileManager, out.isEmpty)
      case "jvm5"     => Jvm5TestFile(files(0), fileManager, out.isEmpty)
      case "shootout" => ShootoutTestFile(files(0), fileManager, out.isEmpty)
      case "scalacheck" =>
        ScalaCheckTestFile(files(0), fileManager, out.isEmpty)
    }
    test.defineSettings(command.settings)
    out match {
      case Some(outDir) =>
        command.settings.outdir.value = outDir.getAbsolutePath
        command.settings.classpath.value = command.settings.classpath.value+
          File.pathSeparator+outDir.getAbsolutePath
      case None =>
        // do nothing
    }

    val toCompile = files.map(_.getPath)
    try {
      NestUI.verbose("compiling "+toCompile)
      try {
        (new global.Run) compile toCompile
      } catch {
        case FatalError(msg) =>
          testRep.error(null, "fatal error: " + msg)
      }
      testRep.printSummary
      testRep.writer.flush
      testRep.writer.close
    } catch {
      case e: Exception =>
        e.printStackTrace()
        return false
    } finally {
      logWriter.close()
    }
    !testRep.hasErrors
  }
}

class ReflectiveCompiler(val fileManager: ConsoleFileManager) extends SimpleCompiler {
  import fileManager.{latestCompFile, latestPartestFile}

  val sepUrls = Array(latestCompFile.toURL, latestPartestFile.toURL)
  //NestUI.verbose("constructing URLClassLoader from URLs "+latestCompFile+" and "+latestPartestFile)

  val sepLoader = new java.net.URLClassLoader(sepUrls, null)

  val sepCompilerClass =
    sepLoader.loadClass("scala.tools.partest.nest.DirectCompiler")
  val sepCompiler = sepCompilerClass.newInstance()

  // needed for reflective invocation
  val fileClass = Class.forName("java.io.File")
  val stringClass = Class.forName("java.lang.String")
  val sepCompileMethod =
    sepCompilerClass.getMethod("compile", Array(fileClass, stringClass): _*)
  val sepCompileMethod2 =
    sepCompilerClass.getMethod("compile", Array(fileClass, stringClass, fileClass): _*)

  /* This method throws java.lang.reflect.InvocationTargetException
   * if the compiler crashes.
   * This exception is handled in the shouldCompile and shouldFailCompile
   * methods of class CompileManager.
   */
  def compile(out: Option[File], files: List[File], kind: String, log: File): Boolean = {
    val fileArgs: Array[AnyRef] = Array(out, files, kind, log)
    val res = sepCompileMethod2.invoke(sepCompiler, fileArgs: _*).asInstanceOf[java.lang.Boolean]
    res.booleanValue()
  }
}

class CompileManager(val fileManager: FileManager) {

  import scala.actors.Actor._
  import scala.actors.{Actor, Exit, TIMEOUT}

  import java.util.{Timer, TimerTask}

  var compiler: SimpleCompiler = new /*ReflectiveCompiler*/ DirectCompiler(fileManager)

  val timer = new Timer

  var numSeparateCompilers = 1
  def createSeparateCompiler() = {
    numSeparateCompilers += 1
    compiler = new /*ReflectiveCompiler*/ DirectCompiler(fileManager)
  }

  val delay = fileManager.timeout.toLong

  def withTimeout(files: List[File])(thunk: => Boolean): Boolean = {
    createSeparateCompiler()

    val parent = self
    /*self.trapExit = true
    val child = link {
      parent ! (self, thunk)
    }

    receiveWithin(delay) {
      case TIMEOUT =>
        println("compilation timed out")
        false
      case Exit(from, reason) if from == child =>
        val From = from
        reason match {
          case 'normal =>
            receive {
              case (From, result: Boolean) => result
            }
          case t: Throwable =>
            NestUI.verbose("while invoking compiler ("+files+"):")
            NestUI.verbose("caught "+t)
            t.printStackTrace
            if (t.getCause != null)
              t.getCause.printStackTrace
            false
        }
    }*/

    val ontimeout = new TimerTask {
      def run() {
        parent ! 'timeout
      }
    }
    timer.schedule(ontimeout, delay)

    actor {
      val result = try {
        thunk
      } catch {
        case t: Throwable =>
          NestUI.verbose("while invoking compiler ("+files+"):")
          NestUI.verbose("caught "+t)
          t.printStackTrace
          if (t.getCause != null)
            t.getCause.printStackTrace
          false
      }
      parent ! result
    }
    receive {
      case 'timeout =>
        println("compilation timed out")
        false
      case r: Boolean =>
        r
    }
  }

  /* This method returns true iff compilation succeeds.
   */
  def shouldCompile(files: List[File], kind: String, log: File): Boolean =
    withTimeout(files) {
      compiler.compile(None, files, kind, log)
    }

  /* This method returns true iff compilation succeeds.
   */
  def shouldCompile(out: File, files: List[File], kind: String, log: File): Boolean =
    withTimeout(files) {
      compiler.compile(Some(out), files, kind, log)
    }

  /* This method returns true iff compilation fails
   * _and_ the compiler does _not_ crash or loop.
   *
   * If the compiler crashes, this method returns false.
   */
  def shouldFailCompile(files: List[File], kind: String, log: File): Boolean =
    withTimeout(files) {
      !compiler.compile(None, files, kind, log)
    }

  /* This method returns true iff compilation fails
   * _and_ the compiler does _not_ crash or loop.
   *
   * If the compiler crashes, this method returns false.
   */
  def shouldFailCompile(out: File, files: List[File], kind: String, log: File): Boolean =
    withTimeout(files) {
      !compiler.compile(Some(out), files, kind, log)
    }

}
