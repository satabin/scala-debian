/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: PriorityQueueProxy.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.collection.mutable


/** This class implements priority queues using a heap. The
 *  elements of the queue have to be ordered in terms of the
 *  <code>Ordered[T]</code> class.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 03/05/2004
 */
abstract class PriorityQueueProxy[A <% Ordered[A]] extends PriorityQueue[A]
         with RandomAccessSeqProxy[A]
{

  def self: PriorityQueue[A]

  /** Creates a new iterator over all elements contained in this
   *  object.
   *
   *  @return the new iterator
   */
  override def elements: Iterator[A] = self.elements

  /** Returns the length of this priority queue.
   */
  override def length: Int = self.length

  /** Checks if the queue is empty.
   *
   *  @return true, iff there is no element in the queue.
   */
  override def isEmpty: Boolean = self.isEmpty

  /** Inserts a single element into the priority queue.
   *
   *  @param  elem        the element to insert
   */
  override def +=(elem: A): Unit = self += elem

  /** Adds all elements provided by an <code>Iterable</code> object
   *  into the priority queue.
   *
   *  @param  iter        an iterable object
   */
  override def ++=(iter: Iterable[A]): Unit = self ++= iter

  /** Adds all elements provided by an iterator into the priority queue.
   *
   *  @param  it        an iterator
   */
  override def ++=(it: Iterator[A]): Unit = self ++= it

  /** Adds all elements to the queue.
   *
   *  @param  elems       the elements to add.
   */
  override def enqueue(elems: A*): Unit = self ++= elems

  /** Returns the element with the highest priority in the queue,
   *  and removes this element from the queue.
   *
   *  @return   the element with the highest priority.
   */
  override def dequeue(): A = self.dequeue

  /** Returns the element with the highest priority in the queue,
   *  or throws an error if there is no element contained in the queue.
   *
   *  @return   the element with the highest priority.
   */
  override def max: A = self.max

  /** Removes all elements from the queue. After this operation is completed,
   *  the queue will be empty.
   */
  override def clear(): Unit = self.clear

  /** Returns a regular queue containing the same elements.
   */
  override def toQueue: Queue[A] = self.toQueue

  /** This method clones the priority queue.
   *
   *  @return  a priority queue with the same elements.
   */
  override def clone(): PriorityQueue[A] = new PriorityQueueProxy[A] {
    def self = PriorityQueueProxy.this.self.clone()
  }
}
