/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: UnavailableResourceException.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.xml.include

/**
 * <p>
 * An <code>UnavailableResourceException</code> is thrown when
 * an included document cannot be found or loaded.
 * </p>
 *
 */
class UnavailableResourceException(message: String) 
extends XIncludeException(message) {
  def this() = this(null)
}
