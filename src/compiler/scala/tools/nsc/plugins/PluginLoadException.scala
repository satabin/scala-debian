/* NSC -- new Scala compiler
 * Copyright 2007-2009 LAMP/EPFL
 * @author Lex Spoon
 */
// $Id: PluginLoadException.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.tools.nsc.plugins

/** ...
 *
 * @author Lex Spoon
 * @version 1.0, 2007-5-21
 */
class PluginLoadException(filename: String, cause: Exception)
extends Exception(cause)
