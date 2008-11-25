/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichBoolean.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.runtime


final class RichBoolean(x: Boolean) extends Proxy with Ordered[Boolean] {

  // Proxy.self
  def self: Any = x

  // Ordered[Boolean].compare
  def compare (y: Boolean): Int = if (x == y) 0 else if (x) 1 else -1

}
