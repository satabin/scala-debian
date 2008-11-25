/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: StdLexical.scala 13911 2008-02-07 12:39:51Z moors $


package scala.util.parsing.combinatorold.lexical

import scala.util.parsing.syntax._
import scala.util.parsing.input.CharArrayReader.EofCh
import collection.mutable.HashSet

/** <p>
 *    This component provides a standard lexical parser for a simple, Scala-like language. 
 *    It parses keywords and identifiers, numeric literals (integers), strings, and delimiters. 
 *  </p>
 *  <p>
 *    To distinguish between identifiers and keywords, it uses a set of reserved identifiers: 
 *    every string contained in `reserved' is returned as a keyword token.
 *    (Note that "=>" is hard-coded as a keyword.) 
 *    Additionally, the kinds of delimiters can be specified by the `delimiters' set.
 *  </p>
 *  <p>
 *    Usually this component is used to break character-based input into bigger tokens,
 *    which are then passed to a token-parser {@see TokenParsers}.
 *  </p>
 *
 * @author Martin Odersky, Iulian Dragos, Adriaan Moors 
 */
class StdLexical extends Lexical with StdTokens {
  
  // override this parser to change the characters allowed at the beginning of an identifier
  def identBegin: Parser[Char] = ('_' ^^ '_') | letter

  // override this parser to change the characters allowed in an identifier (i.e., after the first character)
  def identCont: Parser[Char] = ('_' ^^ '_') | letter | digit
       
  // see `token' in `Scanners'
  def token: Parser[Token] = 
    ( identBegin ~ rep( identCont )                     ^^ lift2(processIdent)
    | digit ~ rep( digit )                              ^^ lift2(NumericLit)
    | '\'' ~ rep( chrExcept('\'', '\n', EofCh) ) ~ '\'' ^^ lift(StringLit)
    | '\"' ~ rep( chrExcept('\"', '\n', EofCh) ) ~ '\"' ^^ lift(StringLit)
    | EofCh                                             ^^ EOF
    | '\'' ~ failure("unclosed string literal")
    | '\"' ~ failure("unclosed string literal")
    | delim                                             
    | failure("illegal character")
    )

  // see `whitespace in `Scanners'
  def whitespace: Parser[Any] = rep(
      whitespaceChar
    | '/' ~ '*' ~ comment
    | '/' ~ '/' ~ rep( chrExcept(EofCh, '\n') )
    | '/' ~ '*' ~ failure("unclosed comment")
    )

  protected def comment: Parser[Any] = (
      '*' ~ '/'  ^^ ' ' 
    | chrExcept(EofCh) ~ comment
    )

  /** The set of reserved identifiers: these will be returned as `Keyword's */
  val reserved = new HashSet[String]

  /** The set of delimiters (ordering does not matter) */
  val delimiters = new HashSet[String]

  protected def processIdent(name: String) = 
    if (reserved contains name) Keyword(name) else Identifier(name)

  private var _delim: Parser[Token] = null
  protected def delim: Parser[Token] = {
    if(_delim eq null) { // construct parser for delimiters by |'ing together the parsers for the individual delimiters, 
    // starting with the longest one (hence the sort + reverse) -- otherwise a delimiter D will never be matched if 
    // there is another delimiter that is a prefix of D   
      def parseDelim(s: String): Parser[Token] = accept(s.toList) ^^ Keyword(s)
      
      val d = new Array[String](delimiters.size)
      delimiters.copyToArray(d,0)
      scala.util.Sorting.quickSort(d) 
      _delim = d.toList.reverse.map(parseDelim).reduceRight[Parser[Token]](_ | _) // no offence :-)      
    }
    
    _delim
  }

  private def lift[T](f: String => T)(xs: List[Char]): T = f(xs.mkString("", "", ""))

  private def lift2[T](f: String => T)(p: ~[Char, List[Char]]): T = lift(f)(p._1 :: p._2)
}
