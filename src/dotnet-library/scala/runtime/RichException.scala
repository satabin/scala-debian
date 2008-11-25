/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichException.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.runtime


final class RichException(exc: System.Exception) {

  def printStackTrace() = System.Console.WriteLine(exc.StackTrace)
  def getMessage() = exc.Message
  def getStackTraceString: String = exc.StackTrace

}
