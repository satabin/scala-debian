/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: JavaTokenParsers.scala 14415 2008-03-19 00:53:09Z mihaylov $

package scala.util.parsing.combinator

trait JavaTokenParsers extends RegexParsers {
  def ident: Parser[String] = 
    """[a-zA-Z_]\w*""".r
  def wholeNumber: Parser[String] = 
    """-?\d+""".r
  def decimalNumber: Parser[String] = 
    """(\d+(\.\d*)?|\d*.\d+)""".r
  def stringLiteral: Parser[String] = 
    ("\""+"""([^"\p{Cntrl}\\]|\\[\\/bfnrt]|\\u[a-fA-F0-9]{4})*"""+"\"").r
  def floatingPointNumber: Parser[String] = 
    """-?(\d+(\.\d*)?|\d*\.\d+)[eEfFdD]?([+-]?\d+)?""".r
}
