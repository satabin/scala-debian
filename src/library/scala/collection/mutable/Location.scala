/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Location.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.collection.mutable


/** Class <code>Location</code> describes locations in messages implemented
 *  by class <code>Message</code>.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 10/05/2004
 */
abstract class Location

case object NA extends Location

case object Start extends Location

case object End extends Location

case class Index(n: Int) extends Location
