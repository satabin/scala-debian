/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2007-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RollbackIterator.scala 15313 2008-06-10 09:25:57Z michelou $

package scala.collection

import scala.collection.mutable.{ArrayBuffer}

/** Rollback iterators are buffered iterators which allow for unbounded rollbacks
 *
 *  @author  Sean McDirmid
 */
class RollbackIterator[+A](underlying: Iterator[A]) extends BufferedIterator.Default[A] {
  private[this] var rollback: ArrayBuffer[A] = null
  protected def fill(sz: Int): Seq[A] =
    if (underlying.hasNext) underlying.next :: Nil else Nil

  override def next: A = {
    val ret = super.next
    if (rollback != null) rollback += ret
    ret
  }

  private def initRollback =
    if (rollback == null) {
      rollback = new ArrayBuffer[A]
      None
    }
    else Some(rollback.length)

  /** will rollback all elements iterated during 
   *  <code>f</code>'s execution if <code>f</code> return false 
   */
  def tryRead[T](f: => Option[T]): Option[T] = {
    val oldLength = initRollback
    var g : Option[T] = None
    try {
      g = f
    } finally {
      if (g.isEmpty) {
        //putBack(rollback(0))
        val sz = oldLength.getOrElse(0)
        val i = rollback.drop(sz).reverse.elements
        while (i.hasNext) putBack(i.next)
        if (oldLength.isEmpty) rollback = null          
        else rollback.reduceToSize(sz)
      }
    }
    if (!g.isEmpty && oldLength.isEmpty) 
      rollback = null
    g

  }
  /** remembers elements iterated over during <code>g</code>'s execution
   *  and provides these elements to the result of <code>g</code>'s execution
   */
  def remember[T](g: => (Seq[A] => T)): T = {
    val oldLength = initRollback
    var in: Seq[A] = Nil
    val f = try {
      g
    } finally {
      in = rollback.drop(oldLength.getOrElse(0))
      if (oldLength.isEmpty) rollback = null
    }
    f(in)
  }

  /** returns true if any elements are iterated over during <code>f</code>'s execution 
   */
  def read(f: => Unit): Boolean = remember[Boolean] {
    f; seq => !seq.isEmpty
  }

  /** if elements of <code>seq</code> will be iterated over next in this iterator,
   *  returns true and iterates over these elements
   */
  override def readIfStartsWith(seq : Seq[Any]) : Boolean = 
    !tryRead{if (seq.forall(a => hasNext && next == a)) Some(()) else None}.isEmpty

}
