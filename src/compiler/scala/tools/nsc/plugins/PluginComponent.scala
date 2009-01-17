/* NSC -- new Scala compiler
 * Copyright 2007-2009 LAMP/EPFL
 * @author Lex Spoon
 */
// $Id: PluginComponent.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.tools.nsc.plugins

/** A component that is part of a Plugin.
 *
 * @author Lex Spoon
 * @version 1.0, 2007/5/29
 */
abstract class PluginComponent extends SubComponent {
  /** the phase this plugin wants to run after */
  val runsAfter: String
}
