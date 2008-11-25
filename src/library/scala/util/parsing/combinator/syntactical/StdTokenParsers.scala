/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: StdTokenParsers.scala 14415 2008-03-19 00:53:09Z mihaylov $


package scala.util.parsing.combinator.syntactical

import scala.util.parsing.syntax._

/** This component provides primitive parsers for the standard tokens defined in `StdTokens'.
*
* @author Martin Odersky, Adriaan Moors
 */
trait StdTokenParsers extends TokenParsers {
  type Tokens <: StdTokens
  import lexical.{Keyword, NumericLit, StringLit, Identifier}

  /** A parser which matches a single keyword token.
   *
   * @param chars    The character string making up the matched keyword. 
   * @return a `Parser' that matches the given string
   */
  implicit def keyword(chars: String): Parser[String] = accept(Keyword(chars)) ^^ (_.chars)

  /** A parser which matches a numeric literal */
  def numericLit: Parser[String] = 
    elem("number", _.isInstanceOf[NumericLit]) ^^ (_.chars)

  /** A parser which matches a string literal */
  def stringLit: Parser[String] = 
    elem("string literal", _.isInstanceOf[StringLit]) ^^ (_.chars)

  /** A parser which matches an identifier */
  def ident: Parser[String] = 
    elem("identifier", _.isInstanceOf[Identifier]) ^^ (_.chars)
}


