/* NSC -- new Scala compiler
 * Copyright 2007-2008 LAMP/EPFL
 * @author Lex Spoon
 */
// $Id: PluginLoadException.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc.plugins

/** ...
 *
 * @author Lex Spoon
 * @version 1.0, 2007-5-21
 */
class PluginLoadException(filename: String, cause: Exception)
extends Exception(cause)
