/* NEST (New Scala Test)
 * Copyright 2007-2008 LAMP/EPFL
 * @author Philipp Haller
 */

// $Id: NestRunner.scala 14415 2008-03-19 00:53:09Z mihaylov $

package scala.tools.partest.nest

object NestRunner {
  def main(args: Array[String]) {
    val argstr = args.mkString(" ")
    (new ReflectiveRunner).main(argstr)
  }
}
