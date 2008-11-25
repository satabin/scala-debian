/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: UnavailableResourceException.scala 12905 2007-09-18 09:13:45Z michelou $

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
