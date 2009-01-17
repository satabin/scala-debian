/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ScalaObject.scala 16894 2009-01-13 13:09:41Z cunei $


package scala

import Predef._

trait ScalaObject extends AnyRef {

  /** This method is needed for optimizing pattern matching expressions
   *  which match on constructors of case classes.
   */
  @remote
  def $tag(): Int = 0

}
