/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: PhantomReference.scala 14497 2008-04-04 12:09:06Z washburn $

package scala.ref

/**
 *  @author Sean McDirmid
 */
class PhantomReference[+T <: AnyRef](value: T, queue: ReferenceQueue[T]) extends ReferenceWrapper[T] {
  val underlying: java.lang.ref.PhantomReference[_ <: T] = 
    new java.lang.ref.PhantomReference[T](value, queue.underlying.asInstanceOf[java.lang.ref.ReferenceQueue[T]])
}
                                      
