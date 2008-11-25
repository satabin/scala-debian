/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichChar.scala 15285 2008-06-06 18:53:49Z stepancheg $


package scala.runtime


import java.lang.Character
import Predef.NoSuchElementException

/** <p>
 *    For example, in the following code
 *  </p>
 *  <pre>
 *    <b>object</b> test <b>extends</b> Application {
 *      Console.println(<chr>'\40'</chr>.isWhitespace)
 *      Console.println('\011'.isWhitespace)
 *      Console.println('1'.asDigit == 1)
 *      Console.println('A'.asDigit == 10)
 *    }</pre>
 *  <p>
 *    the implicit conversions are performed using the predefined view
 *    <a href="../Predef$object.html#charWrapper(scala.Char)"
 *    target="contentFrame"><code>Predef.charWrapper</code></a>.
 *  </p>
 */
final class RichChar(x: Char) extends Proxy with Ordered[Char] {

  // Proxy.self
  def self: Any = x

  // Ordered[Char].compare
  def compare (y: Char): Int = if (x < y) -1 else if (x > y) 1 else 0

  def asDigit: Int = Character.digit(x, Character.MAX_RADIX)

  def isControl: Boolean = Character.isISOControl(x)
  def isDigit: Boolean = Character.isDigit(x)
  def isLetter: Boolean = Character.isLetter(x)
  def isLetterOrDigit: Boolean = Character.isLetterOrDigit(x)
  def isLowerCase: Boolean = Character.isLowerCase(x)
  def isUpperCase: Boolean = Character.isUpperCase(x)
  def isWhitespace: Boolean = Character.isWhitespace(x)

  def toLowerCase: Char = Character.toLowerCase(x)
  def toUpperCase: Char = Character.toUpperCase(x)
  
  /** Create a <code>RandomAccessSeq.Projection[Char]</code> over the characters from 'x' to 'y' - 1
   */
  def until(limit: Char): RandomAccessSeq.Projection[Char] =
    if (limit <= x) RandomAccessSeq.empty.projection
    else
      new RandomAccessSeq.Projection[Char] {
        def length = limit - x
        def apply(i: Int): Char = {
          Predef.require(i >= 0 && i < length)
          (x + i).toChar
        }
        override def stringPrefix = "RandomAccessSeq.Projection"
      }

  //def until(y: Char): Iterator[Char] = to(y)

  /** Create a <code>RandomAccessSeq.Projection[Char]</code> over the characters from 'x' to 'y'
   */
  def to(y: Char): RandomAccessSeq.Projection[Char] = until((y + 1).toChar)

}
