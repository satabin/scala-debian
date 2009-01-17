/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SortedSetWrapper.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.collection.jcl;

/** <p>
 *    A wrapper around a Java sorted set.
 *  </p>
 *  <p>
 *    The comparator of the sorted set matches the comparator of this set. 
 *  </p>
 *
 *  @author Sean McDirmid
 */
trait SortedSetWrapper[A] extends SortedSet[A] with SetWrapper[A] {
  def underlying : java.util.SortedSet[A];
  /** delegates to the comparator of the underlying Java sorted set */
  override def compare(a0 : A, a1 : A) = underlying.comparator.compare(a0, a1);
  override def firstKey = underlying.first.asInstanceOf[A];
  override def lastKey  = underlying.last .asInstanceOf[A];
  override def rangeImpl(from : Option[A], until : Option[A]) : SortedSet[A] = new Range(from,until);
  protected class Range(from : Option[A], until : Option[A]) extends super.Range(from, until) with SortedSetWrapper[A] {
    val underlying = Tuple2(from,until) match {
    case Tuple2(None,Some(until)) => SortedSetWrapper.this.underlying.headSet(until);
    case Tuple2(Some(from),None)  => SortedSetWrapper.this.underlying.tailSet(from);
    case Tuple2(Some(from),Some(until)) => SortedSetWrapper.this.underlying.subSet(from,until);
    case _ => throw new IllegalArgumentException;
    }
    override def elements : MutableIterator[A] = super[SortedSetWrapper].elements;
  }
  override def toString = super.toString;
}
