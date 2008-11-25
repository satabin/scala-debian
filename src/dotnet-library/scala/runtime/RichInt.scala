/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichInt.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.runtime


final class RichInt(start: Int) extends Proxy with Ordered[Int] {

  // Proxy
  def self: Any = start

  // Ordered[Int]
  def compare(that: Int): Int = if (start < that) -1 else if (start > that) 1 else 0

  /** See <code>Iterator.range</code>. */
  def until(end: Int): Range = new Range(start, end, 1)

  /** See <code>Iterator.range</code>. */
  def until(end: Int, step: Int): Range = new Range(start, end, step)

  /** like <code>until</code>, but includes the last index */
  def to(end: Int) = new Range.Inclusive(start, end, 1)

  def min(that: Int): Int = if (start < that) start else that
  def max(that: Int): Int = if (start > that) start else that
  def abs: Int = if (start < 0) -start else start

  def toBinaryString: String = System.Convert.ToString(start, 2)
  def toHexString: String = System.Convert.ToString(start, 16)
  def toOctalString: String = System.Convert.ToString(start, 8)
}
