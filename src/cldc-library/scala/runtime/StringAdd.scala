/*                                                                      *\
**     ________ ___   __   ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ |_|                                         **
**                                                                      **
\*                                                                      */

// $Id: StringAdd.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.runtime


import Predef._

final class StringAdd(self: Any) {

  def +(other: String) = self.toString + other

}

