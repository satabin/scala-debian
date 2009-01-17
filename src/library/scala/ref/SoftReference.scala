/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SoftReference.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.ref

/**
 *  @author Sean McDirmid
 */
class SoftReference[+T <: AnyRef](value : T, queue : ReferenceQueue[T]) extends ReferenceWrapper[T] {
  def this(value : T) = this(value, null);
  val underlying: java.lang.ref.SoftReference[_ <: T] = 
    if (queue == null) new java.lang.ref.SoftReference[T](value);
    else new java.lang.ref.SoftReference[T](value, queue.underlying.asInstanceOf[java.lang.ref.ReferenceQueue[T]])
}
