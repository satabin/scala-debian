/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ListBuffer.scala 14378 2008-03-13 11:39:05Z dragos $


package scala.collection.mutable


import Predef._

/** A Buffer implementation back up by a list. It provides constant time
 *  prepend and append. Most other operations are linear.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 15/03/2004
 */
@serializable
final class ListBuffer[A] extends Buffer[A] {
  private var start: List[A] = Nil
  private var last0: ::[A] = _
  private var exported: Boolean = false

  /** Prepends a single element to this buffer. It takes constant
   *  time.
   *
   *  @param x  the element to prepend.
   *  @return   this buffer.
   */
  def +: (x: A): Buffer[A] = {
    if (exported) copy()
    val newElem = new scala.:: (x, start)
    if (start.isEmpty) last0 = newElem
    start = newElem
    this
  }
  
  

  /** Appends a single element to this buffer. It takes constant
   *  time.
   *
   *  @param x  the element to append.
   */
  override def += (x: A) {
    if (exported) copy()
    if (start.isEmpty) {
      last0 = new scala.:: (x, Nil)
      start = last0
    } else {
      val last1 = last0
      last0 = new scala.:: (x, Nil)
      last1.tl = last0
    }
  }

  /** Removes a single element from the buffer and return
   *  the identity of the buffer. Same as <code>this -= x; this</code>. It
   *  takes linear time (except removing the first element, which is done 
   *  in constant time).
   *
   *  @param x  the element to remove.
   *  @return   this buffer.
   */
  def - (x: A): Buffer[A] = { this -= x; this }

  /** Remove a single element from this buffer. It takes linear time
   *  (except removing the first element, which is done  in constant time).
   *
   *  @param x  the element to remove.
   */
  override def -= (x: A) {
    if (exported) copy()
    if (start.isEmpty) {}
    else if (start.head == x) start = start.tail
    else {
      var cursor = start
      while (!cursor.tail.isEmpty && cursor.tail.head != x) { cursor = cursor.tail }
      if (!cursor.tail.isEmpty) {
        val z = cursor.asInstanceOf[scala.::[A]]
        if (z.tl == last0)
          last0 = z
        z.tl = cursor.tail.tail
      }
    }
  }

  /** Converts this buffer to a list. Takes constant time. The buffer is 
   *  copied lazily, the first time it is mutated.
   */
  override def toList: List[A] = {
    exported = !start.isEmpty
    start
  }
  /** expose the underlying list but do not mark it as exported */
  override def readOnly : List[A] = start

  /** Prepends the elements of this buffer to a given list
   *
   *  @param xs   the list to which elements are prepended
   */
  def prependToList(xs: List[A]): List[A] =
    if (start.isEmpty) xs
    else { last0.tl = xs; toList }

  /** Clears the buffer contents.
   */
  def clear() {
    start = Nil
    exported = false
  }

  /** Copy contents of this buffer */
  private def copy() {
    var cursor = start
    val limit = last0.tail
    clear
    while (cursor ne limit) {
      this += cursor.head
      cursor = cursor.tail
    }
  }

  /** Returns the length of this buffer. It takes linear time.
   *
   *  @return the length of this buffer.
   */
  def length: Int = start.length
  
  // will be faster since this is a list
  override def isEmpty = start.isEmpty

  /** Returns the n-th element of this list. This method
   *  yields an error if the element does not exist. Takes time
   *  linear in the buffer size.
   *
   *  @param n  the position of the element to be returned.
   *  @return   the n-th element of this buffer.
   *  @throws Predef.IndexOutOfBoundsException
   */
  def apply(n: Int): A = try {
    start(n)
  } catch {
    case ex: Exception =>
      throw new IndexOutOfBoundsException(n.toString())
  }

  /** Replaces element at index <code>n</code> with the new element
   *  <code>newelem</code>. Takes time linear in the buffer size. (except the first
   *  element, which is updated in constant time).
   *
   *  @param n  the index of the element to replace.
   *  @param x  the new element.
   *  @throws Predef.IndexOutOfBoundsException if <code>n</code> is out of bounds.
   */
  def update(n: Int, x: A) {
    try {
      if (exported) copy()
      if (n == 0) {
        val newElem = new scala.:: (x, start.tail);
        if (last0 eq start) last0 = newElem
        start = newElem
      } else {
        var cursor = start
        var i = 1
        while (i < n) {
          cursor = cursor.tail
          i += 1
        }
        val newElem = new scala.:: (x, cursor.tail.tail)
        if (last0 eq cursor.tail) last0 = newElem
        cursor.asInstanceOf[scala.::[A]].tl = newElem
      }
    } catch {
      case ex: Exception => throw new IndexOutOfBoundsException(n.toString())
    }
  }

  /** Inserts new elements at the index <code>n</code>. Opposed to method
   *  <code>update</code>, this method will not replace an element with a new
   *  one. Instead, it will insert a new element at index <code>n</code>.
   *
   *  @param  n     the index where a new element will be inserted.
   *  @param  iter  the iterable object providing all elements to insert.
   *  @throws Predef.IndexOutOfBoundsException if <code>n</code> is out of bounds.
   */
  def insertAll(n: Int, iter: Iterable[A]) {
    try {
      if (exported) copy()
      var elems = iter.elements.toList.reverse
      if (n == 0) {
        while (!elems.isEmpty) {
          val newElem = new scala.:: (elems.head, start)
          if (start.isEmpty) last0 = newElem
          start = newElem
          elems = elems.tail
        }
      } else {
        var cursor = start
        var i = 1
        while (i < n) {
          cursor = cursor.tail
          i += 1
        }
        while (!elems.isEmpty) {
          val newElem = new scala.:: (elems.head, cursor.tail)
          if (cursor.tail.isEmpty) last0 = newElem
          cursor.asInstanceOf[scala.::[A]].tl = newElem
          elems = elems.tail
        }
      }
    } catch {
      case ex: Exception =>
        throw new IndexOutOfBoundsException(n.toString())
    }
  }

  /** Removes the element on a given index position. Takes time linear in
   *  the buffer size (except for the first element, which is removed in constant
   *  time).
   *
   *  @param  n  the index which refers to the element to delete.
   *  @return n  the element that was formerly at position <code>n</code>.
   *  @pre       an element exists at position <code>n</code>
   *  @throws Predef.IndexOutOfBoundsException if <code>n</code> is out of bounds.
   */
  def remove(n: Int): A = try {
    if (exported) copy()
    var old = start.head;
    if (n == 0) {
      start = start.tail
    } else {
      var cursor = start
      var i = 1
      while (i < n) {
        cursor = cursor.tail
        i += 1
      }
      old = cursor.tail.head
      if (last0 eq cursor.tail) last0 = cursor.asInstanceOf[scala.::[A]]
      cursor.asInstanceOf[scala.::[A]].tl = cursor.tail.tail
    }
    old
  } catch {
    case ex: Exception =>
      throw new IndexOutOfBoundsException(n.toString())
  }

  /** <p>
   *    Returns an iterator over all elements of this list.
   *  </p>
   *  <blockquote>
   *    Note: the iterator can be affected by insertions, updates and
   *    deletions that are performed afterwards on the buffer. To get
   *    iterator an over the current buffer snapshot, use
   *    <code>toList.elements</code>.
   *  </blockquote>
   *
   *  @throws Predef.NoSuchElementException if buffer is empty
   */
  override def elements = new Iterator[A] {
    var cursor: List[A] = null
    def hasNext: Boolean = !start.isEmpty && cursor != last0
    def next(): A =
      if (!hasNext) {
        throw new NoSuchElementException("next on empty Iterator")
      } else {
        if (cursor eq null) cursor = start else cursor = cursor.tail
        cursor.head
      }
  }

  /** Returns a clone of this buffer.
   *
   *  @return a <code>ListBuffer</code> with the same elements.
   */
  override def clone(): Buffer[A] = (new ListBuffer[A]) ++ this

  /** Checks if two buffers are structurally identical.
   *
   *  @return <code>true</code>, iff both buffers contain the same sequence
   *          of elements.
   */
  override def equals(obj: Any): Boolean = obj match {
    case that: ListBuffer[_] =>
      (this.length == that.length &&
       elements.zip(that.elements).forall {
         case (thiselem, thatelem) => thiselem == thatelem
       })
    case _ =>
      false
  }

  /** Defines the prefix of the string representation.
   *
   *  @return the string representation of this buffer.
   */
  override protected def stringPrefix: String = "ListBuffer"
}

