/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: OfflineCompilerCommand.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc

/** A compiler command for the offline compiler.
 *
 * @author Martin Odersky and Lex Spoon
 */
class OfflineCompilerCommand(
  arguments: List[String],
  settings: Settings,
  error: String => Unit, 
  interactive: Boolean) 
extends CompilerCommand(arguments, new Settings(error), error, false)
{
  override val cmdName = "fsc"
  settings.disable(settings.prompt)
  settings.disable(settings.resident)
  new settings.BooleanSetting("-reset", "Reset compile server caches")
  new settings.BooleanSetting("-shutdown", "Shutdown compile server")
  new settings.StringSetting("-server", "hostname:portnumber", 
                             "Specify compile server socket", "")
  new settings.BooleanSetting("-J<flag>", "Pass <flag> directly to runtime system")
}
