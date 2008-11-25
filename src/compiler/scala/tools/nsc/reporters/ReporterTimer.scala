/* NSC -- new Scala compiler
 * Copyright 2002-2007 LAMP/EPFL
 * @author Martin Odersky
 */
// $Id: ReporterTimer.scala 13250 2007-11-13 18:32:06Z michelou $

package scala.tools.nsc.reporters

import scala.tools.util.AbstractTimer

/**
 * This class implements a timer that uses a Reporter to issue
 * timings.
 */
class ReporterTimer(reporter: Reporter) extends AbstractTimer {

  def issue(msg: String, duration: Long) =
    reporter.info(null, "[" + msg + " in " + duration + "ms]", false)

}
