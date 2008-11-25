/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichDouble.scala 14532 2008-04-07 12:23:22Z washburn $


package scala.runtime


final class RichDouble(x: Double) extends Proxy with Ordered[Double] {

  // Proxy.self
  def self: Any = x

  // Ordered[Double].compare
  //def compare(y: Double): Int = if (x < y) -1 else if (x > y) 1 else 0
  def compare(y: Double): Int = java.lang.Double.compare(x, y)

  def min(y: Double): Double = Math.min(x, y)
  def max(y: Double): Double = Math.max(x, y)
  def abs: Double = Math.abs(x)

  def round: Long = Math.round(x)
  def ceil: Double = Math.ceil(x)
  def floor: Double = Math.floor(x)

  /** Converts an angle measured in degrees to an approximately equivalent
   *  angle measured in radians.
   *
   *  @param  x an angle, in degrees
   *  @return the measurement of the angle <code>x</code> in radians.
   */
  def toRadians: Double = Math.toRadians(x)

  /** Converts an angle measured in radians to an approximately equivalent
   *  angle measured in degrees.
   *
   *  @param  x angle, in radians
   *  @return the measurement of the angle <code>x</code> in degrees.
   */
  def toDegrees: Double = Math.toDegrees(x)

  // isNaN is provided by the implicit conversion to java.lang.Double
  // def isNaN: Boolean = java.lang.Double.isNaN(x)
  def isInfinity: Boolean = java.lang.Double.isInfinite(x)
  def isPosInfinity: Boolean = isInfinity && x > 0.0
  def isNegInfinity: Boolean = isInfinity && x < 0.0

}
