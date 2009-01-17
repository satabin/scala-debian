/*                                                                      *\
**     ________ ___   __   ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ |_|                                         **
**                                                                      **
\*                                                                      */

// $Id: StringAdd.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.runtime


import Predef._

final class StringAdd(self: Any) {

  def +(other: String) = self.toString + other

}

