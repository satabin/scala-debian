/* NSC -- new Scala compiler
 * Copyright 2007-2008 LAMP/EPFL
 * @author Lex Spoon
 */
// $Id: PluginComponent.scala 14416 2008-03-19 01:17:25Z mihaylov $

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
