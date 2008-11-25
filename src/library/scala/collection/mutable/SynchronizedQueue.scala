/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SynchronizedQueue.scala 10203 2007-03-05 14:21:28Z odersky $


package scala.collection.mutable


/** This is a synchronized version of the <code>Queue[T]</code> class. It
 *  implements a data structure that allows one to insert and retrieve
 *  elements in a first-in-first-out (FIFO) manner.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 03/05/2004
 */
class SynchronizedQueue[A] extends Queue[A] {

  /** Checks if the queue is empty.
   *
   *  @return true, iff there is no element in the queue.
   */
  override def isEmpty: Boolean = synchronized { super.isEmpty }

  /** Inserts a single element at the end of the queue.
   *
   *  @param  elem        the element to insert
   */
  override def +=(elem: A): Unit = synchronized { super.+=(elem) }

  /** Adds all elements provided by an <code>Iterable</code> object
   *  at the end of the queue. The elements are prepended in the order they
   *  are given out by the iterator.
   *
   *  @param  iter        an iterable object
   */
  override def ++=(iter: Iterable[A]): Unit = synchronized { super.++=(iter) }

  /** Adds all elements provided by an iterator
   *  at the end of the queue. The elements are prepended in the order they
   *  are given out by the iterator.
   *
   *  @param  it        an iterator
   */
  override def ++=(it: Iterator[A]): Unit = synchronized { super.++=(it) }

  /** Adds all elements to the queue.
   *
   *  @param  elems       the elements to add.
   */
  override def enqueue(elems: A*): Unit = synchronized { super.++=(elems) }

  /** Returns the first element in the queue, and removes this element
   *  from the queue.
   *
   *  @return the first element of the queue.
   */
  override def dequeue(): A = synchronized { super.dequeue }

  /** Returns the first element in the queue, or throws an error if there
   *  is no element contained in the queue.
   *
   *  @return the first element.
   */
  override def front: A = synchronized { super.front }

  /** Removes all elements from the queue. After this operation is completed,
   *  the queue will be empty.
   */
  override def clear(): Unit = synchronized { super.clear }

  /** Checks if two queues are structurally identical.
   *
   *  @return true, iff both queues contain the same sequence of elements.
   */
  override def equals(that: Any): Boolean = synchronized { super.equals(that) }

  /** The hashCode method always yields an error, since it is not
   *  safe to use mutable queues as keys in hash tables.
   *
   *  @return never.
   */
  override def hashCode(): Int = synchronized { super.hashCode() }

  /** Returns a textual representation of a queue as a string.
   *
   *  @return the string representation of this queue.
   */
  override def toString() = synchronized { super.toString() }
}
