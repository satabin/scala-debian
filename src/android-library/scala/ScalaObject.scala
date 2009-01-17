/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ScalaObject.scala 16881 2009-01-09 16:28:11Z cunei $


package scala

import Predef._

trait ScalaObject extends AnyRef {

  /** This method is needed for optimizing pattern matching expressions
   *  which match on constructors of case classes.
   */
  def $tag(): Int = 0

}
