/* NSC -- new Scala compiler
 * Copyright 2002-2009 LAMP/EPFL
 * @author Martin Odersky
 */
// $Id: ReporterTimer.scala 16894 2009-01-13 13:09:41Z cunei $

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
