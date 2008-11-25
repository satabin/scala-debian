/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ImmutableMapAdaptor.scala 10086 2007-02-21 19:10:41Z odersky $


package scala.collection.mutable


/** This class can be used as an adaptor to create mutable maps from
 *  immutable map implementations. Only method <code>empty</code> has
 *  to be redefined if the immutable map on which this mutable map is
 *  originally based is not empty. <code>empty</code> is supposed to
 *  return the representation of an empty map.
 *
 *  @author  Matthias Zenger
 *  @author  Martin Odersky
 *  @version 2.0, 01/01/2007
 */
@serializable
class ImmutableMapAdaptor[A, B](protected var imap: immutable.Map[A, B])
extends Map[A, B]
{

  def size: Int = imap.size

  def get(key: A): Option[B] = imap.get(key)

  override def isEmpty: Boolean = imap.isEmpty

  override def apply(key: A): B = imap.apply(key)

  override def contains(key: A): Boolean = imap.contains(key)

  override def isDefinedAt(key: A) = imap.isDefinedAt(key)

  override def keys: Iterator[A] = imap.keys

  override def keySet: collection.Set[A] = imap.keySet

  override def values: Iterator[B] = imap.values

  def elements: Iterator[(A, B)] = imap.elements

  override def toList: List[(A, B)] = imap.toList

  def update(key: A, value: B): Unit = { imap = imap.update(key, value) }

  def -= (key: A): Unit = { imap = imap - key }

  override def clear(): Unit = { imap = imap.empty }

  override def transform(f: (A, B) => B): Unit = { imap = imap.transform(f) }

  override def retain(p: (A, B) => Boolean): Unit = { 
    imap = imap.filter(xy => p(xy._1, xy._2))
  }

  override def toString() = imap.toString()
}
