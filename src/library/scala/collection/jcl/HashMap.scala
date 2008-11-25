/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://www.scala-lang.org/           **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: HashMap.scala 14532 2008-04-07 12:23:22Z washburn $

package scala.collection.jcl

/** A map that is backed by a Java hash map.
 *
 *  @author Sean McDirmid
 */
class HashMap[K, E](override val underlying: java.util.HashMap[K, E]) extends MapWrapper[K, E] {
  def this() = this(new java.util.HashMap[K, E])
  override def clone: HashMap[K, E] =
    new HashMap[K, E](underlying.clone().asInstanceOf[java.util.HashMap[K, E]])
}
