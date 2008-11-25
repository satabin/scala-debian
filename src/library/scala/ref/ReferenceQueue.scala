/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ReferenceQueue.scala 14497 2008-04-04 12:09:06Z washburn $

package scala.ref

/**
 *  @author Sean McDirmid
 */
class ReferenceQueue[+T <: AnyRef] {
  private[ref] val underlying: java.lang.ref.ReferenceQueue[_ <: T] = new java.lang.ref.ReferenceQueue[T]
  override def toString = underlying.toString
  protected class Wrapper[U <: AnyRef](val underlying: java.lang.ref.Reference[U]) extends ReferenceWrapper[U]
  protected def Wrapper[U <: AnyRef](ref: java.lang.ref.Reference[U]) = ref match {
    case null => None
    case ref => Some(new Wrapper(ref))
  }
  def poll: Option[Reference[T]] = Wrapper(underlying.poll)
  def remove: Option[Reference[T]] = Wrapper(underlying.remove)
  def remove(timeout: Long): Option[Reference[T]] = Wrapper(underlying.remove(timeout))
}
