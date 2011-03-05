/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2010, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

package scala.collection
package generic

import mutable.{ Builder, AddingBuilder }

abstract class ImmutableSetFactory[CC[X] <: immutable.Set[X] with SetLike[X, CC[X]]]
  extends SetFactory[CC] {
    
  def newBuilder[A]: Builder[A, CC[A]] = new AddingBuilder[A, CC[A]](empty[A])
}
