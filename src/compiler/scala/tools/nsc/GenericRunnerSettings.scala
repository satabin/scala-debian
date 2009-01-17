/* NSC -- new Scala compiler
 * Copyright 2006-2009 LAMP/EPFL
 * @author  Lex Spoon
 */

// $Id: GenericRunnerSettings.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc

import java.lang.System.getProperties
import scala.collection.mutable.Queue

class GenericRunnerSettings(error: String => Unit)
extends Settings(error) {
  val howtorun =
    ChoiceSetting(
      "-howtorun",
      "how to run the specified code",
      List("guess", "object", "script"),
      "guess")
      
  val loadfiles =
    MultiStringSetting(
        "-i",
        "file",
        "load a file (assumes the code is given interactively)")

  val execute =
    StringSetting(
        "-e",
        "string",
        "execute a single command",
        "")

  val savecompiled = 
    BooleanSetting(
        "-savecompiled",
        "save the compiled script (assumes the code is a script)")
        
  val nocompdaemon =
    BooleanSetting(
        "-nocompdaemon",
        "do not use the fsc compilation daemon")


  /** For some reason, "object defines extends Setting(...)"
   *  does not work here.  The object is present but the setting
   *  is not added to allsettings.  Thus, 
   */
  class DefinesSetting extends Setting("set a Java property") {

    def name = "-D<prop>"

    private val props = new Queue[(String, String)]
    
    def value = props.toList
    
    def tryToSet(args: List[String]): List[String] = {
      args match {
        case arg0::rest
        if arg0.startsWith("-D") =>
          val stripD = arg0.substring(2)
          val eqidx = stripD.indexOf('=')
          val addition =
            if (eqidx < 0) 
              (stripD, "")
            else
              (stripD.substring(0, eqidx), stripD.substring(eqidx+1))
          props += addition
          rest

        case _ => args
      }
    }

    /** Apply the specified properties to the current JVM */
    def applyToCurrentJVM = {
      val systemProps = getProperties
      for ((key, value) <- props.toList)
        systemProps.setProperty(key, value)
    }
    
    def unparse: List[String] =
      (props.toList.foldLeft[List[String]]
        (Nil)
        ((args, prop) =>
         ("-D" + prop._1 + "=" + prop._2) :: args))
  }
  
  val defines = new DefinesSetting
}
