/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: ListBuffer.scala 12005 2007-06-13 12:28:07Z michelou $

package scala.tools.nsc.util

class ListBuffer[T] extends Iterator[T] {

  private var first = new LinkedList[T]
  private var limit = first

  def +=(x: T) {
    limit.elem = x
    limit.next = new LinkedList[T]
    limit = limit.next
  }

  def ++=(xs: Iterable[T]) {
    for (x <- xs.elements) +=(x)
  }

  def +(x: T): ListBuffer[T] = { +=(x); this }
  def ++(xs: Iterable[T]): ListBuffer[T] = { ++=(xs); this }

  def hasNext: Boolean =
    first != limit

  def next: T = {
    assert(hasNext)
    val x = first.elem
    first = first.next
    x
  }

  def elements: Iterator[T] = new Iterator[T] {
    var first = ListBuffer.this.first

    def hasNext: Boolean =
      first != limit

    def next: T = {
      assert(hasNext)
      val x = first.elem
      first = first.next
      x
    }
  }

  def clear { first = limit }

  /** override for efficiency */
  override def toList: List[T] = { 
    def mkList(p: LinkedList[T]): List[T] =
      if (p == limit) List() else p.elem :: mkList(p.next)
    mkList(first)
  }

  override def toString(): String = toList.mkString("", ",", "")
}
