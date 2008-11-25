/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: DefaultEntry.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.collection.mutable

import Predef._

@serializable
final class DefaultEntry[A, B](val key: A, var value: B) 
      extends HashEntry[A, DefaultEntry[A, B]]
