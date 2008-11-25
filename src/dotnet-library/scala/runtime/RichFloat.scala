/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichFloat.scala 15115 2008-05-20 14:57:39Z rytz $


package scala.runtime


import Predef._

final class RichFloat(x: Float) extends Proxy with Ordered[Float] {

  // Proxy.self
  def self: Any = x

  // Ordered[Float].compare
  def compare (y: Float): Int = if (x < y) -1 else if (x > y) 1 else 0

  def min(y: Float) = Math.min(x, y)
  def max(y: Float) = Math.max(x, y)
  def abs: Float = Math.abs(x)

  def round: Int = Math.round(x)
  def ceil: Float = Math.ceil(x).toFloat
  def floor: Float = Math.floor(x).toFloat

  /** Converts an angle measured in degrees to an approximately equivalent
   *  angle measured in radians.
   *
   *  @param  x an angle, in degrees
   *  @return the measurement of the angle <code>x</code> in radians.
   */
  def toRadians: Float = Math.toRadians(x).toFloat

  /** Converts an angle measured in radians to an approximately equivalent
   *  angle measured in degrees.
   *
   *  @param  x angle, in radians
   *  @return the measurement of the angle <code>x</code> in degrees.
   */
  def toDegrees: Float = Math.toDegrees(x).toFloat

  def isNaN: Boolean = System.Single.IsNaN(x)
  def isInfinity: Boolean = System.Single.IsInfinity(x)
  def isPosInfinity: Boolean = System.Single.IsPositiveInfinity(x)
  def isNegInfinity: Boolean = System.Single.IsNegativeInfinity(x)

}
