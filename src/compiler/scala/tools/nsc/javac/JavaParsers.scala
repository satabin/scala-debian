/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: Parsers.scala 15004 2008-05-13 16:37:33Z odersky $
//todo: allow infix type patterns


package scala.tools.nsc.javac

import scala.tools.nsc.util.{Position, OffsetPosition, NoPosition, BatchSourceFile}
import scala.collection.mutable.ListBuffer
import symtab.Flags
import JavaTokens._

trait JavaParsers extends JavaScanners {
  val global : Global 
  import global._
  import posAssigner.atPos
  import definitions._

  case class JavaOpInfo(operand: Tree, operator: Name, pos: Int)

  class JavaUnitParser(val unit: global.CompilationUnit) extends JavaParser {
    val in = new JavaUnitScanner(unit)
    def freshName(pos : Position, prefix : String) = unit.fresh.newName(pos, prefix)
    implicit def i2p(offset : Int) : Position = new OffsetPosition(unit.source,offset)
    def warning(pos : Int, msg : String) : Unit = unit.warning(pos, msg)
    def syntaxError(pos: Int, msg: String) : Unit = unit.error(pos, msg)
  }

  abstract class JavaParser {

    val in: JavaScanner
    protected def posToReport: Int = in.currentPos
    protected def freshName(pos : Position, prefix : String): Name
    protected implicit def i2p(offset : Int) : Position
    private implicit def p2i(pos : Position): Int = pos.offset.getOrElse(-1)

    /** The simple name of the package of the currently parsed file */
    private var thisPackageName: Name = nme.EMPTY
    
    /** this is the general parse method
     */
    def parse(): Tree = {
      val t = compilationUnit()
      accept(EOF)
      t
    }

    // -------- error handling ---------------------------------------

    private var lastErrorPos : Int = -1

    protected def skip() {
      var nparens = 0
      var nbraces = 0
      while (true) {
        in.token match {
          case EOF =>
            return
          case SEMI =>
            if (nparens == 0 && nbraces == 0) return
          case RPAREN =>
            nparens -= 1
          case RBRACE =>
            if (nbraces == 0) return
            nbraces -= 1
          case LPAREN =>
            nparens += 1
          case LBRACE =>
            nbraces += 1
          case _ =>
        }
        in.nextToken
      }
    }

    def warning(pos : Int, msg : String) : Unit
    def syntaxError(pos: Int, msg: String) : Unit
    def syntaxError(msg: String, skipIt: Boolean) {
      syntaxError(in.currentPos, msg, skipIt)
    }
    
    def syntaxError(pos: Int, msg: String, skipIt: Boolean) {
      if (pos > lastErrorPos) {
        syntaxError(pos, msg)
        // no more errors on this token.
        lastErrorPos = in.currentPos
      }
      if (skipIt) 
        skip()
    }
    def warning(msg: String) : Unit = warning(in.currentPos, msg)
        
    def errorTypeTree = TypeTree().setType(ErrorType).setPos((in.currentPos))
    def errorTermTree = Literal(Constant(null)).setPos((in.currentPos))
    def errorPatternTree = blankExpr.setPos((in.currentPos))

    // --------- tree building -----------------------------

    def rootId(name: Name) = 
      Select(Ident(nme.ROOTPKG), name)

    def scalaDot(name: Name): Tree =
      Select(rootId(nme.scala_) setSymbol ScalaPackage, name)

    def javaDot(name: Name): Tree = 
      Select(rootId(nme.java), name)

    def javaLangDot(name: Name): Tree =
      Select(javaDot(nme.lang), name)

    def javaLangObject(): Tree = javaLangDot(nme.Object.toTypeName)

    def arrayOf(tpt: Tree) = 
      AppliedTypeTree(scalaDot(nme.Array.toTypeName), List(tpt))

    def blankExpr = Ident(nme.WILDCARD)

    def makePackaging(pkg: Tree, stats: List[Tree]): PackageDef = pkg match {
      case Ident(name) =>
        PackageDef(name, stats).setPos(pkg.pos)
      case Select(qual, name) =>
        makePackaging(qual, List(PackageDef(name, stats).setPos(pkg.pos)))
    }

    def makeTemplate(parents: List[Tree], stats: List[Tree]) =
      Template(
        parents, 
        emptyValDef,
        if (treeInfo.firstConstructor(stats) == EmptyTree) makeConstructor(List()) :: stats
        else stats)

    def makeParam(name: Name, tpt: Tree) =
      ValDef(Modifiers(Flags.JAVA | Flags.PARAM), name, tpt, EmptyTree)

    def makeConstructor(formals: List[Tree]) = {
      var count = 0
      val vparams = 
        for (formal <- formals) 
        yield {
          count += 1
          makeParam(newTermName("x$"+count), formal)
        }
      DefDef(Modifiers(Flags.JAVA), nme.CONSTRUCTOR, List(), List(vparams), TypeTree(), blankExpr)
    }
    
    // ------------- general parsing ---------------------------

    /** skip parent or brace enclosed sequence of things */
    def skipAhead() {
      var nparens = 0
      var nbraces = 0
      do {
        in.token match {
          case LPAREN =>
            nparens += 1
          case LBRACE =>
            nbraces += 1
          case _ =>
        }
        in.nextToken
        in.token match {
          case RPAREN =>
            nparens -= 1
          case RBRACE =>
            nbraces -= 1
          case _ =>
        }
      } while (in.token != EOF && (nparens > 0 || nbraces > 0))
    }

    def skipTo(tokens: Int*) {
      while (!(tokens contains in.token) && in.token != EOF) {
        if (in.token == LBRACE) { skipAhead(); accept(RBRACE) }
        else if (in.token == LPAREN) { skipAhead(); accept(RPAREN) }
        else in.nextToken
      }
    }

    /** Consume one token of the specified type, or
      * signal an error if it is not there.
      */
    def accept(token: Int): Int = {
      val pos = in.currentPos
      if (in.token != token) {
        val posToReport = 
          //if (in.currentPos.line(unit.source).get(0) > in.lastPos.line(unit.source).get(0))
          //  in.lastPos
          //else
            in.currentPos
        val msg =
          JavaScannerConfiguration.token2string(token) + " expected but " +
            JavaScannerConfiguration.token2string(in.token) + " found."

        syntaxError(posToReport, msg, true)
      }
      if (in.token == token) in.nextToken
      pos
    }

    def acceptClosingAngle() {
      if (in.token == GTGTGTEQ) in.token = GTGTEQ
      else if (in.token == GTGTGT) in.token = GTGT
      else if (in.token == GTGTEQ) in.token = GTEQ
      else if (in.token == GTGT) in.token = GT
      else if (in.token == GTEQ) in.token = ASSIGN
      else accept(GT)
    }

    def ident(): Name =
      if (in.token == IDENTIFIER) {
        val name = in.name
        in.nextToken
        name
      } else {
        accept(IDENTIFIER)
        nme.ERROR
      }

    def repsep[T <: Tree](p: () => T, sep: Int): List[T] = {
      val buf = new ListBuffer[T] + p()
      while (in.token == sep) {
        in.nextToken
        buf += p()
      }
      buf.toList
    }

    /** Convert (qual)ident to type identifier
     */
    def convertToTypeId(tree: Tree): Tree = tree match {
      case Ident(name) =>
        Ident(name.toTypeName).setPos(tree.pos)
      case Select(qual, name) =>
        Select(qual, name.toTypeName).setPos(tree.pos)
      case AppliedTypeTree(_, _) | ExistentialTypeTree(_, _) =>
        tree
      case _ =>
        syntaxError(tree.pos, "identifier expected", false)
        errorTypeTree
    }

    // -------------------- specific parsing routines ------------------

    def qualId(): Tree = {
      var t: Tree = atPos(in.currentPos) { Ident(ident()) }
      while (in.token == DOT) { 
        in.nextToken
        t = atPos(in.currentPos) { Select(t, ident()) }
      }
      t
    }

    def optArrayBrackets(tpt: Tree): Tree = 
      if (in.token == LBRACKET) {
        val tpt1 = atPos(in.pos) { arrayOf(tpt) }
        in.nextToken
        accept(RBRACKET)
        optArrayBrackets(tpt1)
      } else tpt

    def basicType(): Tree = 
      atPos(in.pos) {
        in.token match {
          case BYTE => in.nextToken; TypeTree(ByteClass.tpe)
          case SHORT => in.nextToken; TypeTree(ShortClass.tpe)
          case CHAR => in.nextToken; TypeTree(CharClass.tpe)
          case INT => in.nextToken; TypeTree(IntClass.tpe)
          case LONG => in.nextToken; TypeTree(LongClass.tpe)
          case FLOAT => in.nextToken; TypeTree(FloatClass.tpe)
          case DOUBLE => in.nextToken; TypeTree(DoubleClass.tpe)
          case BOOLEAN => in.nextToken; TypeTree(BooleanClass.tpe)
          case _ => syntaxError("illegal start of type", true); errorTypeTree
        }
      }

    def typ(): Tree =
      optArrayBrackets {
        if (in.token == FINAL) in.nextToken
        if (in.token == IDENTIFIER) {
          var t = typeArgs(atPos(in.currentPos)(Ident(ident())))
          while (in.token == DOT) {
            in.nextToken
            t = typeArgs(atPos(in.currentPos)(Select(t, ident())))
          }
          convertToTypeId(t)
        } else {
          basicType()
        }
      }

    def typeArgs(t: Tree): Tree = {
      val wildcards = new ListBuffer[TypeDef]
      def typeArg(): Tree = 
        if (in.token == QMARK) {
          val pos = in.currentPos
          in.nextToken
          var lo: Tree = TypeTree(NothingClass.tpe)
          var hi: Tree = TypeTree(AnyClass.tpe)
          if (in.token == EXTENDS) {
            in.nextToken
            hi = typ()
          } else if (in.token == SUPER) {
            in.nextToken
            lo = typ()
          }
          val tdef = atPos(pos) {
            TypeDef(
              Modifiers(Flags.JAVA | Flags.DEFERRED), 
              newTypeName("_$"+ (wildcards.length + 1)),
              List(), 
              TypeBoundsTree(lo, hi))
          }
          wildcards += tdef
          atPos(pos) { Ident(tdef.name) }
        } else {
          typ()
        }
      if (in.token == LT) {
        in.nextToken
        val t1 = convertToTypeId(t)
        val args = repsep(typeArg, COMMA)
        acceptClosingAngle()
        atPos(t1.pos) {
          val t2: Tree = AppliedTypeTree(t1, args)
          if (wildcards.isEmpty) t2
          else ExistentialTypeTree(t2, wildcards.toList)
        }
      } else t
    }

    def annotations(): List[Annotation] = {
      //var annots = new ListBuffer[Annotation]
      while (in.token == AT) {
        in.nextToken
        annotation()
      }
      List() // don't pass on annotations for now
    }

    /** Annotation ::= TypeName [`(' AnnotationArgument {`,' AnnotationArgument} `)']
     */
    def annotation() {
      val pos = in.currentPos
      var t = typ()
      if (in.token == LPAREN) { skipAhead(); accept(RPAREN) }
      else if (in.token == LBRACE) { skipAhead(); accept(RBRACE) }
    }
/*
    def annotationArg() = {
      val pos = in.token
      if (in.token == IDENTIFIER && in.lookaheadToken == ASSIGN) {
        val name = ident()
        accept(ASSIGN)
        atPos(pos) {
          ValDef(Modifiers(Flags.JAVA), name, TypeTree(), elementValue())
        }
      } else {
        elementValue()
      }
    }

    def elementValue(): Tree =
      if (in.token == AT) annotation()
      else if (in.token == LBRACE) elementValueArrayInitializer()
      else expression1()

    def elementValueArrayInitializer() = {
      accept(LBRACE)
      val buf = new ListBuffer[Tree]
      def loop() =
        if (in.token != RBRACE) {
          buf += elementValue()
          if (in.token == COMMA) {
            in.nextToken
            loop()
          }
        }
      loop()
      accept(RBRACE)
      buf.toList
    }
 */

    def modifiers(inInterface: Boolean): Modifiers = {
      var flags: Long = Flags.JAVA
      var privateWithin: Name =
        if (inInterface) nme.EMPTY.toTypeName else thisPackageName
      while (true) {
        in.token match {
          case AT if (in.lookaheadToken != INTERFACE) => 
            in.nextToken
            annotation()
          case PUBLIC => 
            privateWithin = nme.EMPTY.toTypeName
            in.nextToken
          case PROTECTED => 
            flags |= Flags.PROTECTED
            privateWithin = thisPackageName
            in.nextToken
          case PRIVATE =>
            flags |= Flags.PRIVATE
            privateWithin = nme.EMPTY.toTypeName
            in.nextToken
          case STATIC =>  
            flags |= Flags.STATIC
            in.nextToken
          case ABSTRACT =>
            flags |= Flags.ABSTRACT
            in.nextToken
          case FINAL =>
            flags |= Flags.FINAL
            in.nextToken
          case NATIVE | SYNCHRONIZED | TRANSIENT | VOLATILE | STRICTFP =>
            in.nextToken
          case _ =>
            return Modifiers(flags, privateWithin)
        }
      }
      throw new Error("should not be here")
    }

    def typeParams(): List[TypeDef] =
      if (in.token == LT) {
        in.nextToken
        val tparams = repsep(typeParam, COMMA)
        acceptClosingAngle()
        tparams
      } else List()

    def typeParam(): TypeDef = 
      atPos(in.currentPos) {
        val name = ident().toTypeName
        val hi = 
          if (in.token == EXTENDS) {
            in.nextToken
            bound()
          } else {
            scalaDot(nme.Any.toTypeName)
          }
        TypeDef(Modifiers(Flags.JAVA | Flags.DEFERRED | Flags.PARAM), name, List(), 
                TypeBoundsTree(scalaDot(nme.Nothing.toTypeName), hi))
      }

    def bound(): Tree = 
      atPos(in.currentPos) {
        val buf = new ListBuffer[Tree] + typ()
        while (in.token == AMP) {
          in.nextToken
          buf += typ()
        }
        val ts = buf.toList
        if (ts.tail.isEmpty) ts.head
        else CompoundTypeTree(Template(ts, emptyValDef, List()))
      }
        
    def formalParams(): List[ValDef] = {
      accept(LPAREN)
      val vparams = if (in.token == RPAREN) List() else repsep(formalParam, COMMA)
      accept(RPAREN)
      vparams
    }

    def formalParam(): ValDef = {
      if (in.token == FINAL) in.nextToken
      annotations()
      var t = typ()
      if (in.token == DOTDOTDOT) {
        in.nextToken
        t = atPos(t.pos) {
          AppliedTypeTree(scalaDot(nme.REPEATED_PARAM_CLASS_NAME.toTypeName), List(t))
        }
      }
     varDecl(in.currentPos, Modifiers(Flags.JAVA | Flags.PARAM), t, ident())
    }

    def optThrows() {
      if (in.token == THROWS) {
        in.nextToken
        repsep(typ, COMMA)
      }
    }

    def methodBody(): Tree = {
      skipAhead()
      accept(RBRACE) // skip block
      blankExpr
    } 

    def definesInterface(token: Int) = token == INTERFACE || token == AT

    def termDecl(mods: Modifiers, parentToken: Int): List[Tree] = {
      val inInterface = definesInterface(parentToken)
      val tparams = if (in.token == LT) typeParams() else List()
      val isVoid = in.token == VOID
      var rtpt =
        if (isVoid) {
          in.nextToken
          TypeTree(UnitClass.tpe) setPos in.pos
        } else typ()
      var pos = in.currentPos
      val rtptName = rtpt match {
        case Ident(name) => name
        case _ => nme.EMPTY
      }
      if (in.token == LPAREN && rtptName != nme.EMPTY && !inInterface) { 
        // constructor declaration
        val vparams = formalParams()
        optThrows()
        List {
          atPos(pos) {
            DefDef(mods, nme.CONSTRUCTOR, tparams, List(vparams), TypeTree(), methodBody())
          }
        }
      } else {        
        var mods1 = mods
        if (mods hasFlag Flags.ABSTRACT) mods1 = mods &~ Flags.ABSTRACT | Flags.DEFERRED
        pos = in.currentPos
        val name = ident()
        if (in.token == LPAREN) {
          // method declaration
          val vparams = formalParams()
          if (!isVoid) rtpt = optArrayBrackets(rtpt)
          optThrows()
          val body = 
            if (!inInterface && in.token == LBRACE) {
              methodBody()
            } else {
              if (parentToken == AT && in.token == DEFAULT) {
                val annot = 
                  atPos(pos) {
                    Annotation(
                      New(rootId(nme.AnnotationDefaultATTR.toTypeName), List(List())),
                      List())
                  }
                mods1 = Modifiers(mods1.flags, mods1.privateWithin, annot :: mods1.annotations)
                skipTo(SEMI)
                accept(SEMI)
                blankExpr
              } else {
                accept(SEMI)
                EmptyTree 
              }
            }
          if (inInterface) mods1 |= Flags.DEFERRED
          List {
            atPos(pos) {
              DefDef(mods1, name, tparams, List(vparams), rtpt, body)
            }
          }
        } else {
          if (inInterface) mods1 |= Flags.FINAL | Flags.STATIC
          val result = fieldDecls(pos, mods1, rtpt, name)
          accept(SEMI)
          result
        }
      }
    }
    
    /** Parse a sequence of field declarations, separated by commas.
     *  This one is tricky because a comma might also appear in an
     *  initializer. Since we don't parse initializers we don't know
     *  what the comma signifies.
     *  We solve this with a second list buffer `maybe' which contains
     *  potential variable definitions. 
     *  Once we have reached the end of the statement, we know whether
     *  these potential definitions are real or not.
     */
    def fieldDecls(pos: Position, mods: Modifiers, tpt: Tree, name: Name): List[Tree] = {
      val buf = new ListBuffer[Tree] + varDecl(pos, mods, tpt, name)
      val maybe = new ListBuffer[Tree] // potential variable definitions.
      while (in.token == COMMA) {
        in.nextToken
        if (in.token == IDENTIFIER) { // if there's an ident after the comma ...
          val name = ident()
          if (in.token == ASSIGN || in.token == SEMI) { // ... followed by a `=' or `;', we know it's a real variable definition
            buf ++= maybe 
            buf += varDecl(in.currentPos, mods, tpt.duplicate, name)
            maybe.clear()
          } else if (in.token == COMMA) { // ... if there's a comma after the ident, it could be a real vardef or not.
            maybe += varDecl(in.currentPos, mods, tpt.duplicate, name)
          } else { // ... if there's something else we were still in the initializer of the
                   // previous var def; skip to next comma or semicolon.
            skipTo(COMMA, SEMI)
            maybe.clear()
          }
        } else { // ... if there's no ident following the comma we were still in the initializer of the
                 // previous var def; skip to next comma or semicolon.
          skipTo(COMMA, SEMI)
          maybe.clear()
        }
      }
      if (in.token == SEMI) {
        buf ++= maybe // every potential vardef that survived until here is real. 
      }
      buf.toList
    }

    def varDecl(pos: Position, mods: Modifiers, tpt: Tree, name: Name): ValDef = {
      val tpt1 = optArrayBrackets(tpt)
      if (in.token == ASSIGN && !(mods hasFlag Flags.PARAM)) skipTo(COMMA, SEMI)
      val mods1 = if (mods hasFlag Flags.FINAL) mods &~ Flags.FINAL else mods | Flags.MUTABLE
      atPos(pos) {
        ValDef(mods1, name, tpt1, blankExpr)
      }
    }

    def memberDecl(mods: Modifiers, parentToken: Int): List[Tree] = in.token match {
      case CLASS | ENUM | INTERFACE | AT => 
        typeDecl(if (definesInterface(parentToken)) mods | Flags.STATIC else mods)
      case _ => 
        termDecl(mods, parentToken)
    }

    def makeCompanionObject(cdef: ClassDef, statics: List[Tree]): Tree = 
      atPos(cdef.pos) {
        ModuleDef(cdef.mods & (Flags.AccessFlags | Flags.JAVA), cdef.name.toTermName,
                  makeTemplate(List(), statics))
      }

    def importCompanionObject(cdef: ClassDef): Tree =
      atPos(cdef.pos) {
        Import(Ident(cdef.name.toTermName), List((nme.WILDCARD, null)))
      }

    def addCompanionObject(statics: List[Tree], cdef: ClassDef): List[Tree] = 
      if (statics.isEmpty) List(cdef)
      else List(makeCompanionObject(cdef, statics), importCompanionObject(cdef), cdef)

    def importDecl(): List[Tree] = {
      accept(IMPORT)
      val pos = in.currentPos
      val buf = new ListBuffer[Name]
      def collectIdents() {
        if (in.token == ASTERISK) {
          in.nextToken
          buf += nme.WILDCARD
        } else {
          buf += ident()
          if (in.token == DOT) {
            in.nextToken
            collectIdents()
          }
        }
      }
      if (in.token == STATIC) in.nextToken
      else buf += nme.ROOTPKG
      collectIdents()
      accept(SEMI)
      val names = buf.toList
      if (names.length < 2) {
        syntaxError(pos, "illegal import", false)
        List()
      } else {
        val qual = ((Ident(names.head): Tree) /: names.tail.init) (Select(_, _))
        val lastname = names.last
        List {
          atPos(pos) {
            if (lastname == nme.WILDCARD) Import(qual, List((lastname, null)))
            else Import(qual, List((lastname, lastname)))
          }
        }
      }
    }

    def interfacesOpt() =
      if (in.token == IMPLEMENTS) {
        in.nextToken
        repsep(typ, COMMA)
      } else {
        List()
      }

    def classDecl(mods: Modifiers): List[Tree] = {
      accept(CLASS)
      val pos = in.currentPos
      val name = ident().toTypeName
      val tparams = typeParams()
      val superclass = 
        if (in.token == EXTENDS) {
          in.nextToken
          typ()
        } else {
          javaLangObject()
        }
      val interfaces = interfacesOpt()
      val (statics, body) = typeBody(CLASS) 
      addCompanionObject(statics, atPos(pos) {
        ClassDef(mods, name, tparams, makeTemplate(superclass :: interfaces, body))
      })
    }

    def interfaceDecl(mods: Modifiers): List[Tree] = {
      accept(INTERFACE)
      val pos = in.currentPos
      val name = ident().toTypeName
      val tparams = typeParams()
      val parents = 
        if (in.token == EXTENDS) {
          in.nextToken
          repsep(typ, COMMA)
        } else {
          List(javaLangObject)
        }
      val (statics, body) = typeBody(INTERFACE)
      addCompanionObject(statics, atPos(pos) {
        ClassDef(mods | Flags.TRAIT | Flags.INTERFACE | Flags.ABSTRACT, 
                 name, tparams, 
                 makeTemplate(parents, body))
      })
    }

    def typeBody(leadingToken: Int): (List[Tree], List[Tree]) = {
      accept(LBRACE)
      val defs = typeBodyDecls(leadingToken)
      accept(RBRACE)
      defs
    }
 
    def typeBodyDecls(parentToken: Int): (List[Tree], List[Tree]) = {
      val inInterface = definesInterface(parentToken)
      val statics = new ListBuffer[Tree]
      val members = new ListBuffer[Tree]
      while (in.token != RBRACE && in.token != EOF) {
        var mods = modifiers(inInterface)
        if (in.token == LBRACE) {
          skipAhead() // skip init block, we just assume we have seen only static
          accept(RBRACE)
        } else if (in.token == SEMI) {
          in.nextToken
        } else {
          if (in.token == ENUM || definesInterface(in.token)) mods |= Flags.STATIC
          val decls = memberDecl(mods, parentToken)
          (if ((mods hasFlag Flags.STATIC) || inInterface && !(decls exists (_.isInstanceOf[DefDef])))
             statics 
           else 
             members) ++= decls
        }
      }
      (statics.toList, members.toList)
    }
      
    def annotationDecl(mods: Modifiers): List[Tree] = {
      accept(AT)
      accept(INTERFACE)
      val pos = in.currentPos
      val name = ident().toTypeName
      val parents = List(scalaDot(newTypeName("Annotation")), 
                         Select(javaLangDot(newTermName("annotation")), newTypeName("Annotation")),
                         scalaDot(newTypeName("ClassfileAnnotation")))
      val (statics, body) = typeBody(AT)
      def getValueMethodType(tree: Tree) = tree match {
        case DefDef(_, nme.value, _, _, tpt, _) => Some(tpt.duplicate)
        case _ => None
      }
      var templ = makeTemplate(parents, body)
      for (stat <- templ.body; tpt <- getValueMethodType(stat))
        templ = makeTemplate(parents, makeConstructor(List(tpt)) :: templ.body)
      addCompanionObject(statics, atPos(pos) {
        ClassDef(mods, name, List(), templ)
      })
    }

    def enumDecl(mods: Modifiers): List[Tree] = {
      accept(ENUM)
      val pos = in.currentPos
      val name = ident().toTypeName
      def enumType = Ident(name)
      val interfaces = interfacesOpt()
      accept(LBRACE)
      val buf = new ListBuffer[Tree]
      def parseEnumConsts() {
        if (in.token != RBRACE && in.token != SEMI && in.token != EOF) {
          buf += enumConst(enumType)
          if (in.token == COMMA) {
            in.nextToken
            parseEnumConsts()
          }
        }
      }
      parseEnumConsts() 
      val consts = buf.toList
      val (statics, body) = 
        if (in.token == SEMI) {
          in.nextToken
          typeBodyDecls(ENUM)
        } else {
          (List(), List())
        }
      val predefs = List(
        DefDef(
          Modifiers(Flags.JAVA | Flags.STATIC), newTermName("values"), List(), 
          List(List()),
          arrayOf(enumType),
          blankExpr),
        DefDef(
          Modifiers(Flags.JAVA | Flags.STATIC), newTermName("valueOf"), List(), 
          List(List(makeParam(newTermName("x"), TypeTree(StringClass.tpe)))),
          enumType,
          blankExpr))
      accept(RBRACE)
      val superclazz = 
        AppliedTypeTree(javaLangDot(newTypeName("Enum")), List(enumType))
      addCompanionObject(consts ::: statics ::: predefs, atPos(pos) {
        ClassDef(mods, name, List(), 
                 makeTemplate(superclazz :: interfaces, body))
      })
    }

    def enumConst(enumType: Tree) = {
      annotations()
      atPos(in.currentPos) {
        val name = ident()
        if (in.token == LPAREN) {
          // skip arguments
          skipAhead()
          accept(RPAREN)
        }
        if (in.token == LBRACE) {
          // skip classbody
          skipAhead()
          accept(RBRACE)
        }
        ValDef(Modifiers(Flags.JAVA | Flags.STATIC), name, enumType, blankExpr)
      }
    }

    def typeDecl(mods: Modifiers): List[Tree] = in.token match {
      case ENUM => enumDecl(mods)
      case INTERFACE => interfaceDecl(mods)
      case AT => annotationDecl(mods)
      case CLASS => classDecl(mods)
      case _ => in.nextToken; syntaxError("illegal start of type declaration", true); List(errorTypeTree)
    }

    /** CompilationUnit ::= [package QualId semi] TopStatSeq 
     */
    def compilationUnit(): Tree = {
      var pos = in.currentPos;
      val pkg = 
        if (in.token == AT || in.token == PACKAGE) {
          annotations()
          pos = in.currentPos
          accept(PACKAGE)
          val pkg = qualId()
          accept(SEMI)
          pkg
        } else {
          Ident(nme.EMPTY_PACKAGE_NAME)
        }
      thisPackageName = pkg match {
        case Ident(name) => name.toTypeName
        case Select(_, name) => name.toTypeName
      }
      val buf = new ListBuffer[Tree]
      while (in.token == IMPORT)
        buf ++= importDecl()
      while (in.token != EOF && in.token != RBRACE) {
        while (in.token == SEMI) in.nextToken
        buf ++= typeDecl(modifiers(false))
      }
      accept(EOF)
      atPos(pos) {
        makePackaging(pkg, buf.toList)
      }
    }
  }
}
