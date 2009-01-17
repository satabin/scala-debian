/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SynchronizedSet.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.collection.mutable


/** This class should be used as a mixin. It synchronizes the <code>Set</code>
 *  functions of the class into which it is mixed in.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 08/07/2003
 */
trait SynchronizedSet[A] extends Set[A] {

  abstract override def size: Int = synchronized {
    super.size
  }

  override def isEmpty: Boolean = synchronized {
    super.isEmpty
  }

  abstract override def contains(elem: A) = synchronized {
    super.contains(elem)
  }

  abstract override def update(elem: A, included: Boolean): Unit = synchronized {
    super.update(elem, included)
  }

  abstract override def +=(elem: A): Unit = synchronized {
    super.+=(elem)
  }

  override def ++=(that: Iterable[A]) = synchronized {
    super.++=(that)
  }

  override def ++=(it: Iterator[A]) = synchronized {
    super.++=(it)
  }

  override def incl(elems: A*): Unit = synchronized {
    super.++=(elems)
  }

  abstract override def -=(elem: A): Unit = synchronized {
    super.-=(elem)
  }

  override def --=(that: Iterable[A]) = synchronized {
    super.--=(that)
  }

  override def --=(it: Iterator[A]) = synchronized {
    super.--=(it)
  }

  override def excl(elems: A*): Unit = synchronized {
    super.--=(elems)
  }

  override def intersect(that: Set[A]) = synchronized {
    super.intersect(that)
  }

  abstract override def clear(): Unit = synchronized {
    super.clear
  }

  override def subsetOf(that: scala.collection.Set[A]) = synchronized {
    super.subsetOf(that)
  }

  override def foreach(f: A => Unit) = synchronized {
    super.foreach(f)
  }

  override def retain(p: A => Boolean) = synchronized {
    super.retain(p)
  }

  override def toList: List[A] = synchronized {
    super.toList
  }

  override def toString = synchronized {
    super.toString
  }

  override def <<(cmd: Message[A]): Unit = synchronized {
    super.<<(cmd)
  }

  override def clone(): Set[A] = synchronized {
    super.clone()
  }
}
