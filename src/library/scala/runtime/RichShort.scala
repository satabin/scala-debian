/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichShort.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.runtime


final class RichShort(start: Short) extends Proxy with Ordered[Short] {

  // Proxy.self
  def self: Any = start

  // Ordered[Short].compare
  def compare(that: Short): Int = if (start < that) -1 else if (start > that) 1 else 0

}
