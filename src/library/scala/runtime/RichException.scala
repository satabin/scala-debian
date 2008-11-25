/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichException.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.runtime

import Predef._
import compat.Platform.EOL

final class RichException(exc: Throwable) {

  def getStackTraceString: String = {
    val s = new StringBuilder()
    for (trElem <- exc.getStackTrace()) {
      s.append(trElem.toString())
      s.append(EOL)
    }
    s.toString()
  }

}
