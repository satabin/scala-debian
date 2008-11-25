/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichByte.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.runtime


final class RichByte(x: Byte) extends Proxy with Ordered[Byte] {

  // Proxy.self
  def self: Any = x

  // Ordered[Byte].compare
  def compare (y: Byte): Int = if (x < y) -1 else if (x > y) 1 else 0

}
