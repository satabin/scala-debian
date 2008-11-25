package scala.tools.ant.sabbus

import java.io.File;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.{MatchingTask, Java}
import org.apache.tools.ant.util.{GlobPatternMapper, SourceFileScanner}

class ScalacFork extends MatchingTask with TaskArgs {
  def setSrcdir(input: File) {
    sourceDir = Some(input)
  }
  
  def setFailOnError(input: Boolean): Unit = {
    failOnError = input
  }
  
  def setTimeout(input: Long): Unit = {
    timeout = Some(input)
  }

  def setMaxMemory(input: String): Unit = {
    maxmemory = Some(input)
  }
  
  private var sourceDir: Option[File] = None
  private var failOnError: Boolean = true
  private var timeout: Option[Long] = None
  private var maxmemory: Option[String] = None

  override def execute() {
    if (compilerPath.isEmpty) error("Mandatory attribute 'compilerpath' is not set.")
    if (sourceDir.isEmpty) error("Mandatory attribute 'srcdir' is not set.")
    if (destinationDir.isEmpty) error("Mandatory attribute 'destdir' is not set.")

    val settings = new Settings
    settings.d = destinationDir.get
    if (!compTarget.isEmpty) settings.target = compTarget.get
    if (!compilationPath.isEmpty) settings.classpath = compilationPath.get
    if (!sourcePath.isEmpty) settings.sourcepath = sourcePath.get
    if (!params.isEmpty) settings.more = params.get
    
    // not yet used: compilerPath, sourcedir (used in mapper), failonerror, timeout
    
    val mapper = new GlobPatternMapper()
    mapper.setTo("*.class")
    mapper.setFrom("*.scala")
    val includedFiles: Array[File] =
      new SourceFileScanner(this).restrict(
        getDirectoryScanner(sourceDir.get).getIncludedFiles,
        sourceDir.get,
        destinationDir.get,
        mapper
      ) map (new File(sourceDir.get, _))
    if (includedFiles.size > 0) {
      log("Compiling "+ includedFiles.size +" file"+
            (if (includedFiles.size > 1) "s" else "") +" to "+ destinationDir.get)

      val java = new Java(this) // set this as owner
      java.setFork(true)
      java.setClasspath(compilerPath.get)
      java.setClassname("scala.tools.nsc.Main")
      if (!timeout.isEmpty) java.setTimeout(timeout.get)
      if (!maxmemory.isEmpty) java.setMaxmemory(maxmemory.get)
      for (arg <- settings.toArgs)
        java.createArg().setValue(arg)
      for (file <- includedFiles)
        java.createArg().setFile(file)
      
      log(java.getCommandLine.getCommandline.mkString("", " ", ""), Project.MSG_VERBOSE)
      val res = java.executeJava()
      if (failOnError && res != 0)
        error("Compilation failed because of an internal compiler error;"+
              " see the error output for details.")
    }
  }
}
