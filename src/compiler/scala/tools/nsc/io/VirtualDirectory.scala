/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 */
// $Id$
package scala.tools.nsc.io
import scala.collection.{mutable=>mut}

/**
 * An in-memory directory.
 * 
 * @author Lex Spoon
 */
class VirtualDirectory(val name: String, maybeContainer: Option[VirtualDirectory])
extends AbstractFile {
  def path: String =
    maybeContainer match {
      case None => name
      case Some(parent) => parent.path+'/'+ name
    }
  def container = maybeContainer.get
  def isDirectory = true
  var lastModified: Long = System.currentTimeMillis
  private def updateLastModified {
    lastModified = System.currentTimeMillis
  }
  override def file = null
  override def input = error("directories cannot be read")
  override def output = error("directories cannot be written")
  
  private val files = mut.Map.empty[String, AbstractFile]

  // the toList is so that the directory may continue to be
  // modified while its elements are iterated
  def elements = files.values.toList.elements
  
  override def lookupName(name: String, directory: Boolean): AbstractFile = {
    files.get(name) match {
      case None => null
      case Some(file) =>
        if (file.isDirectory == directory)
          file
        else
          null
    }
  }
    
  override def fileNamed(name: String): AbstractFile = {
    val existing = lookupName(name, false)
    if (existing == null) {
      val newFile = new VirtualFile(name, path+'/'+name)
      files(name) = newFile
      newFile
    } else {
      existing
    }
  }
  
  override def subdirectoryNamed(name: String): AbstractFile = {
    val existing = lookupName(name, true)
    if (existing == null) {
      val dir = new VirtualDirectory(name, Some(this))
      dir
    } else {
      existing
    }
  }
}
