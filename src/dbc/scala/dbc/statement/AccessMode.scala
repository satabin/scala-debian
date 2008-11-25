/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id:AccessMode.scala 6853 2006-03-20 16:58:47 +0100 (Mon, 20 Mar 2006) dubochet $


package scala.dbc.statement


abstract class AccessMode {
  def sqlString: String
}

object AccessMode {
  case object ReadOnly extends AccessMode {
    def sqlString = "READ ONLY"
  }
  case object ReadWrite extends AccessMode {
    def sqlString = "READ WRITE"
  }
}
