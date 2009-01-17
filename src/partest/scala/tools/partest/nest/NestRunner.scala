/* NEST (New Scala Test)
 * Copyright 2007-2009 LAMP/EPFL
 * @author Philipp Haller
 */

// $Id: NestRunner.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.tools.partest.nest

object NestRunner {
  def main(args: Array[String]) {
    val argstr = args.mkString(" ")
    (new ReflectiveRunner).main(argstr)
  }
}
