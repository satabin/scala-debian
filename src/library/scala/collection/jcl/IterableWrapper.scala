/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: IterableWrapper.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.collection.jcl;

/** A wrapper around a Java collection that only supports remove mutations.
 *
 *  @author Sean McDirmid 
 */
trait IterableWrapper[A] extends MutableIterable[A] {
  def underlying: java.util.Collection[A];
  override def remove(a: A) = underlying.remove(a);
  override def removeAll(that: Iterable[A]) = that match {
    case that: IterableWrapper[_] => underlying.removeAll(that.underlying);
    case _ => super.removeAll(that);
  }
  override def retainAll(that : Iterable[A]) = that match {
    case that : IterableWrapper[_] => underlying.retainAll(that.underlying);
    case _ => super.retainAll(that);
  }
  override def size = underlying.size;
  override def isEmpty = underlying.isEmpty;
  override def clear = underlying.clear;
  override def elements : MutableIterator[A] = new MutableIterator.Wrapper[A](underlying.iterator);
}
