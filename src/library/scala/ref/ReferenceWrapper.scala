/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ReferenceWrapper.scala 14497 2008-04-04 12:09:06Z washburn $

package scala.ref

/**
 *  @author Sean McDirmid
 */
trait ReferenceWrapper[+T <: AnyRef] extends Reference[T] with Proxy {
  val underlying: java.lang.ref.Reference[_ <: T]
  @deprecated def isValid = underlying.get != null
  override def get = {
    val ret = underlying.get.asInstanceOf[T]
    if (ret eq null) None else Some(ret)
  }
  def apply() = {
    val ret = underlying.get.asInstanceOf[T]
    if (ret eq null) throw new NoSuchElementException
    ret
  }
  def clear = underlying.clear
  def enqueue = underlying.enqueue
  def isEnqueued = underlying.isEnqueued
  
  def self = underlying
}
