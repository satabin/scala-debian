/* NEST (New Scala Test)
 * @author Philipp Haller
 */

// $Id: Worker.scala 16473 2008-11-01 20:01:29Z michelou $

package scala.tools.partest.nest

import java.io.{File, FileInputStream, FileOutputStream, PrintStream,
                PrintWriter, StringWriter, FileWriter, InputStreamReader,
                FileReader, OutputStreamWriter, BufferedReader}

import java.net.URL

import scala.tools.nsc.{ObjectRunner, GenericRunnerCommand}

import scala.actors.{Actor, Exit, TIMEOUT}
import scala.actors.Actor._

case class RunTests(kind: String, files: List[File])
case class Results(succ: Int, fail: Int, logs: List[LogFile], outdirs: List[File])

class LogFile(parent: File, child: String) extends File(parent, child) {
  var toDelete = false
}

class Worker(val fileManager: FileManager) extends Actor {
  import fileManager._
  import scala.tools.nsc.{Settings, CompilerCommand, Global}
  import scala.tools.nsc.reporters.ConsoleReporter
  import scala.tools.nsc.util.FakePos

  var reporter: ConsoleReporter = _

  def error(msg: String) {
    reporter.error(FakePos("scalac"),
                   msg + "\n  scalac -help  gives more information")
  }

  def act() {
    react {
      case RunTests(kind, files) =>
        NestUI.verbose("received "+files.length+" to test")
        val (succ, fail) = runTests(kind, files)
        sender ! Results(succ, fail, createdLogFiles, createdOutputDirs)
    }
  }

  private def basename(name: String): String = {
    val inx = name.lastIndexOf(".")
    if (inx < 0) name else name.substring(0, inx)
  }

  def printInfoStart(file: File, printer: PrintWriter) {
    NestUI.outline("testing: ", printer)
    val filesdir = file.getAbsoluteFile.getParentFile.getParentFile
    val testdir = filesdir.getParentFile
    val totalWidth = 56
    val name = {
      // 1. try with [...]/files/run/test.scala
      val testPathLen = testdir.getAbsolutePath.length
      val name = file.getAbsolutePath.substring(testPathLen)
      if (name.length <= totalWidth)
        name
      // 2. try with [...]/run/test.scala
      else {
        val filesPathLen = filesdir.getAbsolutePath.length
        file.getAbsolutePath.substring(filesPathLen)
      }
    }
    NestUI.normal("[...]"+name+List.toString(List.make(totalWidth-name.length, ' ')), printer)
  }

  def printInfoEnd(success: Boolean, printer: PrintWriter) {
    NestUI.normal("[", printer)
    if (success) NestUI.success("  OK  ", printer)
    else NestUI.failure("FAILED", printer)
    NestUI.normal("]\n", printer)
  }

  var log = ""
  var createdLogFiles: List[LogFile] = List()
  var createdOutputDirs: List[File] = List()

  def createLogFile(dir: File, fileBase: String, kind: String): LogFile = {
    val logFile = new LogFile(dir, fileBase + "-" + kind + ".log")
    createdLogFiles = logFile :: createdLogFiles
    logFile
  }

  def createLogFile(file: File, kind: String): LogFile = {
    val dir = file.getParentFile
    val fileBase = basename(file.getName)
    createLogFile(dir, fileBase, kind)
  }

  def createOutputDir(dir: File, fileBase: String, kind: String): File = {
    val outDir = new File(dir, fileBase + "-" + kind + ".obj")
    if (!outDir.exists)
      outDir.mkdir()
    createdOutputDirs = outDir :: createdOutputDirs
    outDir
  }

  /* Note: not yet used/tested. */
  def execTestObjectRunner(file: File, outDir: File, logFile: File) {
    val consFM = new ConsoleFileManager
    import consFM.{latestCompFile, latestLibFile, latestActFile,
                   latestPartestFile}

    val classpath: List[URL] =
      outDir.toURL ::
      //List(file.getParentFile.toURL) :::
      List(latestCompFile.toURL, latestLibFile.toURL,
           latestActFile.toURL, latestPartestFile.toURL) :::
      (List.fromString(CLASSPATH, File.pathSeparatorChar) map { x =>
        (new File(x)).toURL })
    NestUI.verbose("ObjectRunner classpath: "+classpath)

    try {
      // configure input/output files
      val logOut    = new FileOutputStream(logFile)
      val logWriter = new PrintStream(logOut)

      // grab global lock
      fileManager.synchronized {

        val oldStdOut = System.out
        val oldStdErr = System.err
        System.setOut(logWriter)
        System.setErr(logWriter)

        /*
         " -Djava.library.path="+logFile.getParentFile.getAbsolutePath+
         " -Dscalatest.output="+outDir.getAbsolutePath+
         " -Dscalatest.lib="+LATEST_LIB+
         " -Dscalatest.cwd="+outDir.getParent+
         " -Djavacmd="+JAVACMD+
         */

        System.setProperty("java.library.path", logFile.getParentFile.getCanonicalFile.getAbsolutePath)
        System.setProperty("scalatest.output", outDir.getCanonicalFile.getAbsolutePath)
        System.setProperty("scalatest.lib", LATEST_LIB)
        System.setProperty("scalatest.cwd", outDir.getParent)

        ObjectRunner.run(classpath, "Test", List("jvm"))

        logWriter.flush()
        logWriter.close()

        System.setOut(oldStdOut)
        System.setErr(oldStdErr)
      }

      /*val out = new FileOutputStream(logFile, true)
      Console.withOut(new PrintStream(out)) {
        ObjectRunner.run(classpath, "Test", List("jvm"))
      }
      out.flush
      out.close*/
    } catch {
      case e: Exception =>
        NestUI.verbose(e+" ("+file.getPath+")")
        e.printStackTrace()
    }
  }

  def javac(outDir: File, files: List[File], output: File): Boolean = {
    // compile using command-line javac compiler
    val javacCmd = if ((fileManager.JAVAC_CMD.indexOf("${env.JAVA_HOME}") != -1) ||
                       fileManager.JAVAC_CMD.equals("/bin/javac") ||
                       fileManager.JAVAC_CMD.equals("\\bin\\javac"))
      "javac"
    else
      fileManager.JAVAC_CMD

    val cmd = javacCmd+
      " -d "+outDir.getAbsolutePath+
      " -classpath "+outDir+File.pathSeparator+CLASSPATH+
      " "+files.mkString(" ")

    val (success, msg) = try {
      val exitCode = runCommand(cmd, output)
      NestUI.verbose("javac returned exit code: "+exitCode)
      if (exitCode != 0)
        (false, "Running \"javac\" failed with exit code: "+exitCode+"\n"+cmd+"\n")
      else
        (true, "")
    } catch {
      case e: Exception =>
        val swriter = new StringWriter
        e.printStackTrace(new PrintWriter(swriter))
        (false, "Running \"javac\" failed:\n"+cmd+"\n"+swriter.toString+"\n")
    }
    if (!success) {
      val writer = new PrintWriter(new FileWriter(output, true), true)
      writer.print(msg)
      writer.close()
    }
    success
  }

  /** Runs <code>command</code> redirecting standard out and
   *  error out to <code>output</code> file.
   */
  def runCommand(command: String, output: File): Int = {
    NestUI.verbose("running command:\n"+command)
    val proc = Runtime.getRuntime.exec(command)
    val in = proc.getInputStream
    val err = proc.getErrorStream
    val writer = new PrintWriter(new FileWriter(output), true)
    val inApp = new StreamAppender(new BufferedReader(new InputStreamReader(in)),
                                   writer)
    val errApp = new StreamAppender(new BufferedReader(new InputStreamReader(err)),
                                    writer)
    val async = new Thread(errApp)
    async.start()
    inApp.run()
    async.join()
    writer.close()
    try {
      proc.exitValue()
    } catch {
      case e: IllegalThreadStateException => 0
    }
  }

  def execTest(outDir: File, logFile: File, fileBase: String) {
    // check whether there is a ".javaopts" file
    val argsFile = new File(logFile.getParentFile, fileBase+".javaopts")
    val argString = if (argsFile.exists) {
      NestUI.verbose("argsFile: "+argsFile)
      val fileReader = new FileReader(argsFile)
      val reader = new BufferedReader(fileReader)
      val options = reader.readLine()
      reader.close()
      options
    } else ""
    NestUI.verbose("JAVA_OPTS: "+argString)

    val cp = System.getProperty("java.class.path", ".")
    NestUI.verbose("java.class.path: "+cp)

    def quote(path: String) = "\""+path+"\""

    val cmd =
      JAVACMD+
      " "+argString+
      " "+JAVA_OPTS+
      " -classpath "+outDir+File.pathSeparator+CLASSPATH+
      " -Djava.library.path="+logFile.getParentFile.getAbsolutePath+
      " -Dscalatest.output="+outDir.getAbsolutePath+
      " -Dscalatest.lib="+LATEST_LIB+
      " -Dscalatest.cwd="+outDir.getParent+
      " -Djavacmd="+JAVACMD+
      " scala.tools.nsc.MainGenericRunner"+
      " Test jvm"
    NestUI.verbose(cmd)

    runCommand(cmd, logFile)

    if (fileManager.showLog) {
      // produce log as string in `log`
      val reader = new BufferedReader(new FileReader(logFile))
      val swriter = new StringWriter
      val pwriter = new PrintWriter(swriter, true)
      val appender = new StreamAppender(reader, pwriter)
      appender.run()
      log = swriter.toString
    }
  }

  def existsCheckFile(dir: File, fileBase: String, kind: String) = {
    val checkFile = {
      val chkFile = new File(dir, fileBase + ".check")
      if (chkFile.isFile)
        chkFile
      else
        new File(dir, fileBase + "-" + kind + ".check")
    }
    checkFile.exists && checkFile.canRead
  }

  def compareOutput(dir: File, fileBase: String, kind: String, logFile: File): String = {
    // if check file exists, compare with log file
    val checkFile = {
      val chkFile = new File(dir, fileBase + ".check")
      if (chkFile.isFile)
        chkFile
      else
        new File(dir, fileBase + "-" + kind + ".check")
    }
    if (!checkFile.exists || !checkFile.canRead) {
      val reader = new BufferedReader(new FileReader(logFile))
      val swriter = new StringWriter
      val pwriter = new PrintWriter(swriter, true)
      val appender = new StreamAppender(reader, pwriter)
      appender.run()
      swriter.toString
    }
    else fileManager.compareFiles(logFile, checkFile)
  }

  def file2String(logFile: File) = {
    val logReader = new BufferedReader(new FileReader(logFile))
    val strWriter = new StringWriter
    val logWriter = new PrintWriter(strWriter, true)
    val logAppender = new StreamAppender(logReader, logWriter)
    logAppender.run()
    logReader.close()
    strWriter.toString
  }

  /** Runs a list of tests.
   *
   * @param kind  The test kind (pos, neg, run, etc.)
   * @param files The list of test files
   */
  def runTests(kind: String, files: List[File]): (Int, Int) = {
    val compileMgr = new CompileManager(fileManager)
    var errors = 0
    var succeeded = true
    var diff = ""
    var log = ""

    /** 1. Creates log file and output directory.
     *  2. Runs <code>script</code> function, providing log file and
     *     output directory as arguments.
     *  3. Prints test result.
     *  4. Shows log/diff if enabled.
     */
    def runInContext(file: File, kind: String, script: (File, File) => Unit) {
      // when option "--failed" is provided
      // execute test only if log file is present
      // (which means it failed before)
      val logFile = createLogFile(file, kind)
      if (!fileManager.failed || (logFile.exists && logFile.canRead)) {
        val swr = new StringWriter
        val wr = new PrintWriter(swr)
        succeeded = true
        diff = ""
        log = ""
        printInfoStart(file, wr)

        val fileBase: String = basename(file.getName)
        NestUI.verbose(this+" running test "+fileBase)
        val dir = file.getParentFile
        val outDir = createOutputDir(dir, fileBase, kind)
        NestUI.verbose("output directory: "+outDir)

        // run test-specific code
        try {
          script(logFile, outDir)
        } catch {
          case e: Exception =>
            val writer = new PrintWriter(new FileWriter(logFile), true)
            e.printStackTrace(writer)
            writer.close()
            succeeded = false
        }

        if (!succeeded) {
          errors += 1
          NestUI.verbose("incremented errors: "+errors)
        } else {
          // delete log file only if test was successful
          logFile.toDelete = true
        }

        printInfoEnd(succeeded, wr)
        wr.flush()
        swr.flush() //TODO: needed?
        NestUI.normal(swr.toString)
        if (!succeeded && fileManager.showDiff && diff != "")
          NestUI.normal(diff)
        else if (!succeeded && fileManager.showLog)
          showLog(logFile)
      }
    }

    def compileFilesIn(dir: File, kind: String, logFile: File, outDir: File) {
      val testFiles = dir.listFiles.toList
      val javaFiles = testFiles.filter(_.getName.endsWith(".java"))
      val scalaFiles = testFiles.filter(_.getName.endsWith(".scala"))
      if (!(scalaFiles.isEmpty && javaFiles.isEmpty) &&
          !compileMgr.shouldCompile(outDir, javaFiles ::: scalaFiles, kind, logFile)) {
        NestUI.verbose("compilation of "+scalaFiles+" failed\n")
        succeeded = false
      } else
        if (!javaFiles.isEmpty) {
          succeeded = javac(outDir, javaFiles, logFile)
          if (succeeded) {
            scalaFiles foreach { scalaFile =>
              if (!compileMgr.shouldCompile(outDir, List(scalaFile), kind, logFile)) {
                NestUI.verbose("compilation of "+scalaFile+" failed\n")
                succeeded = false
              }
            }
          }
        }
    }

    def failCompileFilesIn(dir: File, kind: String, logFile: File, outDir: File) {
      val testFiles = dir.listFiles.toList
      val javaFiles = testFiles.filter(_.getName.endsWith(".java"))
      val scalaFiles = testFiles.filter(_.getName.endsWith(".scala"))
      if (!(scalaFiles.isEmpty && javaFiles.isEmpty) &&
          !compileMgr.shouldFailCompile(outDir, javaFiles ::: scalaFiles, kind, logFile)) {
        NestUI.verbose("compilation of "+scalaFiles+" failed\n")
        succeeded = false
      }
    }

    def runJvmTest(file: File, kind: String) {
      runInContext(file, kind, (logFile: File, outDir: File) => {
        if (file.isDirectory) {
          compileFilesIn(file, kind, logFile, outDir)
        } else if (!compileMgr.shouldCompile(List(file), kind, logFile)) {
          NestUI.verbose("compilation of "+file+" failed\n")
          succeeded = false
        }
        if (succeeded) { // run test
          val fileBase = basename(file.getName)
          val dir      = file.getParentFile

          //TODO: detect whether we have to use Runtime.exec
          val useRuntime = true

          if (useRuntime)
            execTest(outDir, logFile, fileBase)
          else
            execTestObjectRunner(file, outDir, logFile)
          NestUI.verbose(this+" finished running "+fileBase)

          diff = compareOutput(dir, fileBase, kind, logFile)
          if (!diff.equals("")) {
            NestUI.verbose("output differs from log file\n")
            succeeded = false
          }
        }
      })
    }

    kind match {
      case "scalacheck" => for (file <- files)
        runInContext(file, kind, (logFile: File, outDir: File) => {
          if (file.isDirectory) {
            compileFilesIn(file, kind, logFile, outDir)
          } else if (!compileMgr.shouldCompile(List(file), kind, logFile)) {
            NestUI.verbose("compilation of "+file+" failed\n")
            succeeded = false
          }
          if (succeeded) {
            NestUI.verbose("compilation of "+file+" succeeded\n")

            val libs = new File(fileManager.LIB_DIR)
            val urls = List((new File(libs, "ScalaCheck.jar")).toURL,
                            (new File(libs, "ScalaCheckHelper.jar")).toURL)
            val outURL = outDir.getCanonicalFile.toURL
            val urlArr = (outURL :: urls).toArray
            NestUI.verbose("loading classes from:")
            urlArr foreach {url => NestUI.verbose(url.toString)}
            val loader = new java.net.URLClassLoader(urlArr, fileManager.getClass.getClassLoader)
            
            (try {
              Some(Class.forName("ScalaCheckHelper", true, loader))
            } catch {
              case se: SecurityException => None
              case cnfe: ClassNotFoundException => None
            }) match {
              case None =>
                NestUI.verbose("cannot find ScalaCheckHelper class")
                succeeded = false
              case Some(clazz) =>
                val method = clazz.getMethod("passed", Array(classOf[File], classOf[Array[URL]]): _*)
                val res = method.invoke(null, Array(logFile, urlArr): _*).asInstanceOf[String]
                NestUI.verbose("ScalaCheck result: "+res)
                succeeded = res.equals("ok")
            }
          }
        })
      case "pos" => for (file <- files)
        runInContext(file, kind, (logFile: File, outDir: File) => {
          if (file.isDirectory) {
            compileFilesIn(file, kind, logFile, outDir)
          } else if (!compileMgr.shouldCompile(List(file), kind, logFile)) {
            NestUI.verbose("compilation of "+file+" failed\n")
            succeeded = false
          }
        })
      case "neg" =>
        for (file <- files) {
          runInContext(file, kind, (logFile: File, outDir: File) => {
            if (file.isDirectory) {
              failCompileFilesIn(file, kind, logFile, outDir)
            } else if (!compileMgr.shouldFailCompile(List(file), kind, logFile)) {
              succeeded = false
            }
            if (succeeded) { // compare log file to check file
              val fileBase = basename(file.getName)
              val dir      = file.getParentFile
              if (!existsCheckFile(dir, fileBase, kind)) {
                // diff is contents of logFile
                diff = file2String(logFile)
              } else
                diff = compareOutput(dir, fileBase, kind, logFile)

              if (!diff.equals("")) {
                NestUI.verbose("output differs from log file\n")
                succeeded = false
              }
            }
          })
        }
      case "run" =>
        for (file <- files) {
          runJvmTest(file, kind)
        }
      case "jvm" =>
        for (file <- files) {
          runJvmTest(file, kind)
        }
      case "jvm5" =>
        for (file <- files) {
          runJvmTest(file, kind)
        }
      case "res" => {
        for (file <- files) {
          // when option "--failed" is provided
          // execute test only if log file is present
          // (which means it failed before)

          //val (logFileOut, logFileErr) = createLogFiles(file, kind)
          val logFile = createLogFile(file, kind)
          if (!fileManager.failed || (logFile.exists && logFile.canRead)) {
            val swr = new StringWriter
            val wr = new PrintWriter(swr)
            succeeded = true; diff = ""; log = ""
            printInfoStart(file, wr)

            val fileBase: String = basename(file.getName)
            NestUI.verbose(this+" running test "+fileBase)
            val dir = file.getParentFile
            val outDir = createOutputDir(dir, fileBase, kind)
            if (!outDir.exists) outDir.mkdir()
            val resFile = new File(dir, fileBase + ".res")
            NestUI.verbose("outDir:  "+outDir)
            NestUI.verbose("logFile: "+logFile)
            //NestUI.verbose("logFileErr: "+logFileErr)
            NestUI.verbose("resFile: "+resFile)

            // run compiler in resident mode
            // $SCALAC -d "$os_dstbase".obj -Xresident -sourcepath . "$@"

            try {

            val sourcedir  = logFile.getParentFile.getCanonicalFile
            val sourcepath = sourcedir.getAbsolutePath+File.separator
            NestUI.verbose("sourcepath: "+sourcepath)

            val argString =
              "-d "+outDir.getCanonicalFile.getAbsolutePath+
              " -Xresident"+
              " -sourcepath "+sourcepath
            val argList = List.fromString(argString, ' ')

            // configure input/output files
            val logOut    = new FileOutputStream(logFile)
            val logWriter = new PrintStream(logOut)
            val resReader = new BufferedReader(new FileReader(resFile))
            val logConsoleWriter = new PrintWriter(new OutputStreamWriter(logOut))

            // create compiler
            val settings = new Settings(error)
            settings.sourcepath.value = sourcepath
            settings.classpath.value = fileManager.CLASSPATH
            reporter = new ConsoleReporter(settings, Console.in, logConsoleWriter)
            val command = new CompilerCommand(argList, settings, error, false)
            object compiler extends Global(command.settings, reporter)

            // simulate resident compiler loop
            val prompt = "\nnsc> "

            val resCompile = (line: String) => {
              NestUI.verbose("compiling "+line)
              val cmdArgs = List.fromString(line, ' ') map { fs => new File(dir, fs).getAbsolutePath }
              NestUI.verbose("cmdArgs: "+cmdArgs)
              val sett = new Settings(error)
              sett.sourcepath.value = sourcepath
              val command = new CompilerCommand(cmdArgs, sett, error, true)
              (new compiler.Run) compile command.files
            }

            def loop(action: (String) => Unit) {
              logWriter.print(prompt)
              val line = resReader.readLine()
              if ((line ne null) && line.length() > 0) {
                val parent = self
                self.trapExit = true
                val child = link {
                  action(line)
                }

                receiveWithin(fileManager.timeout.toLong) {
                  case TIMEOUT =>
                    NestUI.verbose("action timed out")
                    false
                  case Exit(from, reason) if from == child => reason match {
                    case 'normal => // do nothing
                    case t: Throwable =>
                      NestUI.verbose("while invoking compiler:")
                      NestUI.verbose("caught "+t)
                      t.printStackTrace
                      if (t.getCause != null)
                        t.getCause.printStackTrace
                      false
                  }
                }

                loop(action)
              }
            }
            val oldStdOut = System.out
            val oldStdErr = System.err
            System.setOut(logWriter)
            System.setErr(logWriter)
            loop(resCompile)
            resReader.close()
            logWriter.flush()
            logWriter.close()
    
            System.setOut(oldStdOut)
            System.setErr(oldStdErr)

            val tempLogFile = new File(dir, fileBase+".temp.log")
            val logFileReader = new BufferedReader(new FileReader(logFile))
            val tempLogFilePrinter = new PrintWriter(new FileWriter(tempLogFile))
            val appender =
              new StreamAppender(logFileReader, tempLogFilePrinter)

	    // function that removes a given string from another string
	    def removeFrom(line: String, path: String): String = {
              // find `path` in `line`
              val index = line.indexOf(path)
              if (index != -1) {
                line.substring(0, index) + line.substring(index + path.length, line.length)
              } else line
            }

            appender.runAndMap({ s =>
              val woPath = removeFrom(s, dir.getAbsolutePath/*.replace(File.separatorChar,'/')*/+File.separator)
              // now replace single '\' with '/'
              woPath.replace('\\', '/')
            })
            logFileReader.close()
            tempLogFilePrinter.close()
            
            val tempLogFileReader = new BufferedReader(new FileReader(tempLogFile))
            val logFilePrinter= new PrintWriter(new FileWriter(logFile), true)
            (new StreamAppender(tempLogFileReader, logFilePrinter)).run
            tempLogFileReader.close()
            logFilePrinter.close()
            
            tempLogFile.delete()
            
            diff = compareOutput(dir, fileBase, kind, logFile)
            if (!diff.equals("")) {
              NestUI.verbose("output differs from log file\n")
              succeeded = false
            }

            } catch {
              case e: Exception =>
	        e.printStackTrace()
                succeeded = false
            }

            // delete log file only if test was successful
            if (succeeded)
              logFile.toDelete = true

            if (!succeeded) {
              errors += 1
              NestUI.verbose("incremented errors: "+errors)
            }
            printInfoEnd(succeeded, wr)           
            wr.flush()
            swr.flush()
            NestUI.normal(swr.toString)

            if (!succeeded && fileManager.showDiff) NestUI.normal(diff)
            if (!succeeded && fileManager.showLog) showLog(logFile)
          }
        }
      }
      case "shootout" => {
        for (file <- files) {
          // when option "--failed" is provided
          // execute test only if log file is present
          // (which means it failed before)
          val logFile = createLogFile(file, kind)
          if (!fileManager.failed || (logFile.exists && logFile.canRead)) {
            val swr = new StringWriter
            val wr = new PrintWriter(swr)
            succeeded = true; diff = ""; log = ""
            printInfoStart(file, wr)

            val fileBase: String = basename(file.getName)
            NestUI.verbose(this+" running test "+fileBase)
            val dir = file.getParentFile
            val outDir = createOutputDir(dir, fileBase, kind)
            if (!outDir.exists) outDir.mkdir()

            // 2. define file {outDir}/test.scala that contains code to compile/run
            val testFile = new File(outDir, "test.scala")
            NestUI.verbose("outDir:   "+outDir)
            NestUI.verbose("logFile:  "+logFile)
            NestUI.verbose("testFile: "+testFile)

            // 3. cat {test}.scala.runner {test}.scala > testFile
            val runnerFile = new File(dir, fileBase+".scala.runner")
            val bodyFile   = new File(dir, fileBase+".scala")
            val appender = StreamAppender.concat(new FileInputStream(runnerFile),
                                                 new FileInputStream(bodyFile),
                                                 new FileOutputStream(testFile))
            appender.run()

            try { // *catch-all*
              // 4. compile testFile
              if (!compileMgr.shouldCompile(List(testFile), kind, logFile)) {
                NestUI.verbose("compilation of "+file+" failed\n")
                succeeded = false
              } else {
                NestUI.verbose("compilation of "+testFile+"succeeded")
                // -------- run test --------
                
                //TODO: detect whether we have to use Runtime.exec
                val useRuntime = true

                if (useRuntime)
                  execTest(outDir, logFile, fileBase)
                else
                  execTestObjectRunner(file, outDir, logFile)
                NestUI.verbose(this+" finished running "+fileBase)
              } // successful compile
            } catch { // *catch-all*
              case e: Exception =>
                NestUI.verbose("caught "+e)
                succeeded = false
            }

            diff = compareOutput(dir, fileBase, kind, logFile)
            if (!diff.equals("")) {
              NestUI.verbose("output differs from log file\n")
              succeeded = false
            }

            // delete log file only if test was successful
            if (succeeded)
              logFile.toDelete = true

            if (!succeeded) {
              errors += 1
              NestUI.verbose("incremented errors: "+errors)
            }
            printInfoEnd(succeeded, wr)           
            wr.flush()
            swr.flush()
            NestUI.normal(swr.toString)

            if (!succeeded && fileManager.showDiff) NestUI.normal(diff)
            if (!succeeded && fileManager.showLog) showLog(logFile)
          }
        }
      }
      case "script" => {
        val osName = System.getProperty("os.name", "")
        for (file <- files) {
          // when option "--failed" is provided
          // execute test only if log file is present
          // (which means it failed before)
          val logFile = createLogFile(file, kind)
          if (!fileManager.failed || (logFile.exists && logFile.canRead)) {
            val swr = new StringWriter
            val wr = new PrintWriter(swr)
            succeeded = true; diff = ""; log = ""
            printInfoStart(file, wr)

            val fileBase: String = basename(file.getName)
            NestUI.verbose(this+" running test "+fileBase)

            // check whether there is an args file
            val argsFile = new File(file.getParentFile, fileBase+".args")
            NestUI.verbose("argsFile: "+argsFile)
            val argString = if (argsFile.exists) {
              val swriter = new StringWriter
              val app = StreamAppender(new BufferedReader(new FileReader(argsFile)),
                                       swriter)
              app.run()
              " "+swriter.toString
            } else ""

            try {
              val cmdString =
                if (osName startsWith "Windows") {
                  val batchFile = new File(file.getParentFile, fileBase+".bat")
                  NestUI.verbose("batchFile: "+batchFile)
                  batchFile.getAbsolutePath
                }
                else file.getAbsolutePath
              val proc = Runtime.getRuntime.exec(cmdString+argString)
              val in = proc.getInputStream
              val err = proc.getErrorStream
              val writer = new PrintWriter(new FileWriter(logFile), true)
              val inApp = new StreamAppender(new BufferedReader(new InputStreamReader(in)),
                                             writer)
              val errApp = new StreamAppender(new BufferedReader(new InputStreamReader(err)),
                                              writer)
              val async = new Thread(errApp)
              async.start()
              inApp.run()
              async.join()
          
              writer.close()

              diff = compareOutput(file.getParentFile, fileBase, kind, logFile)
              if (!diff.equals("")) {
                NestUI.verbose("output differs from log file\n")
                succeeded = false
              }
            } catch { // *catch-all*
              case e: Exception =>
                NestUI.verbose("caught "+e)
                succeeded = false
            }

            // delete log file only if test was successful
            if (succeeded)
              logFile.toDelete = true
            
            if (!succeeded) {
              errors += 1
              NestUI.verbose("incremented errors: "+errors)
            }
            printInfoEnd(succeeded, wr)           
            wr.flush()
            swr.flush()
            NestUI.normal(swr.toString)

            if (!succeeded && fileManager.showDiff) NestUI.normal(diff)
            if (!succeeded && fileManager.showLog) showLog(logFile)
          }
        }
      }
    }
    NestUI.verbose("finished testing "+kind+" with "+errors+" errors")
    NestUI.verbose("created "+compileMgr.numSeparateCompilers+" separate compilers")
    (files.length-errors, errors)
  }

  def showLog(logFile: File) {
    try {
      val logReader = new BufferedReader(new FileReader(logFile))
      val strWriter = new StringWriter
      val logWriter = new PrintWriter(strWriter, true)
      val logAppender = new StreamAppender(logReader, logWriter)
      logAppender.run()
      logReader.close()
      val log = strWriter.toString
      NestUI.normal(log)
    } catch {
      case fnfe: java.io.FileNotFoundException =>
        NestUI.failure("Couldn't open log file \""+logFile+"\".")
    }
  }
}
