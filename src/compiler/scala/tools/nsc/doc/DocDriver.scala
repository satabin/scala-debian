/* NSC -- new Scala compiler
 * Copyright 2007-2008 LAMP/EPFL
 * @author  Sean McDirmid
 */
// $Id: DocDriver.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc.doc

/**
 *  This is an abstract class for documentation plugins.
 *
 *  @author Geoffrey Washburn
 */
abstract class DocDriver {
 val global: Global
 import global._
 def settings: doc.Settings

 def process(units: Iterator[CompilationUnit]): Unit
}
