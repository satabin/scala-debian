/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: StackProxy.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.collection.mutable


/** A stack implements a data structure which allows to store and retrieve
 *  objects in a last-in-first-out (LIFO) fashion.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 10/05/2004
 */
trait StackProxy[A] extends Stack[A] with SeqProxy[A] {

  def self: Stack[A]

  /** Access element number <code>n</code>.
   *
   *  @return  the element at index <code>n</code>.
   */
  override def apply(n: Int): A = self.apply(n)

  /** Returns the length of this stack.
   */
  override def length: Int = self.length

  /** Checks if the stack is empty.
   *
   *  @return true, iff there is no element on the stack
   */
  override def isEmpty: Boolean = self.isEmpty

  /** Pushes a single element on top of the stack.
   *
   *  @param  elem        the element to push onto the stack
   */
  override def +=(elem: A): Unit = self += elem

  /** Pushes all elements provided by an <code>Iterable</code> object
   *  on top of the stack. The elements are pushed in the order they
   *  are given out by the iterator.
   *
   *  @param  iter        an iterable object
   */
    override def ++=(iter: Iterable[A]): Unit = self ++= iter

  /** Pushes all elements provided by an iterator
   *  on top of the stack. The elements are pushed in the order they
   *  are given out by the iterator.
   *
   *  @param  iter        an iterator
   */
  override def ++=(it: Iterator[A]): Unit = self ++= it

  /** Pushes a sequence of elements on top of the stack. The first element
   *  is pushed first, etc.
   *
   *  @param  elems       a sequence of elements
   */
  override def push(elems: A*): Unit = self ++= elems

  /** Returns the top element of the stack. This method will not remove
   *  the element from the stack. An error is signaled if there is no
   *  element on the stack.
   *
   *  @return the top element
   */
  override def top: A = self.top

  /** Removes the top element from the stack.
   */
  override def pop(): A = self.pop

  /**
   * Removes all elements from the stack. After this operation completed,
   * the stack will be empty.
   */
  override def clear(): Unit = self.clear

  /** Returns an iterator over all elements on the stack. This iterator
   *  is stable with respect to state changes in the stack object; i.e.
   *  such changes will not be reflected in the iterator. The iterator
   *  issues elements in the order they were inserted into the stack
   *  (FIFO order).
   *
   *  @return an iterator over all stack elements.
   */
  override def elements: Iterator[A] = self.elements

  /** Creates a list of all stack elements in FIFO order.
   *
   *  @return the created list.
   */
  override def toList: List[A] = self.toList

  /** This method clones the stack.
   *
   *  @return  a stack with the same elements.
   */
  override def clone(): Stack[A] = new StackProxy[A] {
    def self = StackProxy.this.self.clone()
  }
}
