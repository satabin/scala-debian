/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Index.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.xml.persistent

/** an Index returns some unique key that is part of a node
 */
abstract class Index[A] extends Function1[Node,A] {}
