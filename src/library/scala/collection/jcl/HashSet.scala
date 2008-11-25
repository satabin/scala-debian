/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://www.scala-lang.org/           **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: HashSet.scala 14532 2008-04-07 12:23:22Z washburn $

package scala.collection.jcl

/** A hash set that is backed by a Java hash set. 
 *
 *  @author Sean McDirmid
 */
class HashSet[A](override val underlying: java.util.HashSet[A]) extends SetWrapper[A] {
  def this() = this(new java.util.HashSet[A])
  override def clone: HashSet[A] =
    new HashSet[A](underlying.clone().asInstanceOf[java.util.HashSet[A]])
}
