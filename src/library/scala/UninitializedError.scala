/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: UninitializedError.scala 11038 2007-05-15 10:51:15Z mihaylov $


package scala

import Predef._

/** This class represents uninitialized variable/value errors.
 *
 *  @author  Martin Odersky
 */
final class UninitializedError extends RuntimeException("uninitialized value")
