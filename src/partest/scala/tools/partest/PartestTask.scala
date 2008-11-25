/*                     __                                               *\
**     ________ ___   / /  ___     Scala Parallel Testing               **
**    / __/ __// _ | / /  / _ |    (c) 2007-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: PartestTask.scala 16512 2008-11-06 17:28:08Z dragos $

package scala.tools.partest

import scala.actors.Actor._

import java.io.File
import java.net.URLClassLoader

import org.apache.tools.ant.Task
import org.apache.tools.ant.types.{Path, Reference, FileSet}

class PartestTask extends Task {

  def addConfiguredPosTests(input: FileSet): Unit =
    posFiles = Some(input)

  def addConfiguredPos5Tests(input: FileSet): Unit =
    pos5Files = Some(input)

  def addConfiguredNegTests(input: FileSet): Unit =
    negFiles = Some(input)

  def addConfiguredRunTests(input: FileSet): Unit =
    runFiles = Some(input)

  def addConfiguredJvm5Tests(input: FileSet): Unit =
    jvm5Files = Some(input)

  def addConfiguredResidentTests(input: FileSet): Unit =
    residentFiles = Some(input)

  def addConfiguredScriptTests(input: FileSet): Unit =
    scriptFiles = Some(input)

  def addConfiguredShootoutTests(input: FileSet): Unit =
    shootoutFiles = Some(input)

  def setClasspath(input: Path): Unit =
    if (classpath.isEmpty)
      classpath = Some(input)
    else
      classpath.get.append(input)
  
  def createClasspath(): Path = {
    if (classpath.isEmpty) classpath = Some(new Path(getProject()))
    classpath.get.createPath()
  }
  
  def setClasspathref(input: Reference): Unit =
    createClasspath().setRefid(input)
  
  def setShowLog(input: Boolean): Unit =
    showLog = input
  
  def setShowDiff(input: Boolean): Unit =
    showDiff = input
  
  def setErrorOnFailed(input: Boolean): Unit =
    errorOnFailed = input
    
  def setJavaCmd(input: File): Unit =
    javacmd = Some(input)

  def setJavacCmd(input: File): Unit =
    javaccmd = Some(input)

  def setScalacOpts(opts: String): Unit =
    scalacOpts = Some(opts)

  def setTimeout(delay: String): Unit =
    timeout = Some(delay)

  private var classpath: Option[Path] = None
  private var javacmd: Option[File] = None
  private var javaccmd: Option[File] = None
  private var showDiff: Boolean = false
  private var showLog: Boolean = false
  private var runFailed: Boolean = false
  private var posFiles: Option[FileSet] = None
  private var pos5Files: Option[FileSet] = None
  private var negFiles: Option[FileSet] = None
  private var runFiles: Option[FileSet] = None
  private var jvm5Files: Option[FileSet] = None
  private var residentFiles: Option[FileSet] = None
  private var scriptFiles: Option[FileSet] = None
  private var shootoutFiles: Option[FileSet] = None
  private var errorOnFailed: Boolean = false
  private var scalacOpts: Option[String] = None
  private var timeout: Option[String] = None

  private def getFilesAndDirs(fileSet: Option[FileSet]): Array[File] =
    if (!fileSet.isEmpty) {
      val files = fileSet.get
      val dir = files.getDir(getProject)
      val fileTests = (files.getDirectoryScanner(getProject).getIncludedFiles map { fs =>
        new File(dir, fs) })
      val dirTests = dir.listFiles(new java.io.FileFilter {
        def accept(file: File) =
          file.isDirectory &&
          (!file.getName().equals(".svn")) &&
          (!file.getName().endsWith(".obj"))
      })
      (dirTests ++ fileTests).toArray
    }
    else
      Array()

  private def getPosFiles: Array[File] =
    getFilesAndDirs(posFiles)

  private def getPos5Files: Array[File] =
    getFilesAndDirs(pos5Files)
  
  private def getNegFiles: Array[File] =
    if (!negFiles.isEmpty) {
      val files = negFiles.get
      (files.getDirectoryScanner(getProject).getIncludedFiles map { fs => new File(files.getDir(getProject), fs) })
    }
    else
      Array()
  
  private def getRunFiles: Array[File] =
    if (!runFiles.isEmpty) {
      val files = runFiles.get
      (files.getDirectoryScanner(getProject).getIncludedFiles map { fs => new File(files.getDir(getProject), fs) })
    }
    else
      Array()
  
  private def getJvm5Files: Array[File] =
    getFilesAndDirs(jvm5Files)
  
  private def getResidentFiles: Array[File] =
    if (!residentFiles.isEmpty) {
      val files = residentFiles.get
      (files.getDirectoryScanner(getProject).getIncludedFiles map { fs => new File(files.getDir(getProject), fs) })
    }
    else
      Array()

  private def getScriptFiles: Array[File] =
    if (!scriptFiles.isEmpty) {
      val files = scriptFiles.get
      (files.getDirectoryScanner(getProject).getIncludedFiles map { fs => new File(files.getDir(getProject), fs) })
    }
    else
      Array()

  private def getShootoutFiles: Array[File] =
    if (!shootoutFiles.isEmpty) {
      val files = shootoutFiles.get
      (files.getDirectoryScanner(getProject).getIncludedFiles map { fs => new File(files.getDir(getProject), fs) })
    }
    else
      Array()

  override def execute(): Unit = {
    
    if (classpath.isEmpty)
      error("Mandatory attribute 'classpath' is not set.")
      
    val scalaLibrary =
      (classpath.get.list map { fs => new File(fs) }) find { f =>
        f.getName match {
          case "scala-library.jar" => true
          case "library" if (f.getParentFile.getName == "classes") => true
          case _ => false
        }
      }
    
    if (scalaLibrary.isEmpty)
      error("Provided classpath does not contain a Scala library.") 
    
    val classloader = this.getClass.getClassLoader
    
    val antRunner: AnyRef =
      classloader.loadClass("scala.tools.partest.nest.AntRunner").newInstance().asInstanceOf[AnyRef]
    val antFileManager: AnyRef =
      antRunner.getClass.getMethod("fileManager", Array[Class[_]](): _*).invoke(antRunner, Array[Object](): _*)
    
    val runMethod =
      antRunner.getClass.getMethod("reflectiveRunTestsForFiles", Array(classOf[Array[File]], classOf[String]): _*)
  
    def runTestsForFiles(kindFiles: Array[File], kind: String): (Int, Int) = {
      val result = runMethod.invoke(antRunner, Array(kindFiles, kind): _*).asInstanceOf[Int]
      (result >> 16, result & 0x00FF)
    }
    
    def setFileManagerBooleanProperty(name: String, value: Boolean) = {
      val setMethod =
        antFileManager.getClass.getMethod(name+"_$eq", Array(classOf[Boolean]): _*)
      setMethod.invoke(antFileManager, Array(java.lang.Boolean.valueOf(value)): _*)
    }
    
    def setFileManagerStringProperty(name: String, value: String) = {
      val setMethod =
        antFileManager.getClass.getMethod(name+"_$eq", Array(classOf[String]): _*)
      setMethod.invoke(antFileManager, Array(value): _*)
    }
    
    setFileManagerBooleanProperty("showDiff", showDiff)
    setFileManagerBooleanProperty("showLog", showLog)
    setFileManagerBooleanProperty("failed", runFailed)
    if (!javacmd.isEmpty)
      setFileManagerStringProperty("JAVACMD", javacmd.get.getAbsolutePath)
    if (!javaccmd.isEmpty)
      setFileManagerStringProperty("JAVAC_CMD", javaccmd.get.getAbsolutePath)
    setFileManagerStringProperty("CLASSPATH", classpath.get.list.mkString(File.pathSeparator))
    setFileManagerStringProperty("LATEST_LIB", scalaLibrary.get.getAbsolutePath)
    if (!scalacOpts.isEmpty)
      setFileManagerStringProperty("SCALAC_OPTS", scalacOpts.get)
    if (!timeout.isEmpty)
      setFileManagerStringProperty("timeout", timeout.get)

    var allSucesses: Int = 0
    var allFailures: Int = 0
    
    if (getPosFiles.size > 0) {
      log("Compiling files that are expected to build")
      val (successes, failures) = runTestsForFiles(getPosFiles, "pos")
      allSucesses += successes
      allFailures += failures
    }

    if (getPos5Files.size > 0) {
      log("Compiling files that are expected to build")
      val (successes, failures) = runTestsForFiles(getPos5Files, "pos")
      allSucesses += successes
      allFailures += failures
    }
    
    if (getNegFiles.size > 0) {
      log("Compiling files that are expected to fail")
      val (successes, failures) = runTestsForFiles(getNegFiles, "neg")
      allSucesses += successes
      allFailures += failures
    }
    
    if (getRunFiles.size > 0) {
      log("Compiling and running files")
      val (successes, failures) = runTestsForFiles(getRunFiles, "run")
      allSucesses += successes
      allFailures += failures
    }

    if (getJvm5Files.size > 0) {
      log("Compiling and running files")
      val (successes, failures) = runTestsForFiles(getJvm5Files, "jvm5")
      allSucesses += successes
      allFailures += failures
    }
    
    if (getResidentFiles.size > 0) {
      log("Running resident compiler scenarii")
      val (successes, failures) = runTestsForFiles(getResidentFiles, "res")
      allSucesses += successes
      allFailures += failures
    }

    if (getScriptFiles.size > 0) {
      log("Running script files")
      val (successes, failures) = runTestsForFiles(getScriptFiles, "script")
      allSucesses += successes
      allFailures += failures
    }

    if (getShootoutFiles.size > 0) {
      log("Running shootout tests")
      val (successes, failures) = runTestsForFiles(getShootoutFiles, "shootout")
      allSucesses += successes
      allFailures += failures
    }

    if ((getPosFiles.size + getNegFiles.size + getRunFiles.size + getResidentFiles.size + getScriptFiles.size + getShootoutFiles.size) == 0)
      log("There where no tests to run.")
    else if (allFailures == 0)
      log("Test suite finished with no failures.")
    else if (errorOnFailed)
      error("Test suite finished with " + allFailures + " case" + (if (allFailures > 1) "s" else "") + " failing.")
    else
      log("Test suite finished with " + allFailures + " case" + (if (allFailures > 1) "s" else "") + " failing.")
    
  }
  
}
