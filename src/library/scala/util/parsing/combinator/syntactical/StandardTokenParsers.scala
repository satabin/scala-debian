/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: StandardTokenParsers.scala 14415 2008-03-19 00:53:09Z mihaylov $


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
