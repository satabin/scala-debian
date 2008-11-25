/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ScalaObject.scala 14498 2008-04-04 12:12:27Z washburn $


package scala

import Predef._

trait ScalaObject extends AnyRef {

  /** This method is needed for optimizing pattern matching expressions
   *  which match on constructors of case classes.
   */
  def $tag(): Int = 0

}
