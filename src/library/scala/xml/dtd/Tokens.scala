/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Tokens.scala 6904 2006-03-25 14:47:10Z emir $


package scala.xml.dtd;


class Tokens {

  // Tokens

  final val TOKEN_PCDATA = 0
  final val NAME         = 1
  final val LPAREN       = 3
  final val RPAREN       = 4
  final val COMMA        = 5
  final val STAR         = 6
  final val PLUS         = 7
  final val OPT          = 8
  final val CHOICE       = 9
  final val END          = 10
  final val S            = 13
  
  final def token2string(i: Int): String = i match {
    case 0 => "#PCDATA"
    case 1 => "NAME"
    case 3 => "("
    case 4 => ")"
    case 5 => ","
    case 6 => "*"
    case 7 => "+"
    case 8 => "?"
    case 9 => "|"
    case 10 => "END"
    case 13 => " "
  }
}