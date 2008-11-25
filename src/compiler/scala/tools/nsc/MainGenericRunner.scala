/* NSC -- new Scala compiler
 * Copyright 2006-2007 LAMP/EPFL
 * @author  Lex Spoon
 */

// $Id: MainGenericRunner.scala 15196 2008-05-25 15:02:28Z spoon $

package scala.tools.nsc

import java.io.File
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
        val libdir = new File(new File(scalaHome), "lib")
        if (!libdir.exists || libdir.isFile)
          return classpath
        
        val filesInLib = libdir.listFiles
        val jarsInLib = 
          filesInLib.filter(f => 
            f.isFile && f.getName.endsWith(".jar"))
  
        jarsInLib.mkString("", File.pathSeparator, "")
      }
    
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


    if (settings.help.value || settings.Xhelp.value || settings.Yhelp.value) {
      if (command.settings.help.value) {
        println(command.usageMsg)
        println(sampleCompiler.pluginOptionsHelp)
      }

      if (settings.Xhelp.value) 
        println(command.xusageMsg)

      if (settings.Yhelp.value) 
        println(command.yusageMsg)

      return
    }


    if (settings.showPhases.value) {
      println(sampleCompiler.phaseDescriptions)
      return
    }

    if (settings.showPlugins.value) {
      println(sampleCompiler.pluginDescriptions)
      return
    }

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
        ScriptRunner.runCommand(settings, 
				settings.execute.value,
				fullArgs)

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
              exit(1)
            case e: NoSuchMethodException =>
              Console.println(e)
              exit(1)
            case e: InvocationTargetException =>
              e.getCause.printStackTrace
              exit(1)
          }

        } else {
          try {
            ScriptRunner.runScript(settings, thingToRun, command.arguments)
          } catch {
            case e: SecurityException =>
              Console.println(e)
              exit(1)
          }
        }
    }
  }
}
