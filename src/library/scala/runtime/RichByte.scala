/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichByte.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.runtime


final class RichByte(x: Byte) extends Proxy with Ordered[Byte] {

  // Proxy.self
  def self: Any = x

  // Ordered[Byte].compare
  def compare (y: Byte): Int = if (x < y) -1 else if (x > y) 1 else 0

}
