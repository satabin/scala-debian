/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2010, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */



package scala.collection
package mutable



/** Class used internally for default map model.
 *  @since 2.3
 */
@serializable
final class DefaultEntry[A, B](val key: A, var value: B) 
      extends HashEntry[A, DefaultEntry[A, B]]
