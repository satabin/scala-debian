/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: StreamCons.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.runtime

final class StreamCons[T](xs: => Stream[T]) {
  def lazy_:: (x: T): Stream[T] = Stream.cons(x, xs)
  def lazy_::: (ys: Stream[T]): Stream[T] = ys append xs
}
