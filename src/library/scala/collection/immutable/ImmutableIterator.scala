/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ImmutableIterator.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.collection.immutable

/** An object for creating immutable iterators.
  */
object ImmutableIterator {
  case object Empty extends ImmutableIterator[Nothing] {
    def hasNext = false
    def next = throw new NoSuchElementException
  }

  private case class NonEmpty[+A](item: A, right: () => ImmutableIterator[A]) extends ImmutableIterator[A] {
    def hasNext = true
    def next = Tuple2(item, right())
  }

  /** Creates an empty immutable iterator.
   */
  def empty : ImmutableIterator[Nothing] = Empty

  /** Creates an immutable iterator with one element.
   */
  def apply[A](item : A) : ImmutableIterator[A] = NonEmpty(item, () => Empty)

  /** Prepends a lazy immutable iterator (right) with an element (item).
   */
  def apply[A](item : A, right : () => ImmutableIterator[A]) : () => ImmutableIterator[A] =
    () => NonEmpty(item, right)

  /** Appends an immutable iterator (left) with an element (item) followed
   *  by a lazy immutable iterator (right).
   */
  def apply[A](left : ImmutableIterator[A], item : A, right : () => ImmutableIterator[A]) : ImmutableIterator[A] = left match {
  case NonEmpty(first, middle) => 
    val rest = NonEmpty(item,right);
    NonEmpty(first, apply(middle, () => rest));  
  case Empty => NonEmpty(item, right);
  }

  /** Concats a lazy immutable iterator (left) with another lazy immutable
   *  iterator (right).
   */
  def apply[A](left: () => ImmutableIterator[A], right: () => ImmutableIterator[A]): () => ImmutableIterator[A] = () => (left() match {
    case Empty => right()
    case NonEmpty(item, middle) => NonEmpty(item, apply(middle, right))
  });
}

/** A stateless iterator.
 *
 *  @author  Sean McDirmid
 *  @version 1.0
 */
sealed abstract class ImmutableIterator[+A] {

  /** queries if this iterator has an element to return.
   */
  def hasNext: Boolean

  /** returns the next element and immutable iterator as a pair.
   */
  def next: Tuple2[A,ImmutableIterator[A]]

  /** Creates a new immutable iterator that appends item to this immutable
   *  iterator.
   */
  def append[B >: A](item: B): ImmutableIterator[B] = append(item, () => ImmutableIterator.Empty)

  /** Creates a new immutable iterator that appends item  and a lazy immutable
   *  iterator (right) to this immutable iterator.
   *
   *  @param item  ...
   *  @param right ...
   *  @return      ...
   */
  def append[B >: A](item: B, right: () => ImmutableIterator[B]): ImmutableIterator[B] =
    ImmutableIterator[B](this, item, right)

  /** Creates a new immutable iterator that appends a lazy immutable
   *  iterator (right) to this immutable iterator.
   */
  def append[B >: A](right: () => ImmutableIterator[B]) =
    ImmutableIterator(() => this, right)()

  private class Elements extends Iterator[A] {
    private[this] var cursor: ImmutableIterator[A] = ImmutableIterator.this
    def hasNext = cursor.hasNext
    def next = {
      val Tuple2(ret,cursor0) = cursor.next
      cursor = cursor0
      ret
    }
  }

  /** Converts this immutable iterator into a conventional iterator.
   */
  def elements: Iterator[A] = new Elements
}




