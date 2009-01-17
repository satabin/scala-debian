/* NSC -- new Scala compiler
 * Copyright 2007-2009 LAMP/EPFL
 * @author  Sean McDirmid
 */
// $Id: DocDriver.scala 16881 2009-01-09 16:28:11Z cunei $

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
