/* NSC -- new Scala compiler
 * Copyright 2005-2010 LAMP/EPFL
 * @author Paul Phillips
 */
 
package scala.tools.nsc
package interpreter

import jline.ArgumentCompletor.{ ArgumentDelimiter, ArgumentList }

class JLineDelimiter extends ArgumentDelimiter {
  def delimit(buffer: String, cursor: Int) = Parsed(buffer, cursor).asJlineArgumentList
  def isDelimiter(buffer: String, cursor: Int) = Parsed(buffer, cursor).isDelimiter
}

trait Delimited {
  self: Parsed =>
  
  def delimited: Char => Boolean
  def escapeChars: List[Char] = List('\\')
  def quoteChars: List[(Char, Char)] = List(('\'', '\''), ('"', '"'))
  
  /** Break String into args based on delimiting function.
   */
  protected def toArgs(s: String): List[String] = 
    if (s == "") Nil
    else (s indexWhere isDelimiterChar) match {
      case -1   => List(s)
      case idx  => (s take idx) :: toArgs(s drop (idx + 1))
    }
  
  def isDelimiterChar(ch: Char) = delimited(ch)
  def isEscapeChar(ch: Char): Boolean = escapeChars contains ch
  def isQuoteStart(ch: Char): Boolean = quoteChars map (_._1) contains ch
  def isQuoteEnd(ch: Char): Boolean = quoteChars map (_._2) contains ch
}
