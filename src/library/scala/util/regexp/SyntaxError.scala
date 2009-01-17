/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SyntaxError.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.util.regexp

/** This runtime exception is thrown if an attempt to instantiate a
 *  syntactically incorrect expression is detected.
 *
 *  @author  Burak Emir
 *  @version 1.0
 */
class SyntaxError(e: String) extends RuntimeException(e)
