/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://www.scala-lang.org/           **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: HashSet.scala 16881 2009-01-09 16:28:11Z cunei $

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
