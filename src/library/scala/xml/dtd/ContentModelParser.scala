/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ContentModelParser.scala 6904 2006-03-25 14:47:10Z emir $


package scala.xml.dtd;


/** Parser for regexps (content models in DTD element declarations) */

object ContentModelParser extends Scanner { // a bit too permissive concerning #PCDATA
  import ContentModel._ ;

  /** parses the argument to a regexp */
  def parse(s:String): ContentModel = { initScanner( s ); contentspec }

  //                                              zzz   parser methods   zzz
  def accept(tok: Int) = {
    if( token != tok ) {
      if(( tok == STAR )&&( token == END ))                  // common mistake
        error("in DTDs, \n"+
              "mixed content models must be like (#PCDATA|Name|Name|...)*");
      else
        error("expected "+token2string(tok)+
              ", got unexpected token:"+token2string(token));
    }
    nextToken
  }

  // s [ '+' | '*' | '?' ]
  def maybeSuffix(s: RegExp) = token match {	
    case STAR => nextToken; Star( s ) 
    case PLUS => nextToken; Sequ( s, Star( s )) 
    case OPT  => nextToken; Alt( Eps, s )
    case _    => s
  }


  // contentspec ::= EMPTY | ANY | (#PCDATA) | "(#PCDATA|"regexp)

  def contentspec: ContentModel = token match {

    case NAME    => value match {
      case "ANY"   => ANY 
      case "EMPTY" => EMPTY
      case _       => error("expected ANY, EMPTY or '(' instead of " + value );
    }
    case LPAREN => 

      nextToken; 
      sOpt;
      if( token != TOKEN_PCDATA )
        ELEMENTS(regexp);
      else {
        nextToken;
        token match {
        case RPAREN =>
          PCDATA
        case CHOICE =>
          val res = MIXED(choiceRest(Eps));
          sOpt;
          accept( RPAREN );
          accept( STAR );
          res
        case _ =>
          error("unexpected token:" + token2string(token) );
        }
      }

    case _ =>
      error("unexpected token:" + token2string(token) );
    }
  //                                  sopt ::= S?
  def sOpt = if( token == S ) nextToken;

  //                      (' S? mixed ::= '#PCDATA' S? ')'
  //                                    | '#PCDATA' (S? '|' S? atom)* S? ')*'
  /*
  def mixed = {
    accept( TOKEN_PCDATA );
    sOpt;
    if( token == RPAREN ) 
      PCDATA_
    else {
      val t = choiceRest( PCDATA_ );
      if( !isMixed( t ) )
        error("mixed content models must be like (#PCDATA.|.|.|.)*");
      accept( RPAREN );
      // lax: (workaround for buggy Java XML parser in JDK1.4.2)
      if( token == STAR ) accept( STAR ); 
      // strict:
      // accept( STAR );
      Star( t )
    }
  }
*/
  //       '(' S? regexp ::= cp S? [seqRest|choiceRest] ')' [ '+' | '*' | '?' ]
  def regexp:RegExp = {
    //Console.println("regexp, token = "+token2string(token));
    val p = particle;
    sOpt;
    maybeSuffix( token match {
      case RPAREN  => nextToken; p
      case CHOICE  => val q = choiceRest( p );accept( RPAREN ); q
      case COMMA   => val q = seqRest( p );   accept( RPAREN ); q
    })
  }


  //                                             seqRest ::= (',' S? cp S?)+
  def seqRest( p:RegExp ) = { 
    var k = List( p );
    while( token == COMMA ) {
      nextToken;
      sOpt;
      k = particle::k;
      sOpt;
    }
    Sequ( k.reverse:_* )
  }

  //                                          choiceRest ::= ('|' S? cp S?)+
  def choiceRest( p:RegExp ) = { 
    var k = List( p );
    while( token == CHOICE ) {
      nextToken;
      sOpt;
      k = particle::k;
      sOpt;
    }
    Alt( k.reverse:_* )
  }

  //                                  particle ::=  '(' S? regexp
  //                                             |  name [ '+' | '*' | '?' ]
  def particle = {
    //Console.println("particle, token="+token2string(token));
    token match {
    case LPAREN => nextToken; sOpt; regexp; 
    case NAME   => val a = Letter(ElemName(value)); nextToken; maybeSuffix( a )
    case _      => error("expected '(' or Name, got:"+token2string( token ));
    }
  }

  //                                     atom ::= name
  def atom = token match {
    case NAME   => val a = Letter(ElemName(value)); nextToken; a
    case _      => error("expected Name, got:"+token2string( token ));
  }
}
