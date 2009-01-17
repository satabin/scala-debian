/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ImmutableSetAdaptor.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.collection.mutable


/** This class can be used as an adaptor to create mutable sets from
 *  immutable set implementations. Only method <code>empty</code> has
 *  to be redefined if the immutable set on which this mutable set is
 *  originally based is not empty. <code>empty</code> is supposed to
 *  return the representation of an empty set.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 21/07/2003
 */
@serializable
class ImmutableSetAdaptor[A](protected var set: immutable.Set[A]) extends Set[A] {

  def size: Int = set.size

  override def isEmpty: Boolean = set.isEmpty

  def contains(elem: A): Boolean = set.contains(elem)

  override def foreach(f: A => Unit): Unit = set.foreach(f)

  override def exists(p: A => Boolean): Boolean = set.exists(p)

  override def toList: List[A] = set.toList

  override def toString = set.toString

  def elements: Iterator[A] = set.elements

  def +=(elem: A): Unit = { set = set + elem }

  def -=(elem: A): Unit = { set = set - elem }

  override def clear(): Unit = { set = set.empty }

}
