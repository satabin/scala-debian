/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://www.scala-lang.org/           **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: TreeMap.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.collection.jcl

/** A sorted map that is backed by a Java tree map.
 *
 * @author Sean McDirmid
 */
class TreeMap[K <% Ordered[K], E] extends SortedMapWrapper[K, E] { tm =>
  val underlying = (new java.util.TreeMap[K, E](new Comparator[K]))
  override def clone: TreeMap[K, E] =
    new TreeMap[K, E] {
      override val underlying =
        tm.underlying.clone().asInstanceOf[java.util.TreeMap[K, E]]
    }
}
