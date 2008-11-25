/* NSC -- new Scala compiler
 * Copyright 2006-2007 LAMP/EPFL
 * @author  Lex Spoon
 */

// $Id: MainGenericRunner.scala 16595 2008-11-21 16:50:31Z washburn $

package scala.tools.nsc

import java.io.{File, IOException}
import java.lang.{ClassNotFoundException, NoSuchMethodException}
import java.lang.reflect.InvocationTargetException
import java.net.URL

import util.ClassPath

/** An object that runs Scala code.  It has three possible
  * sources for the code to run: pre-compiled code, a script file,
  * or interactive entry.
  */
object MainGenericRunner {
  /** Append jars found in ${scala.home}/lib to
   *  a specified classpath.  Also append "." if the
   *  input classpath is empty; otherwise do not.
   *
   *  @param  classpath
   *  @return ...
   */
  private def addClasspathExtras(classpath: String): String = {
    val scalaHome = Properties.scalaHome

    val extraClassPath =
      if (scalaHome eq null)
        ""
      else {
        def listDir(name:String):Array[File] = {
          val libdir = new File(new File(scalaHome), name)
          if (!libdir.exists || libdir.isFile)
            Array()
          else       
            libdir.listFiles
        }
        {
          val filesInLib = listDir("lib")
          val jarsInLib = 
            filesInLib.filter(f => 
              f.isFile && f.getName.endsWith(".jar"))
          jarsInLib.toList
        } ::: {
          val filesInClasses = listDir("classes")
          val dirsInClasses = 
            filesInClasses.filter(f => f.isDirectory)
          dirsInClasses.toList
        }
      }.mkString("", File.pathSeparator, "")

    if (classpath == "")
      extraClassPath + File.pathSeparator + "."
    else
      classpath + File.pathSeparator + extraClassPath
  }

  def main(args: Array[String]) {
    def error(str: String) = Console.println(str)
    val command = new GenericRunnerCommand(args.toList, error)

    val settings = command.settings
    def sampleCompiler = new Global(settings)

    if (!command.ok) {
      println(command.usageMsg)
      println(sampleCompiler.pluginOptionsHelp)
      return
    }

    settings.classpath.value =
      addClasspathExtras(settings.classpath.value)
    
    settings.defines.applyToCurrentJVM

    if (settings.version.value) {
      Console.println(
        "Scala code runner " +
        Properties.versionString + " -- " +
        Properties.copyrightString)
      return
    }

    if (command.shouldStopWithInfo) {
      Console.println(command.getInfoMessage(sampleCompiler))
      return
    }
  
    def exitSuccess : Nothing = exit(0)
    def exitFailure : Nothing = exit(1)
    def exitCond(b: Boolean) : Nothing = if(b) exitSuccess else exitFailure

    def fileToURL(f: File): Option[URL] =
      try { Some(f.toURL) }
      catch { case e => Console.println(e); None }

    def paths(str: String): List[URL] =
      for (
        file <- ClassPath.expandPath(str) map (new File(_)) if file.exists;
        val url = fileToURL(file); if !url.isEmpty
      ) yield url.get

    def jars(dirs: String): List[URL] =
      for (
        libdir <- ClassPath.expandPath(dirs) map (new File(_)) if libdir.isDirectory;
        jarfile <- libdir.listFiles if jarfile.isFile && jarfile.getName.endsWith(".jar");
        val url = fileToURL(jarfile); if !url.isEmpty
      ) yield url.get

    def specToURL(spec: String): Option[URL] =
      try { Some(new URL(spec)) }
      catch { case e => Console.println(e); None }

    def urls(specs: String): List[URL] =
      if (specs == null || specs.length == 0) Nil
      else for (
        spec <- specs.split(" ").toList;
        val url = specToURL(spec); if !url.isEmpty
      ) yield url.get

    val classpath: List[URL] =
      paths(settings.bootclasspath.value) :::
      paths(settings.classpath.value) :::
      jars(settings.extdirs.value) :::
      urls(settings.Xcodebase.value)

    command.thingToRun match {
      case _ if settings.execute.value != "" =>
        val fullArgs =
	  command.thingToRun.toList ::: command.arguments
        exitCond(ScriptRunner.runCommand(settings, 
		  			 settings.execute.value,
					 fullArgs))

      case None =>
        (new InterpreterLoop).main(settings)

      case Some(thingToRun) =>
        val isObjectName =
          settings.howtorun.value match {
            case "object" => true
            case "script" => false
            case "guess" =>
              ObjectRunner.classExists(classpath, thingToRun)
          }

        if (isObjectName) {
          try {
            ObjectRunner.run(classpath, thingToRun, command.arguments)
          } catch {
            case e: ClassNotFoundException =>
              Console.println(e)
              exitFailure
            case e: NoSuchMethodException =>
              Console.println(e)
              exitFailure
            case e: InvocationTargetException =>
              e.getCause.printStackTrace
              exitFailure
          }
        } else {
          try {
            exitCond(ScriptRunner.runScript(settings, 
					    thingToRun, 
					    command.arguments))
          } catch {
	    case e: IOException =>
              Console.println(e.getMessage())
              exitFailure
            case e: SecurityException =>
              Console.println(e)
              exitFailure
          }
        }
    }
  }
}
