/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: StandardTokenParsers.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.util.parsing.combinator.syntactical

import scala.util.parsing.syntax._
import scala.util.parsing.combinator.lexical.StdLexical 

/** This component provides primitive parsers for the standard tokens defined in `StdTokens'.
*
* @author Martin Odersky, Adriaan Moors
 */
class StandardTokenParsers extends StdTokenParsers {
  type Tokens = StdTokens
  val lexical = new StdLexical
}
