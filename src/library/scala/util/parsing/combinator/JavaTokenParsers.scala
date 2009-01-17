/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: JavaTokenParsers.scala 16894 2009-01-13 13:09:41Z cunei $

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
    """-?(\d+(\.\d*)?|\d*\.\d+)([eE][+-]?\d+)?[fFdD]?""".r
}
