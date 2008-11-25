/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Index.scala 12905 2007-09-18 09:13:45Z michelou $


package scala.xml.persistent

/** an Index returns some unique key that is part of a node
 */
abstract class Index[A] extends Function1[Node,A] {}
