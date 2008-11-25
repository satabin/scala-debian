/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SyntaxError.scala 10648 2007-04-10 08:40:09Z michelou $


package scala.util.regexp

/** This runtime exception is thrown if an attempt to instantiate a
 *  syntactically incorrect expression is detected.
 *
 *  @author  Burak Emir
 *  @version 1.0
 */
class SyntaxError(e: String) extends RuntimeException(e)
