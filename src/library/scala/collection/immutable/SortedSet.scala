/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SortedSet.scala 13813 2008-01-27 13:11:26Z mcdirmid $

package scala.collection.immutable

trait SortedSet[A] extends scala.collection.SortedSet[A] with Set[A] {
  override def ++ (elems: Iterable[A]): SortedSet[A] = 
    (this /: elems) ((s, elem) => s + elem)
  override def +(elem: A): SortedSet[A]
}
