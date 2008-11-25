/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SortedMap.scala 11911 2007-06-05 15:57:59Z odersky $

package scala.collection;

/** A map whose keys are sorted.
 *
 *  @author Sean McDirmid
 */
trait SortedMap[K,+E] extends Map[K,E] with Sorted[K,Tuple2[K,E]] {
  override def firstKey : K = elements.next._1;
  override def lastKey : K = {
    val i = elements;
    var last : K = i.next._1;
    while (i.hasNext) last = i.next._1;
    last;
  }
  
  // XXX: implement default version
  override def rangeImpl(from : Option[K], until : Option[K]) : SortedMap[K,E];
  override def from(from: K) = rangeImpl(Some(from), None);
  override def until(until: K) = rangeImpl(None, Some(until));
  override def range(from: K, until: K) = rangeImpl(Some(from),Some(until));
  
  protected class DefaultKeySet extends SortedSet[K] {
    def size = SortedMap.this.size
    def contains(key : K) = SortedMap.this.contains(key)
    def elements = SortedMap.this.elements.map(_._1)
    def compare(k0 : K, k1 : K) = SortedMap.this.compare(k0, k1);
    override def rangeImpl(from : Option[K], until : Option[K]) : SortedSet[K] = {
      val map = SortedMap.this.rangeImpl(from,until);
      new map.DefaultKeySet;
    }
  }
  // XXX: implement default version
  override def keySet : SortedSet[K] = new DefaultKeySet;
}
