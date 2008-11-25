/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: MarkupParser.scala 14560 2008-04-09 09:57:07Z emir $


package scala.xml.parsing

import scala.io.Source
import scala.xml.dtd._

/**
 * An XML parser.
 *
 * Parses XML 1.0, invokes callback methods of a MarkupHandler
 * and returns whatever the markup handler returns. Use
 * <code>ConstructingParser</code> if you just want to parse XML to
 * construct instances of <code>scala.xml.Node</code>.
 *
 * While XML elements are returned, DTD declarations - if handled - are 
 * collected using side-effects.
 *
 * @author  Burak Emir
 * @version 1.0
 */
trait MarkupParser extends AnyRef with TokenTests { self:  MarkupParser with MarkupHandler =>

  val input: Source

  /** if true, does not remove surplus whitespace */
  val preserveWS: Boolean

  def externalSource(systemLiteral: String): Source

  //
  // variables, values
  //

  var curInput: Source = input

  /** the handler of the markup, returns this */
  private val handle: MarkupHandler = this

  /** stack of inputs */
  var inpStack: List[Source] = Nil

  /** holds the position in the source file */
  var pos: Int = _


  /* used when reading external subset */
  var extIndex = -1

  /** holds temporary values of pos */
  var tmppos: Int = _

  /** holds the next character */
  var ch: Char = _

  /** character buffer, for names */
  protected val cbuf = new StringBuilder()

  var dtd: DTD = null

  protected var doc: Document = null

  var eof: Boolean = false

  //
  // methods
  //

  /** &lt;? prolog ::= xml S ... ?&gt;
   */
  def xmlProcInstr(): MetaData = {
    xToken("xml")
    xSpace
    val (md,scp) = xAttributes(TopScope)
    if (scp != TopScope)
      reportSyntaxError("no xmlns definitions here, please.");
    xToken('?')
    xToken('>')
    md
  }

  /** &lt;? prolog ::= xml S?
   *  // this is a bit more lenient than necessary...
   */
  def prolog(): Tuple3[Option[String], Option[String], Option[Boolean]] = {

    //Console.println("(DEBUG) prolog")
    var n = 0
    var info_ver: Option[String] = None
    var info_enc: Option[String] = None
    var info_stdl: Option[Boolean] = None

    var m = xmlProcInstr()

    xSpaceOpt

    m("version") match {
      case null  => ;
      case Text("1.0") => info_ver = Some("1.0"); n += 1
      case _     => reportSyntaxError("cannot deal with versions != 1.0")
    }

    m("encoding") match {
      case null => ;
      case Text(enc) =>
        if (!isValidIANAEncoding(enc))
          reportSyntaxError("\"" + enc + "\" is not a valid encoding")
        else {
          info_enc = Some(enc)
          n += 1
        }
    }
    m("standalone") match {
      case null => ;
      case Text("yes") => info_stdl = Some(true);  n += 1
      case Text("no")  => info_stdl = Some(false); n += 1
      case _     => reportSyntaxError("either 'yes' or 'no' expected")
    }

    if (m.length - n != 0) {
      reportSyntaxError("VersionInfo EncodingDecl? SDDecl? or '?>' expected!");
    }
    //Console.println("[MarkupParser::prolog] finished parsing prolog!");
    Tuple3(info_ver,info_enc,info_stdl)
  }

  /** prolog, but without standalone */
  def textDecl(): Tuple2[Option[String],Option[String]] = {

    var info_ver: Option[String] = None
    var info_enc: Option[String] = None

    var m = xmlProcInstr()
    var n = 0

    m("version") match {
      case null => ;
      case Text("1.0") => info_ver = Some("1.0"); n += 1
      case _     => reportSyntaxError("cannot deal with versions != 1.0")
    }

    m("encoding") match {
      case null => ;
      case Text(enc)  =>
        if (!isValidIANAEncoding(enc))
          reportSyntaxError("\"" + enc + "\" is not a valid encoding")
        else {
          info_enc = Some(enc)
          n += 1
        }
    }

    if (m.length - n != 0) {
      reportSyntaxError("VersionInfo EncodingDecl? or '?>' expected!");
    }
    //Console.println("[MarkupParser::textDecl] finished parsing textdecl");
    Tuple2(info_ver, info_enc);
  }

  /**
   *[22]        prolog     ::=          XMLDecl? Misc* (doctypedecl Misc*)?
   *[23]        XMLDecl    ::=          '&lt;?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'
   *[24]        VersionInfo        ::=          S 'version' Eq ("'" VersionNum "'" | '"' VersionNum '"')
   *[25]        Eq         ::=          S? '=' S?
   *[26]        VersionNum         ::=          '1.0'
   *[27]        Misc       ::=          Comment | PI | S
   */

  def document(): Document = {

    //Console.println("(DEBUG) document")
    doc = new Document()

    this.dtd = null
    var info_prolog: Tuple3[Option[String], Option[String], Option[Boolean]] = Tuple3(None, None, None);
    if ('<' != ch) {
      reportSyntaxError("< expected")
      return null
    }

    nextch // is prolog ?
    var children: NodeSeq = null
    if ('?' == ch) {
      //Console.println("[MarkupParser::document] starts with xml declaration");
      nextch;
      info_prolog = prolog()
      doc.version    = info_prolog._1
      doc.encoding   = info_prolog._2
      doc.standAlone = info_prolog._3

      children = content(TopScope) // DTD handled as side effect
    } else {
      //Console.println("[MarkupParser::document] does not start with xml declaration");
 //

      val ts = new NodeBuffer();
      content1(TopScope, ts); // DTD handled as side effect
      ts &+ content(TopScope);
      children = NodeSeq.fromSeq(ts);
    }
    //Console.println("[MarkupParser::document] children now: "+children.toList);
    var elemCount = 0;
    var theNode: Node = null;
    for (c <- children) c match {
      case _:ProcInstr => ;
      case _:Comment => ;
      case _:EntityRef => // todo: fix entities, shouldn't be "special"
        reportSyntaxError("no entity references alllowed here");
      case s:SpecialNode =>
        if (s.toString().trim().length > 0) //non-empty text nodes not allowed
          elemCount = elemCount + 2;
      case m:Node =>
        elemCount = elemCount + 1;
        theNode = m;
    }
    if (1 != elemCount) {
      reportSyntaxError("document must contain exactly one element")
      Console.println(children.toList)
    }

    doc.children = children
    doc.docElem = theNode
    doc
  }

  /** append Unicode character to name buffer*/
  protected def putChar(c: Char) = cbuf.append(c)

  //var xEmbeddedBlock = false;

  /** this method assign the next character to ch and advances in input */
  def nextch {
    if (curInput.hasNext) {
      ch = curInput.next
      pos = curInput.pos
    } else {
      val ilen = inpStack.length;
      //Console.println("  ilen = "+ilen+ " extIndex = "+extIndex);
      if ((ilen != extIndex) && (ilen > 0)) { 
        /** for external source, inpStack == Nil ! need notify of eof! */
        pop()
      } else {
        eof = true
        ch = 0.asInstanceOf[Char]
      }
    }
  }

  //final val enableEmbeddedExpressions: Boolean = false;

  /** munch expected XML token, report syntax error for unexpected
  */
  def xToken(that: Char) {
    if (ch == that)
      nextch
    else  {
      reportSyntaxError("'" + that + "' expected instead of '" + ch + "'")
      error("FATAL")
    }
  }

  def xToken(that: Seq[Char]): Unit = {
    val it = that.elements;
    while (it.hasNext)
      xToken(it.next);
  }

  /** parse attribute and create namespace scope, metadata
   *  [41] Attributes    ::= { S Name Eq AttValue }
   */
  def xAttributes(pscope:NamespaceBinding): (MetaData,NamespaceBinding) = {
    var scope: NamespaceBinding = pscope
    var aMap: MetaData = Null
    while (isNameStart(ch)) {
      val pos = this.pos

      val qname = xName
      val _     = xEQ
      val value = xAttributeValue()

      Utility.prefix(qname) match {
        case Some("xmlns") =>
          val prefix = qname.substring(6 /*xmlns:*/ , qname.length);
          scope = new NamespaceBinding(prefix, value, scope);
        
        case Some(prefix)       => 
          val key = qname.substring(prefix.length+1, qname.length);
          aMap = new PrefixedAttribute(prefix, key, Text(value), aMap);

        case _             => 
          if( qname == "xmlns" ) 
            scope = new NamespaceBinding(null, value, scope);
          else 
            aMap = new UnprefixedAttribute(qname, Text(value), aMap);
      }
            
      if ((ch != '/') && (ch != '>') && ('?' != ch))
        xSpace; 
    }

    if(!aMap.wellformed(scope))
        reportSyntaxError( "double attribute");

    (aMap,scope)
  }

  /** attribute value, terminated by either ' or ". value may not contain &lt;.
   *       AttValue     ::= `'` { _  } `'`
   *                      | `"` { _ } `"`
   */
  def xAttributeValue(): String = {
    val endch = ch
    nextch
    while (ch != endch) {
      if ('<' == ch)
        reportSyntaxError( "'<' not allowed in attrib value" );
      putChar(ch)
      nextch
    }
    nextch
    val str = cbuf.toString()
    cbuf.length = 0

    // well-formedness constraint
    normalizeAttributeValue(str)
  }

  /** entity value, terminated by either ' or ". value may not contain &lt;.
   *       AttValue     ::= `'` { _  } `'`
   *                      | `"` { _ } `"`
   */
  def xEntityValue(): String = {
    val endch = ch
    nextch
    while (ch != endch) {
      putChar(ch)
      nextch
    }
    nextch
    val str = cbuf.toString()
    cbuf.length = 0
    str
  }


  /** parse a start or empty tag.
   *  [40] STag         ::= '&lt;' Name { S Attribute } [S] 
   *  [44] EmptyElemTag ::= '&lt;' Name { S Attribute } [S] 
   */
  protected def xTag(pscope:NamespaceBinding): Tuple3[String, MetaData, NamespaceBinding] = {
    val qname = xName

    xSpaceOpt
    val (aMap: MetaData, scope: NamespaceBinding) = {
      if (isNameStart(ch)) 
        xAttributes(pscope)
      else 
        (Null, pscope)
    }
    (qname, aMap, scope)
  }

  /** [42]  '&lt;' xmlEndTag ::=  '&lt;' '/' Name S? '&gt;'
   */
  def xEndTag(n: String) = {
    xToken('/')
    val m = xName
    if (n != m)
      reportSyntaxError("expected closing tag of " + n/* +", not "+m*/);
    xSpaceOpt
    xToken('>')
  }

  /** '&lt;! CharData ::= [CDATA[ ( {char} - {char}"]]&gt;"{char} ) ']]&gt;'
   *
   * see [15]
   */
  def xCharData: NodeSeq = {
    xToken("[CDATA[")
    val pos1 = pos
    val sb: StringBuilder = new StringBuilder()
    while (true) {
      if (ch==']'  &&
         { sb.append(ch); nextch; ch == ']' } &&
         { sb.append(ch); nextch; ch == '>' } ) {
        sb.length = sb.length - 2
        nextch; 
        return handle.text( pos1, sb.toString() );
      } else sb.append( ch );
      nextch; 
    }
    throw FatalError("this cannot happen");
  };

  /** CharRef ::= "&amp;#" '0'..'9' {'0'..'9'} ";"
   *            | "&amp;#x" '0'..'9'|'A'..'F'|'a'..'f' { hexdigit } ";"
   *
   * see [66]
   */
  def xCharRef(ch: () => Char, nextch: () => Unit): String = {
    Utility.parseCharRef(ch, nextch, reportSyntaxError _)
    /*
    val hex  = (ch() == 'x') && { nextch(); true };
    val base = if (hex) 16 else 10;
    var i = 0;
    while (ch() != ';') {
      ch() match {
        case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' =>
          i = i * base + Character.digit( ch(), base );
        case 'a' | 'b' | 'c' | 'd' | 'e' | 'f'
           | 'A' | 'B' | 'C' | 'D' | 'E' | 'F' =>
          if (! hex) 
            reportSyntaxError("hex char not allowed in decimal char ref\n"
                         +"Did you mean to write &#x ?");
          else 
            i = i * base + Character.digit(ch(), base);
        case _ =>
          reportSyntaxError("character '" + ch() + " not allowed in char ref\n");
      }
      nextch();
    }
    new String(Array(i.asInstanceOf[char]))
    */
  }


  /** Comment ::= '&lt;!--' ((Char - '-') | ('-' (Char - '-')))* '--&gt;'
   *
   * see [15]
   */
  def xComment: NodeSeq = {
    val sb: StringBuilder = new StringBuilder()
    xToken('-')
    xToken('-')
    while (true) { 
      if (ch == '-'  && { sb.append(ch); nextch; ch == '-' }) {
        sb.length = sb.length - 1
        nextch
        xToken('>')
        return handle.comment(pos, sb.toString())
      } else sb.append(ch)
      nextch
    }
    throw FatalError("this cannot happen")
  }

  /* todo: move this into the NodeBuilder class */
  def appendText(pos: Int, ts: NodeBuffer, txt: String): Unit = {
    if (preserveWS)
      ts &+ handle.text(pos, txt);
    else
      for (t <- TextBuffer.fromString(txt).toText) {
        ts &+ handle.text(pos, t.text);
      }
  }

  /** '&lt;' content1 ::=  ... */
  def content1(pscope: NamespaceBinding, ts: NodeBuffer): Unit =
    ch match {
      case '!' =>
        nextch
      if ('[' == ch)                 // CDATA 
        ts &+ xCharData
      else if ('D' == ch) // doctypedecl, parse DTD // @todo REMOVE HACK
        parseDTD()
      else // comment
        ts &+ xComment
      case '?' =>                    // PI
        nextch
        ts &+ xProcInstr
      case _   => 
        ts &+ element1(pscope)      // child
    }

  /** content1 ::=  '&lt;' content1 | '&amp;' charref ... */
  def content(pscope: NamespaceBinding): NodeSeq = {
    var ts = new NodeBuffer
    var exit = eof
    while (! exit) {
      //Console.println("in content, ch = '"+ch+"' line="+scala.io.Position.line(pos));
      /*      if( xEmbeddedBlock ) {
       ts.append( xEmbeddedExpr );
       } else {*/
        tmppos = pos;
        exit = eof;
        if(!eof) 
          ch match {
          case '<' => // another tag
            //Console.println("before ch = '"+ch+"' line="+scala.io.Position.line(pos)+" pos="+pos);
            nextch; 
            //Console.println("after ch = '"+ch+"' line="+scala.io.Position.line(pos)+" pos="+pos);

            if('/' ==ch)
              exit = true;                    // end tag
            else
              content1(pscope, ts)
          //case '{' => 
/*            if( xCheckEmbeddedBlock ) {
              ts.appendAll(xEmbeddedExpr);
            } else {*/
          //    val str = new StringBuilder("{");
          //    str.append(xText);
          //    appendText(tmppos, ts, str.toString());
            /*}*/
          // postcond: xEmbeddedBlock == false!
          case '&' => // EntityRef or CharRef 
            nextch;
            ch match {
              case '#' => // CharacterRef
                nextch;
                val theChar = handle.text( tmppos, 
                                          xCharRef ({ ()=> ch },{ () => nextch }) );
                xToken(';');
                ts &+ theChar ;
              case _ => // EntityRef
                val n = xName
                xToken(';')
                n match {
                  case "lt"    => ts &+ '<'
                  case "gt"    => ts &+ '>'
                  case "amp"   => ts &+ '&'
                  case "quot" => ts &+ '"'
                  case _ =>
                    /*
                     ts + handle.entityRef( tmppos, n ) ;
                     */
                    push(n)
                }
            }
          case _ => // text content
            //Console.println("text content?? pos = "+pos);
            appendText(tmppos, ts, xText);
          // here xEmbeddedBlock might be true
          }
    /*}*/
    }
    val list = ts.toList
    // 2do: optimize seq repr.
    new NodeSeq {
      val theSeq = list
    }
  } // content(NamespaceBinding)

  /** externalID ::= SYSTEM S syslit
   *                 PUBLIC S pubid S syslit
   */

  def externalID(): ExternalID = ch match {
    case 'S' =>
      nextch
      xToken("YSTEM")
      xSpace
      val sysID = systemLiteral()
      new SystemID(sysID)
    case 'P' =>
      nextch; xToken("UBLIC")
      xSpace
      val pubID = pubidLiteral()
      xSpace
      val sysID = systemLiteral()
      new PublicID(pubID, sysID)
  }


  /** parses document type declaration and assigns it to instance variable
   *  dtd.
   *
   *  &lt;! parseDTD ::= DOCTYPE name ... >
   */ 
  def parseDTD(): Unit = { // dirty but fast
    //Console.println("(DEBUG) parseDTD");
    var extID: ExternalID = null
    if (this.dtd ne null)
      reportSyntaxError("unexpected character (DOCTYPE already defined");
    xToken("DOCTYPE")
    xSpace
    val n = xName
    xSpace
    //external ID
    if ('S' == ch || 'P' == ch) {
      extID = externalID()
      xSpaceOpt
    }

    /* parse external subset of DTD 
     */

    if ((null != extID) && isValidating) {

      pushExternal(extID.systemId)
      //val extSubsetSrc = externalSource( extID.systemId );

      extIndex = inpStack.length
      /*
       .indexOf(':') != -1) { // assume URI
         Source.fromFile(new java.net.URI(extID.systemLiteral));
       } else {
         Source.fromFile(extID.systemLiteral);
       }
      */
      //Console.println("I'll print it now");
      //val old = curInput;
      //tmppos = curInput.pos;
      //val oldch = ch;
      //curInput = extSubsetSrc;
      //pos = 0;
      //nextch;

      extSubset()

      pop()

      extIndex = -1

      //curInput = old;
      //pos = curInput.pos;
      //ch = curInput.ch;
      //eof = false;
      //while(extSubsetSrc.hasNext)
      //Console.print(extSubsetSrc.next);

      //Console.println("returned from external, current ch = "+ch )
    }

    if ('[' == ch) { // internal subset
      nextch
      /* TODO */
      //Console.println("hello");
      intSubset()
      //while(']' != ch)
      //  nextch;
      // TODO: do the DTD parsing?? ?!?!?!?!!
      xToken(']')
      xSpaceOpt
    }
    xToken('>')
    this.dtd = new DTD {
      /*override var*/ externalID = extID
      /*override val */decls      = handle.decls.reverse
    }
    //this.dtd.initializeEntities();
    if (doc ne null)
      doc.dtd = this.dtd

    handle.endDTD(n)
  }

  def element(pscope: NamespaceBinding): NodeSeq = {
    xToken('<')
    element1(pscope)
  }

  /** '&lt;' element ::= xmlTag1 '&gt;'  { xmlExpr | '{' simpleExpr '}' } ETag
   *               | xmlTag1 '/' '&gt;'
   */
  def element1(pscope: NamespaceBinding): NodeSeq = {
    val pos = this.pos
    val Tuple3(qname, aMap, scope) = xTag(pscope)
    val Tuple2(pre, local) = Utility.prefix(qname) match {
      case Some(p) => (p,qname.substring(p.length+1, qname.length))
      case _       => (null,qname)
    }
    val ts = {
      if (ch == '/') {  // empty element
        xToken('/')
        xToken('>')
        handle.elemStart(pos, pre, local, aMap, scope)
        NodeSeq.Empty
      }
      else {           // element with content
        xToken('>')
        handle.elemStart(pos, pre, local, aMap, scope)
        val tmp = content(scope)
        xEndTag(qname)
        tmp
      }
    }
    val res = handle.elem(pos, pre, local, aMap, scope, ts)
    handle.elemEnd(pos, pre, local)
    res
  }

  //def xEmbeddedExpr: MarkupType;

  /** Name ::= (Letter | '_' | ':') (NameChar)*
   *
   *  see  [5] of XML 1.0 specification
   */
  def xName: String = {
    if (isNameStart(ch)) {
      while (isNameChar(ch)) {
        putChar(ch)
        nextch
      }
      val n = cbuf.toString().intern()
      cbuf.length = 0
      n
    } else {
      reportSyntaxError("name expected")
      ""
    }
  }

  /** scan [S] '=' [S]*/
  def xEQ = { xSpaceOpt; xToken('='); xSpaceOpt }

  /** skip optional space S? */
  def xSpaceOpt = while (isSpace(ch) && !eof) { nextch; }

  /** scan [3] S ::= (#x20 | #x9 | #xD | #xA)+ */
  def xSpace =
    if (isSpace(ch)) { nextch; xSpaceOpt }
    else reportSyntaxError("whitespace expected")

  /** '&lt;?' ProcInstr ::= Name [S ({Char} - ({Char}'&gt;?' {Char})]'?&gt;'
   *
   * see [15]
   */
  def xProcInstr: NodeSeq = {
    val sb:StringBuilder = new StringBuilder()
    val n = xName
    if (isSpace(ch)) {
      xSpace
      while (true) {
        if (ch == '?' && { sb.append( ch ); nextch; ch == '>' }) {
          sb.length = sb.length - 1;
          nextch;
          return handle.procInstr(tmppos, n, sb.toString);
        } else
          sb.append(ch);
        nextch
      }
    };
    xToken('?')
    xToken('>')
    handle.procInstr(tmppos, n, sb.toString)
  }

  /** parse character data.
   *   precondition: xEmbeddedBlock == false (we are not in a scala block)
   */
  def xText: String = {
    //if( xEmbeddedBlock ) throw FatalError("internal error: encountered embedded block"); // assert

    /*if( xCheckEmbeddedBlock )
      return ""
    else {*/
    //Console.println("in xText! ch = '"+ch+"'");
      var exit = false;
      while (! exit) {
        //Console.println("LOOP in xText! ch = '"+ch+"' + pos="+pos);
        putChar(ch);
        val opos = pos;
        nextch;

        //Console.println("STILL LOOP in xText! ch = '"+ch+"' + pos="+pos+" opos="+opos);
        

        exit = eof || /*{ nextch; xCheckEmbeddedBlock }||*/( ch == '<' ) || ( ch == '&' );
      }
      val str = cbuf.toString();
      cbuf.length = 0;
      str
    /*}*/
  }

  /** attribute value, terminated by either ' or ". value may not contain &lt;.
   *       AttValue     ::= `'` { _ } `'`
   *                      | `"` { _ } `"`
   */
  def systemLiteral(): String = {
    val endch = ch
    if (ch != '\'' && ch != '"')
      reportSyntaxError("quote ' or \" expected");
    nextch
    while (ch != endch) {
      putChar(ch)
      nextch
    }
    nextch
    val str = cbuf.toString()
    cbuf.length = 0
    str
  }


  /* [12]       PubidLiteral ::=        '"' PubidChar* '"' | "'" (PubidChar - "'")* "'" */
  def pubidLiteral(): String = {
    val endch = ch
    if (ch!='\'' && ch != '"')
      reportSyntaxError("quote ' or \" expected");
    nextch
    while (ch != endch) {
      putChar(ch)
      //Console.println("hello '"+ch+"'"+isPubIDChar(ch));
      if (!isPubIDChar(ch))
        reportSyntaxError("char '"+ch+"' is not allowed in public id");
      nextch
    }
    nextch
    val str = cbuf.toString()
    cbuf.length = 0
    str
  }

  //
  //  dtd parsing
  //

  def extSubset(): Unit = {
    var textdecl:Tuple2[Option[String],Option[String]] = null;
    if (ch=='<') {
      nextch
      if (ch=='?') {
        nextch
        textdecl = textDecl()
      } else
        markupDecl1()
    }
    while (!eof)
      markupDecl()
  }

  def markupDecl1() = {
    def doInclude() = {
      xToken('['); while(']' != ch) markupDecl(); nextch // ']'
    }
    def doIgnore() = {
      xToken('['); while(']' != ch) nextch; nextch; // ']'
    }
    if ('?' == ch) {
      nextch
      xProcInstr // simply ignore processing instructions!
    } else {
      xToken('!')
      ch match {
        case '-' =>
          xComment // ignore comments

        case 'E' =>
          nextch
          if ('L' == ch) {
            nextch
            elementDecl()
          } else 
            entityDecl()

        case 'A' =>
          nextch
          attrDecl()

        case 'N' =>
          nextch
          notationDecl()

        case '[' if inpStack.length >= extIndex =>
          nextch
          xSpaceOpt
          ch match {
            case '%' =>
              nextch
              val ent = xName
              xToken(';')
              xSpaceOpt
            /*
              Console.println("hello, pushing!");
            {
              val test =  replacementText(ent);
              while(test.hasNext)
                Console.print(test.next);
            } */
              push(ent)
              xSpaceOpt
              //Console.println("hello, getting name");
              val stmt = xName
              //Console.println("hello, got name");
              xSpaceOpt
            //Console.println("how can we be eof = "+eof);

            // eof = true because not external?!
              //if(!eof)
              //  error("expected only INCLUDE or IGNORE");

              //pop();

              //Console.println("hello, popped");
              stmt match {
                // parameter entity
                case "INCLUDE" =>
                  doInclude()
                case "IGNORE" =>
                  doIgnore()
              }
            case 'I' =>
              nextch
              ch match {
                case 'G' =>
                  nextch
                  xToken("NORE")
                  xSpaceOpt
                  doIgnore()
                case 'N' =>
                  nextch
                  xToken("NCLUDE")
                  doInclude()
              }
          }
        xToken(']')
        xToken('>')

        case _  =>
          curInput.reportError(pos, "unexpected character '"+ch+"', expected some markupdecl")
        while (ch!='>')
          nextch
      }
    }
  }

  def markupDecl(): Unit = ch match {
    case '%' =>                  // parameter entity reference
      nextch
      val ent = xName
      xToken(';')
      if (!isValidating)
        handle.peReference(ent)  //  n-v: just create PE-reference 
      else
        push(ent)                //    v: parse replacementText

    //peReference
    case '<' =>
      nextch
      markupDecl1()
    case _ if isSpace(ch) =>
      xSpace
    case _ =>
      reportSyntaxError("markupdecl: unexpected character '"+ch+"' #" + ch.asInstanceOf[Int])
      nextch
  }

  /**  "rec-xml/#ExtSubset" pe references may not occur within markup 
   declarations 
   */
  def intSubset() {
    //Console.println("(DEBUG) intSubset()")
    xSpace
    while (']' != ch)
      markupDecl()
  }

  /** &lt;! element := ELEMENT
   */
  def elementDecl() {
    xToken("EMENT")
    xSpace
    val n = xName
    xSpace
    while ('>' != ch) {
      //Console.println("["+ch+"]")
      putChar(ch)
      nextch
    }
    //Console.println("END["+ch+"]")
    nextch
    val cmstr = cbuf.toString()
    cbuf.length = 0
    handle.elemDecl(n, cmstr)
  }

  /** &lt;! attlist := ATTLIST
   */
  def attrDecl() = {
    xToken("TTLIST")
    xSpace
    val n = xName
    xSpace
    var attList: List[AttrDecl] = Nil
    // later: find the elemDecl for n
    while ('>' != ch) {
      val aname = xName
      //Console.println("attribute name: "+aname);
      var defdecl: DefaultDecl = null
      xSpace
      // could be enumeration (foo,bar) parse this later :-/
      while ('"' != ch && '\'' != ch && '#' != ch && '<' != ch) {
        if (!isSpace(ch))
          cbuf.append(ch);
        nextch;
      }
      val atpe = cbuf.toString()
      cbuf.length = 0
      //Console.println("attr type: "+atpe);
      ch match {
        case '\'' | '"' =>
          val defValue = xAttributeValue() // default value
          defdecl = DEFAULT(false, defValue)

        case '#' =>
          nextch
          xName match {
            case "FIXED" =>
              xSpace
              val defValue = xAttributeValue() // default value
              defdecl = DEFAULT(true, defValue)
            case "IMPLIED" =>
              defdecl = IMPLIED
            case "REQUIRED" =>
              defdecl = REQUIRED
          }
        case _ =>
      }
      xSpaceOpt

      attList = AttrDecl(aname, atpe, defdecl) :: attList
      cbuf.length = 0
    }
    nextch
    handle.attListDecl(n, attList.reverse)
  }

  /** &lt;! element := ELEMENT
   */
  def entityDecl() = {
    //Console.println("entityDecl()")
    var isParameterEntity = false
    var entdef: EntityDef = null
    xToken("NTITY")
    xSpace
    if ('%' == ch) {
      nextch
      isParameterEntity = true
      xSpace
    }
    val n = xName
    xSpace
    ch match {
      case 'S' | 'P' => //sy
        val extID = externalID()
        if (isParameterEntity) {
          xSpaceOpt
          xToken('>')
          handle.parameterEntityDecl(n, ExtDef(extID))
        } else { // notation?
          xSpace
          if ('>' != ch) {
            xToken("NDATA")
            xSpace
            val notat = xName
            xSpaceOpt
            xToken('>')
            handle.unparsedEntityDecl(n, extID, notat)
          } else {
            nextch
            handle.parsedEntityDecl(n, ExtDef(extID))
          }
        }

      case '"' | '\'' =>
        val av = xEntityValue()
        xSpaceOpt
        xToken('>')
        if (isParameterEntity)
          handle.parameterEntityDecl(n, IntDef(av))
        else
          handle.parsedEntityDecl(n, IntDef(av))
    }
    {}
  } // entityDecl

  /** 'N' notationDecl ::= "OTATION"
   */
  def notationDecl() {
    xToken("OTATION")
    xSpace
    val notat = xName
    xSpace
    val extID = if (ch == 'S') {
      externalID();
    }
    else if (ch == 'P') {
      /** PublicID (without system, only used in NOTATION) */
      nextch
      xToken("UBLIC")
      xSpace
      val pubID = pubidLiteral()
      xSpaceOpt
      val sysID = if (ch != '>')
        systemLiteral()
      else
        null;
      new PublicID(pubID, sysID);
    } else {
      reportSyntaxError("PUBLIC or SYSTEM expected");
      error("died parsing notationdecl")
    }
    xSpaceOpt
    xToken('>')
    handle.notationDecl(notat, extID)
  }

  /**
   * report a syntax error
   */
  def reportSyntaxError(pos: Int, str: String) {
    curInput.reportError(pos, str)
    //error("MarkupParser::synerr") // DEBUG
  }

  def reportSyntaxError(str: String): Unit = reportSyntaxError(pos, str)

  /**
   * report a syntax error
   */
  def reportValidationError(pos: Int, str: String) {
    curInput.reportError(pos, str)
  }

  def push(entityName: String) {
    //Console.println("BEFORE PUSHING  "+ch)
    //Console.println("BEFORE PUSHING  "+pos)
    //Console.print("[PUSHING "+entityName+"]")
    if (!eof)
      inpStack = curInput :: inpStack

    curInput = replacementText(entityName)
    nextch
  }

  /*
  def push(src:Source) = {
    curInput = src
    nextch
  }
  */

  def pushExternal(systemId: String) {
    //Console.print("BEFORE PUSH, curInput = $"+curInput.descr)
    //Console.println(" stack = "+inpStack.map { x => "$"+x.descr })

    //Console.print("[PUSHING EXTERNAL "+systemId+"]")
    if (!eof)
      inpStack = curInput :: inpStack

    curInput = externalSource(systemId)

    //Console.print("AFTER PUSH, curInput = $"+curInput.descr)
    //Console.println(" stack = "+inpStack.map { x => "$"+x.descr })

    nextch
  }

  def pop() {
    curInput = inpStack.head
    inpStack = inpStack.tail
    ch = curInput.ch
    pos = curInput.pos
    eof = false // must be false, because of places where entity refs occur
    //Console.println("\n AFTER POP, curInput = $"+curInput.descr);
    //Console.println(inpStack.map { x => x.descr });
  }

  /** for the moment, replace only character references 
   *  see spec 3.3.3
   *  precond: cbuf empty
   */
  def normalizeAttributeValue(attval: String): String = {
    val s: Seq[Char] = attval
    val it = s.elements
    while (it.hasNext) {
      it.next match {
        case ' '|'\t'|'\n'|'\r' => 
          cbuf.append(' ');
        case '&' => it.next match {
          case '#' =>
            var c = it.next
            val s = xCharRef ({ () => c }, { () => c = it.next })
            cbuf.append(s)
          case nchar =>
            val nbuf = new StringBuilder()
            var d = nchar
            do {
              nbuf.append(d)
              d = it.next
            } while(d != ';');
            nbuf.toString() match {
              case "lt"    => cbuf.append('<')
              case "gt"    => cbuf.append('>')
              case "amp"   => cbuf.append('&')
              case "apos"  => cbuf.append('\'')
              case "quot"  => cbuf.append('"')
              case "quote" => cbuf.append('"')
              case name =>
                cbuf.append('&')
                cbuf.append(name)
                cbuf.append(';')
            }
        }
        case c =>
          cbuf.append(c)
      }
    }
    val name = cbuf.toString()
    cbuf.length = 0
    name
  }

}
