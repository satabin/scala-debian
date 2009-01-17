/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichException.scala 16881 2009-01-09 16:28:11Z cunei $


package scala.runtime


final class RichException(exc: System.Exception) {

  def printStackTrace() = System.Console.WriteLine(exc.StackTrace)
  def getMessage() = exc.Message
  def getStackTraceString: String = exc.StackTrace

}
