/*                                                                      *\
**     ________ ___   __   ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ |_|                                         **
**                                                                      **
\*                                                                      */

// $Id: StringAdd.scala 15645 2008-07-30 11:02:29Z washburn $


package scala.runtime


import Predef._

final class StringAdd(self: Any) {

  def +(other: String) = String.valueOf(self) + other

  /** Returns string formatted according to given <code>format</code> string.
   *  Format strings are as for <code>String.format</code>
   *  (@see java.lang.String.format).
   *  Only works on Java 1.5 or higher!
   */
  def formatted(fmtstr: String): String = fmtstr format self
}
