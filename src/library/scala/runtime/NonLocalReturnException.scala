/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: NonLocalReturnException.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.runtime


import Predef.RuntimeException

class NonLocalReturnException[T](val key: AnyRef, val value: T) extends RuntimeException {
  /*
   * For efficiency reasons we do not fill in
   * the execution stack trace.
   */
  override def fillInStackTrace(): Throwable = this
}
