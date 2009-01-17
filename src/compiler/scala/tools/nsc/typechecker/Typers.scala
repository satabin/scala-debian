/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: Typers.scala 16881 2009-01-09 16:28:11Z cunei $

//todo: rewrite or disllow new T where T is a mixin (currently: <init> not a member of T)
//todo: use inherited type info also for vars and values
//todo: disallow C#D in superclass
//todo: treat :::= correctly
package scala.tools.nsc.typechecker

import scala.collection.mutable.{HashMap, ListBuffer}
import scala.compat.Platform.currentTime
import scala.tools.nsc.util.{HashSet, Position, Set, NoPosition, SourceFile}
import symtab.Flags._
import util.HashSet
 
// Suggestion check whether we can do without priminng scopes with symbols of outer scopes,
// like the IDE does. 
/** This trait provides methods to assign types to trees.
 *
 *  @author  Martin Odersky
 *  @version 1.0
 */
trait Typers { self: Analyzer =>
  import global._
  import definitions._
  import posAssigner.atPos

  private final val printTypings = false

  var appcnt = 0
  var idcnt = 0
  var selcnt = 0
  var implcnt = 0
  var impltime = 0l

  private val transformed = new HashMap[Tree, Tree]

  private val superDefs = new HashMap[Symbol, ListBuffer[Tree]]

  def resetTyper() {
    resetContexts
    resetNamer()
    transformed.clear
    superDefs.clear
  }

  object UnTyper extends Traverser {
    override def traverse(tree: Tree) = {
      if (tree != EmptyTree) tree.tpe = null
      if (tree.hasSymbol) tree.symbol = NoSymbol
      super.traverse(tree)
    }
  } 
  // IDE hooks
  def newTyper(context: Context): Typer = new NormalTyper(context)
  private class NormalTyper(context : Context) extends Typer(context)
  // hooks for auto completion

  /** when in 1.4 mode the compiler accepts and ignores useless
   *  type parameters of Java generics
   */
  def onePointFourMode = true // todo changeto: settings.target.value == "jvm-1.4"

  // Mode constants

  /** The three mode <code>NOmode</code>, <code>EXPRmode</code>
   *  and <code>PATTERNmode</code> are mutually exclusive.
   */
  val NOmode        = 0x000
  val EXPRmode      = 0x001
  val PATTERNmode   = 0x002
  val TYPEmode      = 0x004
 
  /** The mode <code>SCCmode</code> is orthogonal to above. When set we are
   *  in the this or super constructor call of a constructor.
   */
  val SCCmode       = 0x008

  /** The mode <code>FUNmode</code> is orthogonal to above.
   *  When set we are looking for a method or constructor.
   */
  val FUNmode       = 0x010

  /** The mode <code>POLYmode</code> is orthogonal to above.
   *  When set expression types can be polymorphic.
   */
  val POLYmode      = 0x020

  /** The mode <code>QUALmode</code> is orthogonal to above. When set
   *  expressions may be packages and Java statics modules.
   */
  val QUALmode      = 0x040

  /** The mode <code>TAPPmode</code> is set for the function/type constructor
   *  part of a type application. When set we do not decompose PolyTypes.
   */
  val TAPPmode      = 0x080

  /** The mode <code>SUPERCONSTRmode</code> is set for the <code>super</code>
   *  in a superclass constructor call <code>super.&lt;init&gt;</code>.
   */
  val SUPERCONSTRmode = 0x100

  /** The mode <code>SNDTRYmode</code> indicates that an application is typed
   *  for the 2nd time. In that case functions may no longer be coerced with
   *  implicit views.
   */
  val SNDTRYmode    = 0x200

  /** The mode <code>LHSmode</code> is set for the left-hand side of an
   *  assignment.
   */
  val LHSmode       = 0x400

  /** The mode <code>REGPATmode</code> is set when regular expression patterns
   *  are allowed. 
   */
  val REGPATmode    = 0x1000

  /** The mode <code>ALTmode</code> is set when we are under a pattern alternative */
  val ALTmode       = 0x2000
  
  /** The mode <code>HKmode</code> is set when we are typing a higher-kinded type
   * adapt should then check kind-arity based on the prototypical type's kind arity
   * type arguments should not be inferred
   */
  val HKmode        = 0x4000 // @M: could also use POLYmode | TAPPmode 

  /** The mode <code>JAVACALLmode</code> is set when we are typing a call to a Java method
   *  needed temporarily for vararg conversions
   *  !!!VARARG-CONVERSION!!!
   */
  val JAVACALLmode  = 0x8000

  /** The mode <code>TYPEPATmode</code> is set when we are typing a type in a pattern
   */
  val TYPEPATmode   = 0x10000

  private val stickyModes: Int  = EXPRmode | PATTERNmode | TYPEmode | ALTmode

  private def funMode(mode: Int) = mode & (stickyModes | SCCmode) | FUNmode | POLYmode

  private def typeMode(mode: Int) = 
    if ((mode & (PATTERNmode | TYPEPATmode)) != 0) TYPEmode | TYPEPATmode
    else TYPEmode

  private def argMode(fun: Tree, mode: Int) =
    if (treeInfo.isSelfOrSuperConstrCall(fun)) mode | SCCmode 
    else if (fun.symbol hasFlag JAVA) mode | JAVACALLmode // !!!VARARG-CONVERSION!!!
    else mode 

  private val DivergentImplicit = new Exception()

  abstract class Typer(context0: Context) {
    import context0.unit

    val infer = new Inferencer(context0) {
      override def isCoercible(tp: Type, pt: Type): Boolean =
        tp.isError || pt.isError ||
        context0.implicitsEnabled && // this condition prevents chains of views
        inferView(NoPosition, tp, pt, false) != EmptyTree
    }

    /**
     *  @param pos             ...
     *  @param from            ...
     *  @param to              ...
     *  @param reportAmbiguous ...
     *  @return                ...
     */
    private def inferView(pos: Position, from: Type, to: Type, reportAmbiguous: Boolean): Tree = {
      if (settings.debug.value) log("infer view from "+from+" to "+to)//debug
      if (phase.id > currentRun.typerPhase.id) EmptyTree
      else from match {
        case MethodType(_, _) => EmptyTree
        case OverloadedType(_, _) => EmptyTree
        case PolyType(_, _) => EmptyTree
        case _ =>
          val result = inferImplicit(pos, MethodType(List(from), to), reportAmbiguous)
          if (result != EmptyTree) result
          else inferImplicit(
            pos,
            MethodType(List(appliedType(ByNameParamClass.typeConstructor, List(from))), to),
            reportAmbiguous)
      }
    }

    /**
     *  @param pos             ...
     *  @param from            ...
     *  @param name            ...
     *  @param tp              ...
     *  @param reportAmbiguous ...
     *  @return                ...
     */
    private def inferView(pos: Position, from: Type, name: Name, tp: Type, reportAmbiguous: Boolean): Tree = {
      val to = refinedType(List(WildcardType), NoSymbol)
      var psym = (if (name.isTypeName) to.typeSymbol.newAbstractType(pos, name) 
                  else to.typeSymbol.newValue(pos, name)) 
      psym = to.decls enter psym
      psym setInfo tp
      inferView(pos, from, to, reportAmbiguous)
    }

    import infer._

    private var namerCache: Namer = null
    def namer = {
      if ((namerCache eq null) || namerCache.context != context)
        namerCache = newNamer(context)
      namerCache
    }

    private[typechecker] var context = context0
    def context1 = context

    /** Report a type error.
     *
     *  @param pos0   The position where to report the error
     *  @param ex     The exception that caused the error
     */
    def reportTypeError(pos0: Position, ex: TypeError) {
      if (settings.debug.value) ex.printStackTrace()
      val pos = if (ex.pos == NoPosition) pos0 else ex.pos
      ex match {
        case CyclicReference(sym, info: TypeCompleter) =>
          val msg = 
            info.tree match {
              case ValDef(_, _, tpt, _) if (tpt.tpe eq null) =>
                "recursive "+sym+" needs type"
              case DefDef(_, _, _, _, tpt, _) if (tpt.tpe eq null) =>
                (if (sym.owner.isClass && sym.owner.info.member(sym.name).hasFlag(OVERLOADED)) "overloaded "
                 else "recursive ")+sym+" needs result type"
              case _ =>
                ex.getMessage()
            }
          if (context.retyping) context.error(pos, msg) 
          else context.unit.error(pos, msg)
          if (sym == ObjectClass) 
            throw new FatalError("cannot redefine root "+sym)
        case _ =>
          context.error(pos, ex)
      }
    }

    /** Check that <code>tree</code> is a stable expression.
     *
     *  @param tree ...
     *  @return     ...
     */
    def checkStable(tree: Tree): Tree =
      if (treeInfo.isPureExpr(tree)) tree
      else errorTree(
        tree, 
        "stable identifier required, but "+tree+" found."+
        (if (isStableExceptVolatile(tree)) {
          val tpe = tree.symbol.tpe match {
            case PolyType(_, rtpe) => rtpe
            case t => t
          }
          "\n Note that "+tree.symbol+" is not stable because its type, "+tree.tpe+", is volatile."
         } else ""))

    /** Would tree be a stable (i.e. a pure expression) if the type
     *  of its symbol was not volatile?
     */
    private def isStableExceptVolatile(tree: Tree) = {
      tree.hasSymbol && tree.symbol != NoSymbol && tree.tpe.isVolatile &&
      { val savedTpe = tree.symbol.info
        val savedSTABLE = tree.symbol getFlag STABLE
        tree.symbol setInfo AnyRefClass.tpe
        tree.symbol setFlag STABLE
       val result = treeInfo.isPureExpr(tree)
        tree.symbol setInfo savedTpe
        tree.symbol setFlag savedSTABLE
        result
      }
    }

    /** Check that `tpt' refers to a non-refinement class type */
    def checkClassType(tpt: Tree, existentialOK: Boolean) {
      def check(tpe: Type): Unit = tpe.normalize match {
        case TypeRef(_, sym, _) if sym.isClass && !sym.isRefinementClass => ;
        case ErrorType => ;
        case PolyType(_, restpe) => check(restpe)
        case ExistentialType(_, restpe) if existentialOK => check(restpe)
        case AnnotatedType(_, underlying, _) => check(underlying)
        case t => error(tpt.pos, "class type required but "+t+" found")
      }
      check(tpt.tpe)
    }

    /** Check that type <code>tp</code> is not a subtype of itself.
     *
     *  @param pos ...
     *  @param tp  ...
     *  @return    <code>true</code> if <code>tp</code> is not a subtype of itself.
     */
    def checkNonCyclic(pos: Position, tp: Type): Boolean = {
      def checkNotLocked(sym: Symbol): Boolean = {
        sym.initialize
	sym.lockOK || {error(pos, "cyclic aliasing or subtyping involving "+sym); false}
      }
      tp match {
        case TypeRef(pre, sym, args) =>
          (checkNotLocked(sym)) && (
            !sym.isTypeMember ||
            checkNonCyclic(pos, appliedType(pre.memberInfo(sym), args), sym)   // @M! info for a type ref to a type parameter now returns a polytype
            // @M was: checkNonCyclic(pos, pre.memberInfo(sym).subst(sym.typeParams, args), sym)
          )
        case SingleType(pre, sym) =>
          checkNotLocked(sym)
/*
        case TypeBounds(lo, hi) =>
          var ok = true
          for (t <- lo) ok = ok & checkNonCyclic(pos, t)
          ok
*/
        case st: SubType =>
          checkNonCyclic(pos, st.supertype)
        case ct: CompoundType =>
          var p = ct.parents
          while (!p.isEmpty && checkNonCyclic(pos, p.head)) p = p.tail
          p.isEmpty
        case _ =>
          true
      }
    }

    def checkNonCyclic(pos: Position, tp: Type, lockedSym: Symbol): Boolean = {
      lockedSym.lock {
        throw new TypeError("illegal cyclic reference involving " + lockedSym)
      }
      val result = checkNonCyclic(pos, tp)
      lockedSym.unlock()
      result
    }

    def checkNonCyclic(sym: Symbol) {
      if (!checkNonCyclic(sym.pos, sym.tpe)) sym.setInfo(ErrorType)
    }

    def checkNonCyclic(defn: Tree, tpt: Tree) {
      if (!checkNonCyclic(defn.pos, tpt.tpe, defn.symbol)) {
        tpt.tpe = ErrorType
        defn.symbol.setInfo(ErrorType)
      }
    }

    def checkParamsConvertible(pos: Position, tpe: Type) {
      tpe match {
        case MethodType(formals, restpe) =>
          /*
          if (formals.exists(_.typeSymbol == ByNameParamClass) && formals.length != 1)
            error(pos, "methods with `=>'-parameter can be converted to function values only if they take no other parameters")
          if (formals exists (_.typeSymbol == RepeatedParamClass))
            error(pos, "methods with `*'-parameters cannot be converted to function values");
          */
          if (restpe.isDependent)
            error(pos, "method with dependent type "+tpe+" cannot be converted to function value");
          checkParamsConvertible(pos, restpe)
        case _ =>
      }
    }

    def checkRegPatOK(pos: Position, mode: Int) = 
      if ((mode & REGPATmode) == 0 && 
          phase.id <= currentRun.typerPhase.id) // fixes t1059
        error(pos, "no regular expression pattern allowed here\n"+
              "(regular expression patterns are only allowed in arguments to *-parameters)")

    /** Check that type of given tree does not contain local or private
     *  components.
     */
    object checkNoEscaping extends TypeMap {
      private var owner: Symbol = _
      private var scope: Scope = _
      private var hiddenSymbols: List[Symbol] = _

      /** Check that type <code>tree</code> does not refer to private
       *  components unless itself is wrapped in something private
       *  (<code>owner</code> tells where the type occurs).
       *
       *  @param owner ...
       *  @param tree  ...
       *  @return      ...
       */
      def privates[T <: Tree](owner: Symbol, tree: T): T =
        check(owner, EmptyScope, WildcardType, tree)

      /** Check that type <code>tree</code> does not refer to entities
       *  defined in scope <code>scope</code>.
       *
       *  @param scope ...
       *  @param pt    ...
       *  @param tree  ...
       *  @return      ...
       */
      def locals[T <: Tree](scope: Scope, pt: Type, tree: T): T =
        check(NoSymbol, scope, pt, tree)

      def check[T <: Tree](owner: Symbol, scope: Scope, pt: Type, tree: T): T = {
        this.owner = owner
        this.scope = scope
        hiddenSymbols = List()
        val tp1 = apply(tree.tpe)
        if (hiddenSymbols.isEmpty || inIDE) tree setType tp1 // @S: because arguments of classes are owned by the classes' owner
        else if (hiddenSymbols exists (_.isErroneous)) setError(tree)
        else if (isFullyDefined(pt)) tree setType pt //todo: eliminate
        else if (tp1.typeSymbol.isAnonymousClass) // todo: eliminate
          check(owner, scope, pt, tree setType tp1.typeSymbol.classBound)
        else if (owner == NoSymbol)
          tree setType packSymbols(hiddenSymbols.reverse, tp1)
        else { // privates
          val badSymbol = hiddenSymbols.head
          error(tree.pos,
                (if (badSymbol hasFlag PRIVATE) "private " else "") + badSymbol +
                " escapes its defining scope as part of type "+tree.tpe)
          setError(tree)
        }
      }

      def addHidden(sym: Symbol) =
        if (!(hiddenSymbols contains sym)) hiddenSymbols = sym :: hiddenSymbols

      override def apply(t: Type): Type = {
        def checkNoEscape(sym: Symbol) {
          if (sym.hasFlag(PRIVATE)) {
            var o = owner
            while (o != NoSymbol && o != sym.owner && 
                   !o.isLocal && !o.hasFlag(PRIVATE) &&
                   !o.privateWithin.hasTransOwner(sym.owner))
              o = o.owner
            if (o == sym.owner) addHidden(sym)
          } else if (sym.owner.isTerm && !sym.isTypeParameterOrSkolem) {
            var e = scope.lookupEntryWithContext(sym.name)(context.owner)
            var found = false
            while (!found && (e ne null) && e.owner == scope) {
              if (e.sym == sym) {
                found = true
                addHidden(sym)
              } else {
                e = scope.lookupNextEntry(e)
              }
            }
          }
        }
        mapOver(
          t match {
            case TypeRef(_, sym, args) => 
              checkNoEscape(sym)
              if (!hiddenSymbols.isEmpty && hiddenSymbols.head == sym && 
                  sym.isAliasType && sym.typeParams.length == args.length) {
                hiddenSymbols = hiddenSymbols.tail
                t.normalize
              } else t
            case SingleType(_, sym) => 
              checkNoEscape(sym)
              t
            case _ =>
              t
          })
      }
    }

    def reenterValueParams(vparamss: List[List[ValDef]]) {
      for (vparams <- vparamss)
        for (vparam <- vparams)
          vparam.symbol = context.scope enter vparam.symbol
    }

    def reenterTypeParams(tparams: List[TypeDef]): List[Symbol] =
      for (tparam <- tparams) yield {
        tparam.symbol = context.scope enter tparam.symbol
        tparam.symbol.deSkolemize 
      } 

    /** The qualifying class of a this or super with prefix <code>qual</code>.
     *
     *  @param tree ...
     *  @param qual ...
     *  @return     ...
     */
    def qualifyingClassContext(tree: Tree, qual: Name): Context = {
      if (qual.isEmpty) {
        if (context.enclClass.owner.isPackageClass) 
          error(tree.pos, tree+" can be used only in a class, object, or template")
        context.enclClass
      } else {
        var c = context.enclClass
        while (c != NoContext && c.owner.name != qual) c = c.outer.enclClass
        if (c == NoContext) error(tree.pos, qual+" is not an enclosing class")
        c
      }
    }

    /** The typer for an expression, depending on where we are. If we are before a superclass 
     *  call, this is a typer over a constructor context; otherwise it is the current typer.
     */  
    def constrTyperIf(inConstr: Boolean): Typer =  
      if (inConstr) newTyper(context.makeConstructorContext) else this

    /** The typer for a label definition. If this is part of a template we
     *  first have to enter the label definition.
     */
    def labelTyper(ldef: LabelDef): Typer = 
      if (ldef.symbol == NoSymbol) { // labeldef is part of template
        val typer1 = newTyper(context.makeNewScope(ldef, context.owner)(LabelScopeKind))
        typer1.enterLabelDef(ldef)
        typer1
      } else this

    final val xtypes = false

    /** Does the context of tree <code>tree</code> require a stable type?
     */
    private def isStableContext(tree: Tree, mode: Int, pt: Type) =  
      isNarrowable(tree.tpe) && ((mode & (EXPRmode | LHSmode)) == EXPRmode) && 
      (xtypes ||
      (pt.isStable ||
       (mode & QUALmode) != 0 && !tree.symbol.isConstant ||
       pt.typeSymbol.isAbstractType && pt.bounds.lo.isStable && !(tree.tpe <:< pt)))

    /** <p>
     *    Post-process an identifier or selection node, performing the following:
     *  </p>
     *  <ol>
     *  <!--(1)--><li>Check that non-function pattern expressions are stable</li>
     *  <!--(2)--><li>Check that packages and static modules are not used as values</li>
     *  <!--(3)--><li>Turn tree type into stable type if possible and required by context.</li>
     *  </ol>
     */
    private def stabilize(tree: Tree, pre: Type, mode: Int, pt: Type): Tree = {
      if (tree.symbol.hasFlag(OVERLOADED) && (mode & FUNmode) == 0)
        inferExprAlternative(tree, pt)
      val sym = tree.symbol
      if (tree.tpe.isError) tree
      else if ((mode & (PATTERNmode | FUNmode)) == PATTERNmode && tree.isTerm) { // (1)
        checkStable(tree)
      } else if ((mode & (EXPRmode | QUALmode)) == EXPRmode && !sym.isValue && !phase.erasedTypes) { // (2)
        errorTree(tree, sym+" is not a value")
      } else {
        if (sym.isStable && pre.isStable && tree.tpe.typeSymbol != ByNameParamClass &&
            (isStableContext(tree, mode, pt) || sym.isModule && !sym.isMethod))
          tree.setType(singleType(pre, sym))
        else tree
      }
    }

    private def isNarrowable(tpe: Type): Boolean = tpe match {
      case TypeRef(_, _, _) | RefinedType(_, _) => true
      case ExistentialType(_, tpe1) => isNarrowable(tpe1)
      case AnnotatedType(_, tpe1, _) => isNarrowable(tpe1)
      case PolyType(_, tpe1) => isNarrowable(tpe1)
      case _ => !phase.erasedTypes
    } 

    private def stabilizedType(tree: Tree): Type = tree.tpe
/*{
      val sym = tree.symbol
      val res = tree match {
        case Ident(_) if (sym.isStable) =>
          val pre = if (sym.owner.isClass) sym.owner.thisType else NoPrefix 
          singleType(pre, sym)
        case Select(qual, _) if (qual.tpe.isStable && sym.isStable) =>
          singleType(qual.tpe, sym)
        case _ =>
          tree.tpe
      }
      res
    }
*/
    /**
     *  @param tree ...
     *  @param mode ...
     *  @param pt   ...
     *  @return     ...
     */
    def stabilizeFun(tree: Tree, mode: Int, pt: Type): Tree = {
      val sym = tree.symbol
      val pre = tree match {
        case Select(qual, _) => qual.tpe
        case _ => NoPrefix
      }
      if (tree.tpe.isInstanceOf[MethodType] && pre.isStable && sym.tpe.paramTypes.isEmpty &&
          (isStableContext(tree, mode, pt) || sym.isModule))
        tree.setType(MethodType(List(), singleType(pre, sym)))
      else tree
    }

    /** The member with given name of given qualifier tree */
    def member(qual: Tree, name: Name)(from : Symbol) = qual.tpe match {
      case ThisType(clazz) if (context.enclClass.owner.hasTransOwner(clazz)) =>
        qual.tpe.member(name)
      case _  =>
        if (phase.next.erasedTypes) qual.tpe.member(name)
        else qual.tpe.nonLocalMember(name)(from)
    }      

    def silent(op: Typer => Tree): AnyRef /* in fact, TypeError or Tree */ = try {
      if (context.reportGeneralErrors) {
        val context1 = context.makeSilent(context.reportAmbiguousErrors)
        context1.undetparams = context.undetparams
        context1.savedTypeBounds = context.savedTypeBounds
        val typer1 = newTyper(context1)
        val result = op(typer1)
        context.undetparams = context1.undetparams
        context.savedTypeBounds = context1.savedTypeBounds
        result
      } else {
        op(this)
      }
    } catch {
      case ex: CyclicReference => throw ex
      case ex: TypeError => ex
    }

    /** Perform the following adaptations of expression, pattern or type `tree' wrt to 
     *  given mode `mode' and given prototype `pt':
     *  (0) Convert expressions with constant types to literals
     *  (1) Resolve overloading, unless mode contains FUNmode 
     *  (2) Apply parameterless functions
     *  (3) Apply polymorphic types to fresh instances of their type parameters and
     *      store these instances in context.undetparams, 
     *      unless followed by explicit type application.
     *  (4) Do the following to unapplied methods used as values:
     *  (4.1) If the method has only implicit parameters pass implicit arguments
     *  (4.2) otherwise, if `pt' is a function type and method is not a constructor,
     *        convert to function by eta-expansion,
     *  (4.3) otherwise, if the method is nullary with a result type compatible to `pt'
     *        and it is not a constructor, apply it to ()
     *  otherwise issue an error
     *  (5) Convert constructors in a pattern as follows:
     *  (5.1) If constructor refers to a case class factory, set tree's type to the unique
     *        instance of its primary constructor that is a subtype of the expected type.
     *  (5.2) If constructor refers to an exractor, convert to application of
     *        unapply or unapplySeq method.
     *
     *  (6) Convert all other types to TypeTree nodes.
     *  (7) When in TYPEmode but not FUNmode or HKmode, check that types are fully parameterized
     *      (7.1) In HKmode, higher-kinded types are allowed, but they must have the expected kind-arity
     *  (8) When in both EXPRmode and FUNmode, add apply method calls to values of object type.
     *  (9) If there are undetermined type variables and not POLYmode, infer expression instance
     *  Then, if tree's type is not a subtype of expected type, try the following adaptations:
     *  (10) If the expected type is Byte, Short or Char, and the expression
     *      is an integer fitting in the range of that type, convert it to that type. 
     *  (11) Widen numeric literals to their expected type, if necessary
     *  (12) When in mode EXPRmode, convert E to { E; () } if expected type is scala.Unit.
     *  (13) When in mode EXPRmode, apply a view
     *  If all this fails, error
     */
    protected def adapt(tree: Tree, mode: Int, pt: Type): Tree = tree.tpe match {
      case ct @ ConstantType(value) if ((mode & (TYPEmode | FUNmode)) == 0 && (ct <:< pt) && !inIDE) => // (0)
        copy.Literal(tree, value)
      case OverloadedType(pre, alts) if ((mode & FUNmode) == 0) => // (1)
        inferExprAlternative(tree, pt)
        adapt(tree, mode, pt)
      case PolyType(List(), restpe) => // (2)
        adapt(tree setType restpe, mode, pt)
      case TypeRef(_, sym, List(arg))
      if ((mode & EXPRmode) != 0 && sym == ByNameParamClass) => // (2)
        adapt(tree setType arg, mode, pt)
      case tr @ TypeRef(_, sym, _) 
      if sym.isAliasType && tr.normalize.isInstanceOf[ExistentialType] &&
        ((mode & (EXPRmode | LHSmode)) == EXPRmode) =>
        adapt(tree setType tr.normalize.skolemizeExistential(context.owner, tree), mode, pt)
      case et @ ExistentialType(_, _) if ((mode & (EXPRmode | LHSmode)) == EXPRmode) =>
        adapt(tree setType et.skolemizeExistential(context.owner, tree), mode, pt)
      case PolyType(tparams, restpe) if ((mode & (TAPPmode | PATTERNmode)) == 0) => // (3)
        assert((mode & HKmode) == 0) //@M
        val tparams1 = cloneSymbols(tparams)
        val tree1 = if (tree.isType) tree 
                    else TypeApply(tree, tparams1 map (tparam => 
                      TypeTree(tparam.tpe) setOriginal tree)) setPos tree.pos
        context.undetparams = context.undetparams ::: tparams1
        adapt(tree1 setType restpe.substSym(tparams, tparams1), mode, pt)
      case mt: ImplicitMethodType if ((mode & (EXPRmode | FUNmode | LHSmode)) == EXPRmode) => // (4.1)
        if (!context.undetparams.isEmpty & (mode & POLYmode) == 0) { // (9)
          val tparams = context.undetparams
          context.undetparams = List()
          inferExprInstance(tree, tparams, pt)
          adapt(tree, mode, pt)
        } else {
          val typer1 = constrTyperIf(treeInfo.isSelfOrSuperConstrCall(tree))
          typer1.typed(typer1.applyImplicitArgs(tree), mode, pt)
        }
      case mt: MethodType
      if (((mode & (EXPRmode | FUNmode | LHSmode)) == EXPRmode) && 
          (context.undetparams.isEmpty || (mode & POLYmode) != 0)) =>
        val meth = tree.symbol
        if (!meth.isConstructor && 
            //isCompatible(tparamsToWildcards(mt, context.undetparams), pt) &&
            isFunctionType(pt))/* &&
            (pt <:< functionType(mt.paramTypes map (t => WildcardType), WildcardType)))*/ { // (4.2)
          if (settings.debug.value) log("eta-expanding "+tree+":"+tree.tpe+" to "+pt)
          checkParamsConvertible(tree.pos, tree.tpe)
          val tree1 = etaExpand(context.unit, tree)
//          println("eta "+tree+" ---> "+tree1+":"+tree1.tpe)
          typed(tree1, mode, pt)
        } else if (!meth.isConstructor && mt.paramTypes.isEmpty) { // (4.3)
          adapt(typed(Apply(tree, List()) setPos tree.pos), mode, pt)
        } else if (context.implicitsEnabled) {
          errorTree(tree, "missing arguments for "+meth+meth.locationString+
                    (if (meth.isConstructor) ""
                     else ";\nfollow this method with `_' if you want to treat it as a partially applied function"))
        } else {
          setError(tree)
        }
      case _ =>
        def applyPossible = {
          def applyMeth = member(adaptToName(tree, nme.apply), nme.apply)(context.owner)
          if ((mode & TAPPmode) != 0)
            tree.tpe.typeParams.isEmpty && applyMeth.filter(! _.tpe.typeParams.isEmpty) != NoSymbol
          else 
            applyMeth.filter(_.tpe.paramSectionCount > 0) != NoSymbol
        }
        if (tree.isType) {
          if ((mode & FUNmode) != 0) {
            tree
          } else if (tree.hasSymbol && !tree.symbol.typeParams.isEmpty && (mode & HKmode) == 0 &&
                     !(tree.symbol.hasFlag(JAVA) && context.unit.isJava)) { // (7) 
            // @M When not typing a higher-kinded type ((mode & HKmode) == 0) 
            // or raw type (tree.symbol.hasFlag(JAVA) && context.unit.isJava), types must be of kind *, 
            // and thus parameterised types must be applied to their type arguments
            // @M TODO: why do kind-* tree's have symbols, while higher-kinded ones don't?
            errorTree(tree, tree.symbol+" takes type parameters")
            tree setType tree.tpe
          } else if ( // (7.1) @M: check kind-arity 
                    // @M: removed check for tree.hasSymbol and replace tree.symbol by tree.tpe.symbol (TypeTree's must also be checked here, and they don't directly have a symbol)
                     ((mode & HKmode) != 0) && 
                    // @M: don't check tree.tpe.symbol.typeParams. check tree.tpe.typeParams!!! 
                    // (e.g., m[Int] --> tree.tpe.symbol.typeParams.length == 1, tree.tpe.typeParams.length == 0!)
                     tree.tpe.typeParams.length != pt.typeParams.length && 
                     !(tree.tpe.typeSymbol==AnyClass || 
                       tree.tpe.typeSymbol==NothingClass || 
                       pt == WildcardType )) {
              // Check that the actual kind arity (tree.symbol.typeParams.length) conforms to the expected
              // kind-arity (pt.typeParams.length). Full checks are done in checkKindBounds in Infer.
              // Note that we treat Any and Nothing as kind-polymorphic. 
              // We can't perform this check when typing type arguments to an overloaded method before the overload is resolved 
              // (or in the case of an error type) -- this is indicated by pt == WildcardType (see case TypeApply in typed1).
              errorTree(tree, tree.tpe+" takes "+reporter.countElementsAsString(tree.tpe.typeParams.length, "type parameter")+
                              ", expected: "+reporter.countAsString(pt.typeParams.length))
              tree setType tree.tpe
          } else tree match { // (6)
            case TypeTree() => tree
            case _ => TypeTree(tree.tpe) setOriginal(tree)
          }
        } else if ((mode & (PATTERNmode | FUNmode)) == (PATTERNmode | FUNmode)) { // (5)
          val extractor = tree.symbol.filter(sym => unapplyMember(sym.tpe).exists)
          if (extractor != NoSymbol) {
            tree setSymbol extractor
            val unapply = unapplyMember(extractor.tpe)
            val clazz = if (unapply.tpe.paramTypes.length == 1) unapply.tpe.paramTypes.head.typeSymbol 
                        else NoSymbol
            if ((unapply hasFlag CASE) && (clazz hasFlag CASE) && 
                !(clazz.info.baseClasses.tail exists (_ hasFlag CASE))) {
              if (!phase.erasedTypes) checkStable(tree) //todo: do we need to demand this?
              // convert synthetic unapply of case class to case class constructor
              val prefix = tree.tpe.prefix
              val tree1 = TypeTree(clazz.primaryConstructor.tpe.asSeenFrom(prefix, clazz.owner))
                  .setOriginal(tree)
              try {
                inferConstructorInstance(tree1, clazz.typeParams, pt)
              } catch {
                case tpe : TypeError => throw tpe
                case t : Exception =>
                  logError("CONTEXT: " + (tree.pos).dbgString, t)
                  throw t
              }
              tree1
            } else {
              tree
            }
          } else {
            errorTree(tree, tree.symbol + " is not a case class constructor, nor does it have an unapply/unapplySeq method")
          }
        } else if ((mode & (EXPRmode | FUNmode)) == (EXPRmode | FUNmode) && 
                   !tree.tpe.isInstanceOf[MethodType] && 
                   !tree.tpe.isInstanceOf[OverloadedType] && 
                   applyPossible) {
          assert((mode & HKmode) == 0) //@M
          val qual = adaptToName(tree, nme.apply) match {
            case id @ Ident(_) =>
              val pre = if (id.symbol.owner.isPackageClass) id.symbol.owner.thisType
                        else if (id.symbol.owner.isClass) 
                          context.enclosingSubClassContext(id.symbol.owner).prefix
                        else NoPrefix
              stabilize(id, pre, EXPRmode | QUALmode, WildcardType)
            case sel @ Select(qualqual, _) => 
              stabilize(sel, qualqual.tpe, EXPRmode | QUALmode, WildcardType)
            case other => 
              other
          }
          typed(atPos(tree.pos)(Select(qual, nme.apply)), mode, pt)
        } else if (!context.undetparams.isEmpty && (mode & POLYmode) == 0) { // (9)
          assert((mode & HKmode) == 0) //@M
          instantiate(tree, mode, pt)
        } else if (tree.tpe <:< pt) {
          def isStructuralType(tpe: Type): Boolean = tpe match {
            case RefinedType(ps, decls) =>
              decls.toList exists (x => x.isTerm && x.allOverriddenSymbols.isEmpty)
            case _ =>
              false
          }
          if (isStructuralType(pt) && tree.tpe.typeSymbol == ArrayClass) {
            // all Arrays used as structural refinement typed values must be boxed
            // this does not solve the case where the type to be adapted to comes
            // from a type variable that was bound by a strctural but is instantiated
            typed(Apply(Select(gen.mkAttributedRef(ScalaRunTimeModule), nme.forceBoxedArray), List(tree)))
          }
          else
            tree
        } else {
          if ((mode & PATTERNmode) != 0) {
            if ((tree.symbol ne null) && tree.symbol.isModule)
              inferModulePattern(tree, pt)
            if (isPopulated(tree.tpe, approximateAbstracts(pt)))
              return tree
          }
          val tree1 = constfold(tree, pt) // (10) (11)
          if (tree1.tpe <:< pt) adapt(tree1, mode, pt)
          else {
            if ((mode & (EXPRmode | FUNmode)) == EXPRmode) {
              pt.normalize match {
                case TypeRef(_, sym, _) =>
                  // note: was if (pt.typeSymbol == UnitClass) but this leads to a potentially
                  // infinite expansion if pt is constant type ()
                  if (sym == UnitClass && tree.tpe <:< AnyClass.tpe) // (12)
                    return typed(atPos(tree.pos)(Block(List(tree), Literal(()))), mode, pt)
                case _ =>
              }
              if (!context.undetparams.isEmpty) {
                return instantiate(tree, mode, pt)
              }
              if (context.implicitsEnabled && !tree.tpe.isError && !pt.isError) { 
                // (13); the condition prevents chains of views 
                if (settings.debug.value) log("inferring view from "+tree.tpe+" to "+pt)
                val coercion = inferView(tree.pos, tree.tpe, pt, true)
                // convert forward views of delegate types into closures wrapped around
                // the delegate's apply method (the "Invoke" method, which was translated into apply)
                if (forMSIL && coercion != null && isCorrespondingDelegate(tree.tpe, pt)) {
                  val meth: Symbol = tree.tpe.member(nme.apply)
                  if(settings.debug.value)
                    log("replacing forward delegate view with: " + meth + ":" + meth.tpe)
                  return typed(Select(tree, meth), mode, pt)
                }
                if (coercion != EmptyTree) {
                  if (settings.debug.value) log("inferred view from "+tree.tpe+" to "+pt+" = "+coercion+":"+coercion.tpe)
                  return newTyper(context.makeImplicit(context.reportAmbiguousErrors)).typed(
                      Apply(coercion, List(tree)) setPos tree.pos, mode, pt)
                }
              }
            }
            if (settings.debug.value) {
              log("error tree = "+tree)
              if (settings.explaintypes.value) explainTypes(tree.tpe, pt)
            }
            typeErrorTree(tree, tree.tpe, pt)
          }
        }
    }

    /**
     *  @param tree ...
     *  @param mode ...
     *  @param pt   ...
     *  @return     ...
     */
    def instantiate(tree: Tree, mode: Int, pt: Type): Tree = {
      val tparams = context.undetparams
      context.undetparams = List()
      inferExprInstance(tree, tparams, pt)
      adapt(tree, mode, pt)
    }

    /**
     *  @param qual ...
     *  @param name ...
     *  @param tp   ...
     *  @return     ...
     */
    def adaptToMember(qual: Tree, name: Name, tp: Type): Tree = {
      val qtpe = qual.tpe.widen
      if (qual.isTerm && 
          ((qual.symbol eq null) || !qual.symbol.isTerm || qual.symbol.isValue) &&
          phase.id <= currentRun.typerPhase.id && !qtpe.isError && !tp.isError &&
          qtpe.typeSymbol != NullClass && qtpe.typeSymbol != NothingClass && qtpe != WildcardType) {
        val coercion = inferView(qual.pos, qtpe, name, tp, true)
        if (coercion != EmptyTree) 
          typedQualifier(atPos(qual.pos)(Apply(coercion, List(qual))))
        else qual
      } else qual
    }

    def adaptToName(qual: Tree, name: Name) =
      if (member(qual, name)(context.owner) != NoSymbol) qual
      else adaptToMember(qual, name, WildcardType)

    private def typePrimaryConstrBody(clazz : Symbol, cbody: Tree, tparams: List[Symbol], enclTparams: List[Symbol], vparamss: List[List[ValDef]]): Tree = {
      // XXX: see about using the class's symbol....
      enclTparams foreach (sym => context.scope.enter(sym))
      namer.enterValueParams(context.owner, vparamss)
      typed(cbody)
    }

    def parentTypes(templ: Template): List[Tree] = 
      if (templ.parents.isEmpty) List()
      else try {
        val clazz = context.owner

        // Normalize supertype and mixins so that supertype is always a class, not a trait.
        var supertpt = typedTypeConstructor(templ.parents.head)
        val firstParent = supertpt.tpe.typeSymbol
        var mixins = templ.parents.tail map typedType
        // If first parent is a trait, make it first mixin and add its superclass as first parent 
        while ((supertpt.tpe.typeSymbol ne null) && supertpt.tpe.typeSymbol.initialize.isTrait) {
          val supertpt1 = typedType(supertpt)
          if (!supertpt1.tpe.isError) {
            mixins = supertpt1 :: mixins
            supertpt = TypeTree(supertpt1.tpe.parents.head) setOriginal supertpt /* setPos supertpt.pos */
          }
        }

        // Determine 
        //  - supertparams: Missing type parameters from supertype
        //  - supertpe: Given supertype, polymorphic in supertparams
        val supertparams = if (supertpt.hasSymbol) supertpt.symbol.typeParams else List()
        var supertpe = supertpt.tpe
        if (!supertparams.isEmpty)
          supertpe = PolyType(supertparams, appliedType(supertpe, supertparams map (_.tpe)))

        // A method to replace a super reference by a New in a supercall
        def transformSuperCall(scall: Tree): Tree = (scall: @unchecked) match {
          case Apply(fn, args) =>
            copy.Apply(scall, transformSuperCall(fn), args map (_.duplicate))
          case Select(Super(_, _), nme.CONSTRUCTOR) =>
            copy.Select(
              scall, 
              New(TypeTree(supertpe) setOriginal supertpt) setType supertpe setPos supertpt.pos,
              nme.CONSTRUCTOR)
        }

        treeInfo.firstConstructor(templ.body) match {
          case constr @ DefDef(_, _, _, vparamss, _, cbody @ Block(cstats, cunit)) =>
            // Convert constructor body to block in environment and typecheck it
            val cstats1: List[Tree] = cstats map (_.duplicate)
            val scall = if (cstats.isEmpty) EmptyTree else cstats.last
            val cbody1 = scall match {
              case Apply(_, _) =>
                copy.Block(cbody, cstats1.init, 
                           if (supertparams.isEmpty) cunit.duplicate 
                           else transformSuperCall(scall))
              case _ =>
                copy.Block(cbody, cstats1, cunit.duplicate)
            }

            val outercontext = context.outer 
            assert(clazz != NoSymbol)
            val cscope = outercontext.makeNewScope(constr, outercontext.owner)(ParentTypesScopeKind(clazz))
            val cbody2 = newTyper(cscope) // called both during completion AND typing.
                .typePrimaryConstrBody(clazz,  
                  cbody1, supertparams, clazz.unsafeTypeParams, vparamss map (_.map(_.duplicate)))

            scall match {
              case Apply(_, _) =>
                val sarg = treeInfo.firstArgument(scall)
                if (sarg != EmptyTree && supertpe.typeSymbol != firstParent) 
                  error(sarg.pos, firstParent+" is a trait; does not take constructor arguments")
                if (!supertparams.isEmpty) supertpt = TypeTree(cbody2.tpe) setPos supertpt.pos
              case _ =>
                if (!supertparams.isEmpty) error(supertpt.pos, "missing type arguments")
            }

            List.map2(cstats1, treeInfo.preSuperFields(templ.body)) {
              (ldef, gdef) => gdef.tpt.tpe = ldef.symbol.tpe
            }
          case _ =>
            if (!supertparams.isEmpty) error(supertpt.pos, "missing type arguments")
        }

        //Console.println("parents("+clazz") = "+supertpt :: mixins);//DEBUG
        List.mapConserve(supertpt :: mixins)(tpt => checkNoEscaping.privates(clazz, tpt))
      } catch {
        case ex: TypeError =>
          templ.tpe = null
          reportTypeError(templ.pos, ex)
          List(TypeTree(AnyRefClass.tpe))
      }

    /** <p>Check that</p>
     *  <ul>
     *    <li>all parents are class types,</li>
     *    <li>first parent class is not a mixin; following classes are mixins,</li>
     *    <li>final classes are not inherited,</li>
     *    <li>
     *      sealed classes are only inherited by classes which are
     *      nested within definition of base class, or that occur within same
     *      statement sequence,
     *    </li>
     *    <li>self-type of current class is a subtype of self-type of each parent class.</li>
     *    <li>no two parents define same symbol.</li>
     *  </ul>
     */
    def validateParentClasses(parents: List[Tree], selfType: Type) {

      def validateParentClass(parent: Tree, superclazz: Symbol) {
        if (!parent.tpe.isError) {
          val psym = parent.tpe.typeSymbol.initialize
          checkClassType(parent, false)
          if (psym != superclazz) {
            if (psym.isTrait) {
              val ps = psym.info.parents
              if (!ps.isEmpty && !superclazz.isSubClass(ps.head.typeSymbol))
                error(parent.pos, "illegal inheritance; super"+superclazz+
                      "\n is not a subclass of the super"+ps.head.typeSymbol+
                      "\n of the mixin " + psym);
            } else {
              error(parent.pos, psym+" needs to be a trait be mixed in")
            }
          } 
          if (psym hasFlag FINAL) {
            error(parent.pos, "illegal inheritance from final "+psym)
          } 
          if (psym.isSealed && !phase.erasedTypes) {
            if (context.unit.source.file != psym.sourceFile)
              error(parent.pos, "illegal inheritance from sealed "+psym)
            else
              psym addChild context.owner
          }
          if (!(selfType <:< parent.tpe.typeOfThis) && 
              !phase.erasedTypes &&     
              !(context.owner hasFlag SYNTHETIC)) // don't do this check for synthetic concrete classes for virtuals (part of DEVIRTUALIZE)
          { 
            //Console.println(context.owner);//DEBUG
            //Console.println(context.owner.unsafeTypeParams);//DEBUG
            //Console.println(List.fromArray(context.owner.info.closure));//DEBUG
            // disable in IDE, don't know how else to avoid it on refresh
            if (!inIDE) error(parent.pos, "illegal inheritance;\n self-type "+
                  selfType+" does not conform to "+parent +
                  "'s selftype "+parent.tpe.typeOfThis)
            if (settings.explaintypes.value) explainTypes(selfType, parent.tpe.typeOfThis)
          }
          if (parents exists (p => p != parent && p.tpe.typeSymbol == psym && !psym.isError))
            error(parent.pos, psym+" is inherited twice")
        }
      }

      if (!parents.isEmpty && !parents.head.tpe.isError)
        for (p <- parents) validateParentClass(p, parents.head.tpe.typeSymbol)
    }

    def checkFinitary(classinfo: ClassInfoType) {
      val clazz = classinfo.typeSymbol
      for (tparam <- clazz.typeParams) {
        if (classinfo.expansiveRefs(tparam) contains tparam) {
          error(tparam.pos, "class graph is not finitary because type parameter "+tparam.name+" is expansively recursive")
          val newinfo = ClassInfoType(
            classinfo.parents map (_.instantiateTypeParams(List(tparam), List(AnyRefClass.tpe))), 
            classinfo.decls, 
            clazz)
          clazz.setInfo {
            clazz.info match {
              case PolyType(tparams, _) => PolyType(tparams, newinfo)
              case _ => newinfo
            }
          }
        }
      }
    }

    /**
     *  @param cdef ...
     *  @return     ...
     */
    def typedClassDef(cdef: ClassDef): Tree = {
//      attributes(cdef)
      val typedMods = typedModifiers(cdef.mods)
      val clazz = cdef.symbol; 
      if (inIDE && clazz == NoSymbol) {
        throw new TypeError("type signature typing failed")
      }
      assert(clazz != NoSymbol)
      reenterTypeParams(cdef.tparams)
      val tparams1 = List.mapConserve(cdef.tparams)(typedTypeDef)
      val impl1 = newTyper(context.make(cdef.impl, clazz, scopeFor(cdef.impl, TypedDefScopeKind)))
        .typedTemplate(cdef.impl, parentTypes(cdef.impl))
      val impl2 = addSyntheticMethods(impl1, clazz, context)
      if ((clazz != ClassfileAnnotationClass) &&
	  (clazz isNonBottomSubClass ClassfileAnnotationClass))
	unit.warning (cdef.pos,
          "implementation restriction: subclassing Classfile does not\n"+
          "make your annotation visible at runtime.  If that is what\n"+ 
	  "you want, you must write the annotation class in Java.")
      copy.ClassDef(cdef, typedMods, cdef.name, tparams1, impl2)
        .setType(NoType)
    }
 
    /**
     *  @param mdef ...
     *  @return     ...
     */
    def typedModuleDef(mdef: ModuleDef): Tree = {
      //Console.println("sourcefile of " + mdef.symbol + "=" + mdef.symbol.sourceFile)
//      attributes(mdef)
      val typedMods = typedModifiers(mdef.mods)
      val clazz = mdef.symbol.moduleClass
      if (inIDE && clazz == NoSymbol) throw new TypeError("bad signature")
      assert(clazz != NoSymbol)
      val impl1 = newTyper(context.make(mdef.impl, clazz, scopeFor(mdef.impl, TypedDefScopeKind)))
        .typedTemplate(mdef.impl, parentTypes(mdef.impl))
      val impl2 = addSyntheticMethods(impl1, clazz, context)

      copy.ModuleDef(mdef, typedMods, mdef.name, impl2) setType NoType
    }

    /**
     *  @param stat ...
     *  @return     ...
     */
    def addGetterSetter(stat: Tree): List[Tree] = stat match {
      case ValDef(mods, name, tpt, rhs) 
        if (mods.flags & (PRIVATE | LOCAL)) != (PRIVATE | LOCAL)
          && !stat.symbol.isModuleVar
          && !stat.symbol.hasFlag(LAZY) =>
        val vdef = copy.ValDef(stat, mods | PRIVATE | LOCAL, nme.getterToLocal(name), tpt, rhs)
        val value = vdef.symbol
        val getter = if ((mods hasFlag DEFERRED)) value else value.getter(value.owner)
        // XXX:
        if (inIDE && getter == NoSymbol) 
          return Nil
        assert(getter != NoSymbol, stat)
        if (getter hasFlag OVERLOADED)
          error(getter.pos, getter+" is defined twice")
        val getterDef: DefDef = {
          getter.attributes = value.initialize.attributes
          val result = DefDef(getter, vparamss =>
              if (mods hasFlag DEFERRED) EmptyTree 
              else typed(
                atPos(vdef.pos) { gen.mkCheckInit(Select(This(value.owner), value)) }, 
                EXPRmode, value.tpe))
          result.tpt.asInstanceOf[TypeTree] setOriginal tpt /* setPos tpt.pos */
          checkNoEscaping.privates(getter, result.tpt)
          copy.DefDef(result, result.mods withAnnotations mods.annotations, result.name,
                      result.tparams, result.vparamss, result.tpt, result.rhs)
          //todo: withAnnotations is probably unnecessary
        }
        def setterDef: DefDef = {
          val setr = getter.setter(value.owner)
          setr.attributes = value.attributes
          val result = atPos(vdef.pos)(
            DefDef(setr, vparamss =>
              if ((mods hasFlag DEFERRED) || (setr hasFlag OVERLOADED)) 
                EmptyTree
              else 
                typed(Assign(Select(This(value.owner), value),
                             Ident(vparamss.head.head)))))
          copy.DefDef(result, result.mods withAnnotations mods.annotations, result.name,
                      result.tparams, result.vparamss, result.tpt, result.rhs)
        }
        val gs = if (mods hasFlag MUTABLE) List(getterDef, setterDef)
                 else List(getterDef)
        if (mods hasFlag DEFERRED) gs else vdef :: gs

      case DocDef(comment, defn) =>
        addGetterSetter(defn) map (stat => DocDef(comment, stat))

      case Annotated(annot, defn) =>
        addGetterSetter(defn) map (stat => Annotated(annot, stat))

      case _ =>
        List(stat)
    }

    protected def enterSyms(txt: Context, trees: List[Tree]) = {
      var txt0 = txt
      for (tree <- trees) txt0 = enterSym(txt0, tree)
    }

    protected def enterSym(txt: Context, tree: Tree): Context =
      if (txt eq context) namer.enterSym(tree)
      else newNamer(txt).enterSym(tree)

    /**
     *  @param templ    ...
     *  @param parents1 ...
     *    <li> <!-- 2 -->
     *      Check that inner classes do not inherit from Annotation
     *    </li>
     *  @return         ...
     */
    def typedTemplate(templ: Template, parents1: List[Tree]): Template = {
      val clazz = context.owner
      if (templ.symbol == NoSymbol)
        templ setSymbol newLocalDummy(clazz, templ.pos)
      val self1 = templ.self match {
        case vd @ ValDef(mods, name, tpt, EmptyTree) =>
          val tpt1 = checkNoEscaping.privates(clazz.thisSym, typedType(tpt))
          copy.ValDef(vd, mods, name, tpt1, EmptyTree) setType NoType
      }
      if (self1.name != nme.WILDCARD) context.scope enter self1.symbol
      val selfType =
        if (clazz.isAnonymousClass && !phase.erasedTypes) 
          intersectionType(clazz.info.parents, clazz.owner)
        else clazz.typeOfThis
      // the following is necessary for templates generated later
      assert(clazz.info.decls != EmptyScope)
      enterSyms(context.outer.make(templ, clazz, clazz.info.decls), templ.body)
      validateParentClasses(parents1, selfType)
      if ((clazz isSubClass ClassfileAnnotationClass) && !clazz.owner.isPackageClass)
        unit.error(clazz.pos, "inner classes cannot be classfile annotations")
      if (!phase.erasedTypes && !clazz.info.resultType.isError) // @S: prevent crash for duplicated type members
        checkFinitary(clazz.info.resultType.asInstanceOf[ClassInfoType])
      val body = 
        if (phase.id <= currentRun.typerPhase.id && !reporter.hasErrors) 
          templ.body flatMap addGetterSetter
        else templ.body 
      val body1 = typedStats(body, templ.symbol)
      copy.Template(templ, parents1, self1, body1) setType clazz.tpe
    }

    /** Type check the annotations within a set of modifiers.  */
    def typedModifiers(mods: Modifiers): Modifiers = {
      val Modifiers(flags, privateWithin, annotations) = mods
      val typedAnnots = annotations.map(typed(_).asInstanceOf[Annotation])
      Modifiers(flags, privateWithin, typedAnnots)
    }

    /**
     *  @param vdef ...
     *  @return     ...
     */
    def typedValDef(vdef: ValDef): ValDef = {
//      attributes(vdef)
      val sym = vdef.symbol
      val typer1 = constrTyperIf(sym.hasFlag(PARAM) && sym.owner.isConstructor)
      val typedMods = typedModifiers(vdef.mods)

      var tpt1 = checkNoEscaping.privates(sym, typer1.typedType(
        if (inIDE) vdef.tpt.duplicate // avoids wrong context sticking
        else vdef.tpt))
      checkNonCyclic(vdef, tpt1)
      val rhs1 =
        if (vdef.rhs.isEmpty) {
          if (sym.isVariable && sym.owner.isTerm && phase.id <= currentRun.typerPhase.id)
            error(vdef.pos, "local variables must be initialized")
          vdef.rhs
        } else {
          //assert(vdef.rhs.tpe == null)
          val rhs = if (inIDE && vdef.rhs.tpe != null) vdef.rhs.duplicate else vdef.rhs
          newTyper(typer1.context.make(vdef, sym)).transformedOrTyped(rhs, tpt1.tpe)
        }
      copy.ValDef(vdef, typedMods, vdef.name, tpt1, checkDead(rhs1)) setType NoType
    }

    /** Enter all aliases of local parameter accessors.
     *
     *  @param clazz    ...
     *  @param vparamss ...
     *  @param rhs      ...
     */
    def computeParamAliases(clazz: Symbol, vparamss: List[List[ValDef]], rhs: Tree) {
      if (settings.debug.value) log("computing param aliases for "+clazz+":"+clazz.primaryConstructor.tpe+":"+rhs);//debug
      def decompose(call: Tree): (Tree, List[Tree]) = call match {
        case Apply(fn, args) =>
          val (superConstr, args1) = decompose(fn)
          val formals = (if (fn.tpe == null && inIDE) ErrorType else fn.tpe).paramTypes
          val args2 = if (formals.isEmpty || formals.last.typeSymbol != RepeatedParamClass) args
                      else args.take(formals.length - 1) ::: List(EmptyTree)
          if (args2.length != formals.length) {
            if (!inIDE)          
              assert(false, "mismatch " + clazz + " " + formals + " " + args2);//debug
            else error(call.pos, "XXX: mismatch " + clazz + " " + formals + " " + args2)
          } 
          (superConstr, args1 ::: args2)
        case Block(stats, expr) if !stats.isEmpty =>
          decompose(stats.last)
        case _ =>
          (call, List())
      }
      val (superConstr, superArgs) = decompose(rhs)
      assert(superConstr.symbol ne null)//debug
      if (superConstr.symbol.isPrimaryConstructor) {
        val superClazz = superConstr.symbol.owner
        if (!superClazz.hasFlag(JAVA)) {
          val superParamAccessors = superClazz.constrParamAccessors
          if (superParamAccessors.length == superArgs.length) {
            List.map2(superParamAccessors, superArgs) { (superAcc, superArg) =>
              superArg match {
                case Ident(name) =>
                  if (vparamss.exists(_.exists(_.symbol == superArg.symbol))) {
                    var alias = superAcc.initialize.alias
                    if (alias == NoSymbol)
                      alias = superAcc.getter(superAcc.owner)
                    if (alias != NoSymbol &&
                        superClazz.info.nonPrivateMember(alias.name) != alias)
                      alias = NoSymbol
                    if (alias != NoSymbol) {
                      var ownAcc = clazz.info.decl(name).suchThat(_.hasFlag(PARAMACCESSOR))
                      if ((ownAcc hasFlag ACCESSOR) && !ownAcc.isDeferred)
                        ownAcc = ownAcc.accessed
                      if (!ownAcc.isVariable && !alias.accessed.isVariable) {
                        if (settings.debug.value)
                          log("" + ownAcc + " has alias "+alias + alias.locationString);//debug
                        ownAcc.asInstanceOf[TermSymbol].setAlias(alias)
                      }
                    }
                  }
                case _ =>
              }
              ()
            }
          } else if (inIDE) { // XXX: maybe add later
            Console.println("" + superClazz + ":" +
              superClazz.info.decls.toList.filter(_.hasFlag(PARAMACCESSOR)))
            error(rhs.pos, "mismatch: " + superParamAccessors +
              ";" + rhs + ";" + superClazz.info.decls)//debug
            return                          
          }
        }
      }
    }

    private def checkStructuralCondition(refinement: Symbol, vparam: ValDef) {
      val tp = vparam.symbol.tpe
      if (tp.typeSymbol.isAbstractType && !(tp.typeSymbol.hasTransOwner(refinement)))
        error(vparam.tpt.pos,"Parameter type in structural refinement may not refer to abstract type defined outside that same refinement")
    }

    /**
     *  @param ddef ...
     *  @return     ...
     */
    def typedDefDef(ddef: DefDef): DefDef = {
      val meth = ddef.symbol
      if (inIDE && meth == NoSymbol) throw new TypeError("bad signature")
      reenterTypeParams(ddef.tparams)
      reenterValueParams(ddef.vparamss)
      val tparams1 = List.mapConserve(ddef.tparams)(typedTypeDef)
      val vparamss1 = List.mapConserve(ddef.vparamss)(vparams1 =>
        List.mapConserve(vparams1)(typedValDef))
      for (vparams1 <- vparamss1; if !vparams1.isEmpty; vparam1 <- vparams1.init) {
        if (vparam1.symbol.tpe.typeSymbol == RepeatedParamClass)
          error(vparam1.pos, "*-parameter must come last")
      }
      var tpt1 = checkNoEscaping.privates(meth, typedType(ddef.tpt))           
      if (!settings.Xexperimental.value) {
        for (vparams <- vparamss1; vparam <- vparams) {
          checkNoEscaping.locals(context.scope, WildcardType, vparam.tpt); ()
        }
        checkNoEscaping.locals(context.scope, WildcardType, tpt1)
      }
      checkNonCyclic(ddef, tpt1)
      ddef.tpt.setType(tpt1.tpe)
      val typedMods = typedModifiers(ddef.mods)
      var rhs1 = 
        if (ddef.name == nme.CONSTRUCTOR) {
          if (!meth.isPrimaryConstructor &&
              (!meth.owner.isClass ||
               meth.owner.isModuleClass ||
               meth.owner.isAnonymousClass ||
               meth.owner.isRefinementClass))
            error(ddef.pos, "constructor definition not allowed here")
          typed(ddef.rhs)
        } else {
          if (inIDE && ddef.rhs == EmptyTree) EmptyTree
          else transformedOrTyped(ddef.rhs, tpt1.tpe)
        }
      if (meth.isPrimaryConstructor && meth.isClassConstructor && 
          phase.id <= currentRun.typerPhase.id && !reporter.hasErrors)
        computeParamAliases(meth.owner, vparamss1, rhs1)
      if (tpt1.tpe.typeSymbol != NothingClass && !context.returnsSeen) rhs1 = checkDead(rhs1)
      
      if (meth.owner.isRefinementClass && meth.allOverriddenSymbols.isEmpty)
        for (vparams <- ddef.vparamss; vparam <- vparams) 
          checkStructuralCondition(meth.owner, vparam)

      copy.DefDef(ddef, typedMods, ddef.name, tparams1, vparamss1, tpt1, rhs1) setType NoType
    }

    def typedTypeDef(tdef: TypeDef): TypeDef = {
      reenterTypeParams(tdef.tparams) // @M!
      val tparams1 = List.mapConserve(tdef.tparams)(typedTypeDef) // @M!
      val typedMods = typedModifiers(tdef.mods)
      val rhs1 = checkNoEscaping.privates(tdef.symbol, typedType(tdef.rhs))
      checkNonCyclic(tdef.symbol)
      rhs1.tpe match {
        case TypeBounds(lo1, hi1) =>
          if (!(lo1 <:< hi1))
            error(tdef.pos, "lower bound "+lo1+" does not conform to upper bound "+hi1)
        case _ =>
      }
      copy.TypeDef(tdef, typedMods, tdef.name, tparams1, rhs1) setType NoType
    }

    private def enterLabelDef(stat: Tree) {
      stat match {
        case ldef @ LabelDef(_, _, _) =>
          if (ldef.symbol == NoSymbol)
            ldef.symbol = namer.enterInScope(
              context.owner.newLabel(ldef.pos, ldef.name) setInfo MethodType(List(), UnitClass.tpe))
        case _ =>
      }
    }

    def typedLabelDef(ldef: LabelDef): LabelDef = {
      val restpe = ldef.symbol.tpe.resultType
      val rhs1 = typed(ldef.rhs, restpe)
      ldef.params foreach (param => param.tpe = param.symbol.tpe)
      copy.LabelDef(ldef, ldef.name, ldef.params, rhs1) setType restpe
    }

    protected def typedFunctionIDE(fun : Function, txt : Context) = {}
    
    /**
     *  @param block ...
     *  @param mode  ...
     *  @param pt    ...
     *  @return      ...
     */
    def typedBlock(block: Block, mode: Int, pt: Type): Block = {
      if (context.retyping) {
        for (stat <- block.stats) {
          if (stat.isDef) context.scope.enter(stat.symbol)
        }
      }
      namer.enterSyms(block.stats)
      block.stats foreach enterLabelDef
      val stats1 = typedStats(block.stats, context.owner)
      val expr1 = typed(block.expr, mode & ~(FUNmode | QUALmode), pt)
      val block1 = copy.Block(block, stats1, expr1)
        .setType(if (treeInfo.isPureExpr(block)) expr1.tpe else expr1.tpe.deconst)
      //checkNoEscaping.locals(context.scope, pt, block1)
      block1
    }

    /**
     *  @param cdef   ...
     *  @param pattpe ...
     *  @param pt     ...
     *  @return       ...
     */
    def typedCase(cdef: CaseDef, pattpe: Type, pt: Type): CaseDef = {
      val pat1: Tree = typedPattern(cdef.pat, pattpe)
      val guard1: Tree = if (cdef.guard == EmptyTree) EmptyTree
                         else typed(cdef.guard, BooleanClass.tpe)
      var body1: Tree = typed(cdef.body, pt)
      if (!context.savedTypeBounds.isEmpty) {
        body1.tpe = context.restoreTypeBounds(body1.tpe)
        if (isFullyDefined(pt) && !(body1.tpe <:< pt)) {
          body1 =
            typed {
              atPos(body1.pos) {
                TypeApply(Select(body1, Any_asInstanceOf), List(TypeTree(pt))) // @M no need for pt.normalize here, is done in erasure
              }
            }
        }
      }
//    body1 = checkNoEscaping.locals(context.scope, pt, body1)
      copy.CaseDef(cdef, pat1, guard1, body1) setType body1.tpe
    }

    def typedCases(tree: Tree, cases: List[CaseDef], pattp0: Type, pt: Type): List[CaseDef] = {
      var pattp = pattp0
      List.mapConserve(cases) ( cdef => 
        newTyper(context.makeNewScope(cdef, context.owner)(TypedCasesScopeKind))
          .typedCase(cdef, pattp, pt))
/* not yet!
        cdef.pat match {
          case Literal(Constant(null)) => 
            if (!(pattp <:< NonNullClass.tpe))
              pattp = intersectionType(List(pattp, NonNullClass.tpe), context.owner)
          case _ =>
        }
        result
*/
    }

    /**
     *  @param fun  ...
     *  @param mode ...
     *  @param pt   ...
     *  @return     ...
     */
    def typedFunction(fun: Function, mode: Int, pt: Type): Tree = {
      val codeExpected = !forCLDC && !forMSIL && (pt.typeSymbol isNonBottomSubClass CodeClass)

      def decompose(pt: Type): (Symbol, List[Type], Type) =
        if ((isFunctionType(pt)
             || 
             pt.typeSymbol == PartialFunctionClass && 
             fun.vparams.length == 1 && fun.body.isInstanceOf[Match]) 
             && // see bug901 for a reason why next conditions are neeed
            (pt.normalize.typeArgs.length - 1 == fun.vparams.length 
             || 
             fun.vparams.exists(_.tpt.isEmpty)))
          (pt.typeSymbol, pt.normalize.typeArgs.init, pt.normalize.typeArgs.last)
        else
          (FunctionClass(fun.vparams.length), fun.vparams map (x => NoType), WildcardType)

      val (clazz, argpts, respt) = decompose(if (codeExpected) pt.normalize.typeArgs.head else pt)

      if (fun.vparams.length != argpts.length)
        errorTree(fun, "wrong number of parameters; expected = " + argpts.length)
      else {
        val vparamSyms = List.map2(fun.vparams, argpts) { (vparam, argpt) =>
          if (vparam.tpt.isEmpty) {
            vparam.tpt.tpe = 
              if (isFullyDefined(argpt)) argpt
              else {
                fun match {
                  case etaExpansion(vparams, fn, args) if !codeExpected =>
                    silent(_.typed(fn, funMode(mode), pt)) match {
                      case fn1: Tree if context.undetparams.isEmpty =>
                        // if context,undetparams is not empty, the function was polymorphic, 
                        // so we need the missing arguments to infer its type. See #871
                        //println("typing eta "+fun+":"+fn1.tpe+"/"+context.undetparams)
                        val ftpe = normalize(fn1.tpe) baseType FunctionClass(fun.vparams.length)
                        if (isFunctionType(ftpe) && isFullyDefined(ftpe))
                          return typedFunction(fun, mode, ftpe)
                      case _ =>
                    }
                  case _ =>
                }
                error(
                  vparam.pos, 
                  "missing parameter type"+
                  (if (vparam.mods.hasFlag(SYNTHETIC)) " for expanded function "+fun
                   else ""))
                ErrorType 
              }
          }
          enterSym(context, vparam)
          if (context.retyping) context.scope enter vparam.symbol
          vparam.symbol
        }

        val vparams = List.mapConserve(fun.vparams)(typedValDef)
//        for (vparam <- vparams) {
//          checkNoEscaping.locals(context.scope, WildcardType, vparam.tpt); ()
//        }
        var body = typed(fun.body, respt)
        val formals = vparamSyms map (_.tpe)
        val restpe = packedType(body, fun.symbol).deconst
        val funtpe = typeRef(clazz.tpe.prefix, clazz, formals ::: List(restpe))
//        body = checkNoEscaping.locals(context.scope, restpe, body)
        val fun1 = copy.Function(fun, vparams, body).setType(funtpe)
        if (codeExpected) {
          val liftPoint = Apply(Select(Ident(CodeModule), nme.lift_), List(fun1))
          typed(atPos(fun.pos)(liftPoint))
        } else fun1
      }
    }

    def typedRefinement(stats: List[Tree]): List[Tree] = {
      namer.enterSyms(stats)
      val stats1 = typedStats(stats, NoSymbol)
      for (stat <- stats1 if stat.isDef) {
        val member = stat.symbol
        if (!(context.owner.info.baseClasses.tail forall
            (bc => member.matchingSymbol(bc, context.owner.thisType) == NoSymbol))) {
          member setFlag OVERRIDE
        }
      }
      stats1
    }

    def typedImport(imp : Import) : Import = imp

    def typedStats(stats: List[Tree], exprOwner: Symbol): List[Tree] = {

      val inBlock = exprOwner == context.owner

      def typedStat(stat: Tree): Tree = {
        if (context.owner.isRefinementClass && !treeInfo.isDeclaration(stat))
          errorTree(stat, "only declarations allowed here")
        stat match {
          case imp @ Import(_, _) =>
            val imp0 = typedImport(imp)
            if (imp0 ne null) {
              context = context.makeNewImport(imp0)
              imp0.symbol.initialize
            }
            if ((imp0 ne null) && inIDE) {
              imp0.symbol.info match {
              case ImportType(exr) => 
                imp0.expr.tpe = exr.tpe
              case _ =>
              }
              imp0
            } else EmptyTree
          case _ =>
            val localTyper = if (inBlock || (stat.isDef && !stat.isInstanceOf[LabelDef])) this
                             else newTyper(context.make(stat, exprOwner))
            val result = checkDead(localTyper.typed(stat))
            if (treeInfo.isSelfOrSuperConstrCall(result)) {
              context.inConstructorSuffix = true
              if (!inIDE && treeInfo.isSelfConstrCall(result) && result.symbol.pos.offset.getOrElse(0) >= exprOwner.enclMethod.pos.offset.getOrElse(0))
                error(stat.pos, "called constructor's definition must precede calling constructor's definition")
            }
            result
        }
      }

      def accesses(accessor: Symbol, accessed: Symbol) = 
        (accessed hasFlag LOCAL) && (accessed hasFlag PARAMACCESSOR) ||
        (accessor hasFlag ACCESSOR) &&
        !(accessed hasFlag ACCESSOR) && accessed.isPrivateLocal

      def checkNoDoubleDefsAndAddSynthetics(stats: List[Tree]): List[Tree] = {
        val scope = if (inBlock) context.scope else context.owner.info.decls;
        val newStats = new ListBuffer[Tree]
        var e = scope.elems;
        while ((e ne null) && e.owner == scope) {

          // check no double def
          var e1 = scope.lookupNextEntry(e);
          while ((e1 ne null) && e1.owner == scope) {
            if (!accesses(e.sym, e1.sym) && !accesses(e1.sym, e.sym) && 
                (e.sym.isType || inBlock || (e.sym.tpe matches e1.sym.tpe)))
              if (!e.sym.isErroneous && !e1.sym.isErroneous && !inIDE)
                error(e.sym.pos, e1.sym+" is defined twice"+
                      {if(!settings.debug.value) "" else " in "+unit.toString})
            e1 = scope.lookupNextEntry(e1);
          }

          // add synthetics
          context.unit.synthetics get e.sym match {
            case Some(tree) =>
              newStats += tree
              context.unit.synthetics -= e.sym
            case _ =>
          }

          e = e.next
        }
        if (newStats.isEmpty) stats 
        else stats ::: (newStats.toList map typedStat)
      }
      val result = List.mapConserve(stats)(typedStat)
      if (phase.erasedTypes) result
      else checkNoDoubleDefsAndAddSynthetics(result)
    }

    def typedArg(arg: Tree, mode: Int, newmode: Int, pt: Type): Tree =
      checkDead(constrTyperIf((mode & SCCmode) != 0).typed(arg, mode & stickyModes | newmode, pt))

    def typedArgs(args: List[Tree], mode: Int) =
      List.mapConserve(args)(arg => typedArg(arg, mode, 0, WildcardType))

    def typedArgs(args: List[Tree], mode: Int, originalFormals: List[Type], adaptedFormals: List[Type]) = {
      if (isVarArgs(originalFormals)) {
        val nonVarCount = originalFormals.length - 1
        val prefix =
          List.map2(args take nonVarCount, adaptedFormals take nonVarCount) ((arg, formal) =>
            typedArg(arg, mode, 0, formal))

        // if array is passed into java vararg and formal's element is not an array,
        // convert it to vararg by adding : _*
        // this is a gross hack to enable vararg transition; remove it as soon as possible.
        // !!!VARARG-CONVERSION!!!
        def hasArrayElement(tpe: Type) = 
          tpe.typeArgs.length == 1 && tpe.typeArgs.head.typeSymbol == ArrayClass
        var args0 = args 
        if ((mode & JAVACALLmode) != 0 && 
            (args.length == originalFormals.length) &&
            !hasArrayElement(adaptedFormals(nonVarCount)) &&
            !settings.XnoVarargsConversion.value) {
              val lastarg = typedArg(args(nonVarCount), mode, REGPATmode, WildcardType)
              if ((lastarg.tpe.typeSymbol == ArrayClass || lastarg.tpe.typeSymbol == NullClass) &&
                  !treeInfo.isWildcardStarArg(lastarg)) {
                if (lastarg.tpe.typeSymbol == ArrayClass)
                  unit.warning(
                    lastarg.pos,
                    "I'm seeing an array passed into a Java vararg.\n"+
                    "I assume that the elements of this array should be passed as individual arguments to the vararg.\n"+
                    "Therefore I follow the array with a `: _*', to mark it as a vararg argument.\n"+
                    "If that's not what you want, compile this file with option -Xno-varargs-conversion.")
                args0 = args.init ::: List(gen.wildcardStar(args.last))
              }
            }
        val suffix =
          List.map2(args0 drop nonVarCount, adaptedFormals drop nonVarCount) ((arg, formal) =>
            typedArg(arg, mode, REGPATmode, formal))
        prefix ::: suffix
      } else {
        List.map2(args, adaptedFormals)((arg, formal) => typedArg(arg, mode, 0, formal))
      }
    }

    /** Does function need to be instantiated, because a missing parameter
     *  in an argument closure overlaps with an uninstantiated formal?
     */
    def needsInstantiation(tparams: List[Symbol], formals: List[Type], args: List[Tree]) = {
      def isLowerBounded(tparam: Symbol) = {
        val losym = tparam.info.bounds.lo.typeSymbol
        losym != NothingClass && losym != NullClass
      }
      List.exists2(formals, args) { 
        case (formal, Function(vparams, _)) =>
          (vparams exists (_.tpt.isEmpty)) &&
          vparams.length <= MaxFunctionArity &&
          (formal baseType FunctionClass(vparams.length) match {
            case TypeRef(_, _, formalargs) =>
              List.exists2(formalargs, vparams) ((formalarg, vparam) =>
                vparam.tpt.isEmpty && (tparams exists (formalarg contains))) &&
              (tparams forall isLowerBounded)
            case _ =>
              false
          })
        case _ => 
          false
      }
    }

    /**
     *  @param tree ...
     *  @param fun0 ...
     *  @param args ...
     *  @param mode ...
     *  @param pt   ...
     *  @return     ...
     */
    def doTypedApply(tree: Tree, fun0: Tree, args: List[Tree], mode: Int, pt: Type): Tree = {
      var fun = fun0
      if (fun.hasSymbol && (fun.symbol hasFlag OVERLOADED)) {
        // preadapt symbol to number and shape of arguments given
        def shapeType(arg: Tree): Type = arg match {
          case Function(vparams, body) => 
            functionType(vparams map (vparam => AnyClass.tpe), shapeType(body))
          case _ =>
            NothingClass.tpe
        }
        val argtypes = args map shapeType
        val pre = fun.symbol.tpe.prefix
        var sym = fun.symbol filter { alt => 
          isApplicableSafe(context.undetparams, followApply(pre.memberType(alt)), argtypes, pt) 
        }
        //println("narrowed to "+sym+":"+sym.info+"/"+argtypes)
        if (sym hasFlag OVERLOADED) {
          // eliminate functions that would result from tupling transforms
          val sym1 = sym filter (alt => hasExactlyNumParams(followApply(alt.tpe), argtypes.length))
          if (sym1 != NoSymbol) sym = sym1
        }
        if (sym != NoSymbol)
          fun = adapt(fun setSymbol sym setType pre.memberType(sym), funMode(mode), WildcardType)
      }
      fun.tpe match {
        case OverloadedType(pre, alts) =>
          val undetparams = context.undetparams
          context.undetparams = List()
          val args1 = typedArgs(args, argMode(fun, mode))
          context.undetparams = undetparams
          inferMethodAlternative(fun, context.undetparams, args1 map (_.tpe.deconst), pt)
          doTypedApply(tree, adapt(fun, funMode(mode), WildcardType), args1, mode, pt)
        case mt @ MethodType(formals0, _) =>
          val formals = formalTypes(formals0, args.length)
          var args1 = actualArgs(tree.pos, args, formals.length)
          if (args1.length != args.length) {
            silent(_.doTypedApply(tree, fun, args1, mode, pt)) match {
              case t: Tree => t
              case ex => errorTree(tree, "wrong number of arguments for "+treeSymTypeMsg(fun))
            }
          } else if (formals.length != args1.length) {
            if (mt.isErroneous) setError(tree)
            else errorTree(tree, "wrong number of arguments for "+treeSymTypeMsg(fun))
          } else {
            val tparams = context.undetparams
            context.undetparams = List()
            if (tparams.isEmpty) {
              val args2 = typedArgs(args1, argMode(fun, mode), formals0, formals)
              val restpe = mt.resultType(args2 map (_.tpe))
              def ifPatternSkipFormals(tp: Type) = tp match {
                case MethodType(_, rtp) if ((mode & PATTERNmode) != 0) => rtp
                case _ => tp
              }

              // Replace the Delegate-Chainer methods += and -= with corresponding
              // + and - calls, which are translated in the code generator into
              // Combine and Remove
              if (forMSIL) {
                fun match {
                  case Select(qual, name) =>
                   if (isSubType(qual.tpe, DelegateClass.tpe)
                      && (name == encode("+=") || name == encode("-=")))
                     {
                       val n = if (name == encode("+=")) nme.PLUS else nme.MINUS
                       val f = Select(qual, n)
                       // the compiler thinks, the PLUS method takes only one argument,
                       // but he thinks it's an instance method -> still two ref's on the stack
                       //  -> translated by backend
                       val rhs = copy.Apply(tree, f, args1)
                       return typed(Assign(qual, rhs))
                     }
                  case _ => ()
                }
              }

              if (!inIDE && fun.symbol == List_apply && args.isEmpty) {
                atPos(tree.pos) { gen.mkNil setType restpe }
              } else
                constfold(copy.Apply(tree, fun, args2).setType(ifPatternSkipFormals(restpe)))
            } else if (needsInstantiation(tparams, formals, args1)) {
              //println("needs inst "+fun+" "+tparams+"/"+(tparams map (_.info)))
              inferExprInstance(fun, tparams, WildcardType)
              doTypedApply(tree, fun, args1, mode, pt)
            } else {
              assert((mode & PATTERNmode) == 0); // this case cannot arise for patterns
              val lenientTargs = protoTypeArgs(tparams, formals, mt.resultApprox, pt)
              val strictTargs = List.map2(lenientTargs, tparams)((targ, tparam) =>
                if (targ == WildcardType) tparam.tpe else targ)
              def typedArgToPoly(arg: Tree, formal: Type): Tree = {
                val lenientPt = formal.instantiateTypeParams(tparams, lenientTargs)
                val arg1 = typedArg(arg, argMode(fun, mode), POLYmode, lenientPt)
                val argtparams = context.undetparams
                context.undetparams = List()
                if (!argtparams.isEmpty) {
                  val strictPt = formal.instantiateTypeParams(tparams, strictTargs)
                  inferArgumentInstance(arg1, argtparams, strictPt, lenientPt)
                }
                arg1
              }
              val args2 = List.map2(args1, formals)(typedArgToPoly)
              if (args2 exists (_.tpe.isError)) setError(tree)
              else {
                if (settings.debug.value) log("infer method inst "+fun+", tparams = "+tparams+", args = "+args2.map(_.tpe)+", pt = "+pt+", lobounds = "+tparams.map(_.tpe.bounds.lo)+", parambounds = "+tparams.map(_.info));//debug
                val undetparams = inferMethodInstance(fun, tparams, args2, pt)
                val result = doTypedApply(tree, fun, args2, mode, pt)
                context.undetparams = undetparams
                result
              }
            }
          }

        case SingleType(_, _) =>
          doTypedApply(tree, fun setType fun.tpe.widen, args, mode, pt)
        
        case ErrorType =>
          setError(copy.Apply(tree, fun, args))
        /* --- begin unapply  --- */

        case otpe if (mode & PATTERNmode) != 0 && unapplyMember(otpe).exists =>
          val unapp = unapplyMember(otpe)
          assert(unapp.exists, tree)
          val unappType = otpe.memberType(unapp)
          val argDummyType = pt // was unappArg
         // @S: do we need to memoize this?
          val argDummy =  context.owner.newValue(fun.pos, nme.SELECTOR_DUMMY)
            .setFlag(SYNTHETIC)
            .setInfo(argDummyType)
          if (args.length > MaxTupleArity)
            error(fun.pos, "too many arguments for unapply pattern, maximum = "+MaxTupleArity)
          val arg = Ident(argDummy) setType argDummyType
          val oldArgType = arg.tpe
          if (!isApplicableSafe(List(), unappType, List(arg.tpe), WildcardType)) {
            //Console.println("UNAPP: need to typetest, arg.tpe = "+arg.tpe+", unappType = "+unappType)
            def freshArgType(tp: Type): (Type, List[Symbol]) = tp match {
              case MethodType(formals, _) => 
                (formals(0), List())
              case PolyType(tparams, restype) => 
                val tparams1 = cloneSymbols(tparams)
                (freshArgType(restype)._1.substSym(tparams, tparams1), tparams1)
              case OverloadedType(_, _) =>
                error(fun.pos, "cannot resolve overloaded unapply")
                (ErrorType, List())
            }
            val (unappFormal, freeVars) = freshArgType(unappType)
            val context1 = context.makeNewScope(context.tree, context.owner)(FreshArgScopeKind)
            freeVars foreach(sym => context1.scope.enter(sym))
            val typer1 = newTyper(context1)
            arg.tpe = typer1.infer.inferTypedPattern(tree.pos, unappFormal, arg.tpe)
            //todo: replace arg with arg.asInstanceOf[inferTypedPattern(unappFormal, arg.tpe)] instead.
            argDummy.setInfo(arg.tpe) // bq: this line fixed #1281. w.r.t. comment ^^^, maybe good enough?
          }
/*
          val funPrefix = fun.tpe.prefix match {
            case tt @ ThisType(sym) => 
              //Console.println(" sym="+sym+" "+" .isPackageClass="+sym.isPackageClass+" .isModuleClass="+sym.isModuleClass);
              //Console.println(" funsymown="+fun.symbol.owner+" .isClass+"+fun.symbol.owner.isClass);
              //Console.println(" contains?"+sym.tpe.decls.lookup(fun.symbol.name));
              if(sym != fun.symbol.owner && (sym.isPackageClass||sym.isModuleClass) /*(1)*/ ) { // (1) see 'files/pos/unapplyVal.scala'
                if(fun.symbol.owner.isClass) {
                  mkThisType(fun.symbol.owner)
                } else {
                //Console.println("2 ThisType("+fun.symbol.owner+")")
                  NoPrefix                                                 // see 'files/run/unapplyComplex.scala'
                }
              } else tt
            case st @ SingleType(pre, sym) => st
              st
            case xx                        => xx // cannot happen?
          }
          val fun1untyped = fun
            Apply(
              Select(
                gen.mkAttributedRef(funPrefix, fun.symbol) setType null, 
                // setType null is necessary so that ref will be stabilized; see bug 881
                unapp), 
              List(arg))
          }
*/
          val fun1untyped = atPos(fun.pos) { 
            Apply(
              Select(
                fun setType null, // setType null is necessary so that ref will be stabilized; see bug 881
                unapp),
              List(arg))
          }

          val fun1 = typed(fun1untyped)
          if (fun1.tpe.isErroneous) setError(tree)
          else {
            val formals0 = unapplyTypeList(fun1.symbol, fun1.tpe)
            val formals1 = formalTypes(formals0, args.length)
            if (formals1.length == args.length) {
              val args1 = typedArgs(args, mode, formals0, formals1)
              if (!isFullyDefined(pt)) assert(false, tree+" ==> "+UnApply(fun1, args1)+", pt = "+pt)
              // <pending-change>
              //   this would be a better choice (from #1196), but fails due to (broken?) refinements
              val itype =  glb(List(pt, arg.tpe))
              // </pending-change>
              // restore old type (arg is a dummy tree, just needs to pass typechecking)
              arg.tpe = oldArgType
              UnApply(fun1, args1) setPos tree.pos setType itype //pt
              //
              // if you use the better itype, then the following happens.
              // the required type looks wrong...
              // 
              ///files/pos/bug0646.scala                                [FAILED]
              //
              //failed with type mismatch;
              // found   : scala.xml.NodeSeq{ ... }
              // required: scala.xml.NodeSeq{ ... } with scala.xml.NodeSeq{ ... } with scala.xml.Node on: temp3._data().==("Blabla").&&({
              //  exit(temp0);
              //  true
              //})
            } else {
              errorTree(tree, "wrong number of arguments for "+treeSymTypeMsg(fun))
            }
          }
          
/* --- end unapply  --- */
        case _ =>
          errorTree(tree, fun+" of type "+fun.tpe+" does not take parameters")
      }
    }

    def typedAnnotation(annot: Annotation, owner: Symbol): AnnotationInfo =
      typedAnnotation(annot, owner, EXPRmode)

    def typedAnnotation(annot: Annotation, owner: Symbol, mode: Int): AnnotationInfo =
      typedAnnotation(annot, owner, mode, NoSymbol)

    def typedAnnotation(annot: Annotation, owner: Symbol, mode: Int, selfsym: Symbol): AnnotationInfo = {
      var attrError: Boolean = false
      def error(pos: Position, msg: String): Null = {
        context.error(pos, msg)
        attrError = true
        null
      }
      def needConst(tr: Tree) {
        error(tr.pos, "attribute argument needs to be a constant; found: "+tr)
      }

      val typedConstr =
        if (selfsym == NoSymbol) {
          // why a new typer: definitions inside the annotation's constructor argument
          // should not have the annotated's owner as owner.
          val typer1 = newTyper(context.makeNewScope(annot.constr, owner)(TypedScopeKind))
          typer1.typed(annot.constr, mode, AnnotationClass.tpe)
        } else {
          // Since a selfsym is supplied, the annotation should have
          // an extra "self" identifier in scope for type checking.
          // This is implemented by wrapping the rhs
          // in a function like "self => rhs" during type checking,
          // and then stripping the "self =>" and substituting
          // in the supplied selfsym.
          val funcparm = ValDef(NoMods, nme.self, TypeTree(selfsym.info), EmptyTree)
          val func = Function(List(funcparm), annot.constr.duplicate)
                                         // The .duplicate of annot.constr
                                         // deals with problems that
                                         // accur if this annotation is
                                         // later typed again, which
                                         // the compiler sometimes does.
                                         // The problem is that "self"
                                         // ident's within annot.constr
                                         // will retain the old symbol
                                         // from the previous typing.
          val fun1clazz = FunctionClass(1)
          val funcType = typeRef(fun1clazz.tpe.prefix, 
                                 fun1clazz, 
                                 List(selfsym.info, AnnotationClass.tpe))

          typed(func, mode, funcType) match {
            case t @ Function(List(arg), rhs) => 
              val subs =
                new TreeSymSubstituter(List(arg.symbol),List(selfsym))
              subs(rhs)
          }
        }

      typedConstr match {
        case t @ Apply(Select(New(tpt), nme.CONSTRUCTOR), args) =>
          if ((t.tpe==null) || t.tpe.isErroneous) {
            AnnotationInfo(ErrorType, List(), List())
          }
          else {
            val annType = tpt.tpe

            val needsConstant =
              (annType.typeSymbol isNonBottomSubClass ClassfileAnnotationClass)

            def annotArg(tree: Tree): AnnotationArgument = {
              val arg = new AnnotationArgument(tree)
              if(needsConstant && !arg.isConstant && !inIDE)
                needConst(tree)
              arg
            }
            val constrArgs = args map annotArg

            val attrScope = annType.decls
              .filter(sym => sym.isMethod && !sym.isConstructor && sym.hasFlag(JAVA))
            val names = new collection.mutable.HashSet[Symbol]
            names ++= attrScope.elements.filter(_.isMethod)
            if (args.length == 1) {
              names.retain(sym => sym.name != nme.value)
            }
            val nvPairs = annot.elements map {
              case vd @ ValDef(_, name, _, rhs) => {
                val sym = attrScope.lookupWithContext(name)(context.owner);
                if (sym == NoSymbol) {
                  error(vd.pos, "unknown attribute element name: " + name)
                } else if (!names.contains(sym)) {
                  error(vd.pos, "duplicate value for element " + name)
                } else {
                  names -= sym
                  val annArg =
                    annotArg(
                      typed(rhs, EXPRmode, sym.tpe.resultType))
                  (sym.name, annArg)
                }
              }
            }
            for (name <- names) {
              if (!name.attributes.contains(AnnotationInfo(AnnotationDefaultAttr.tpe, List(), List()))) {
                error(annot.constr.pos, "attribute " + annType.typeSymbol.fullNameString + " is missing element " + name.name)
              }
            }
            if (annType.typeSymbol.hasFlag(JAVA) && settings.target.value == "jvm-1.4") {
              context.warning (annot.constr.pos, "Java annotation will not be emitted in classfile unless you use the '-target:jvm-1.5' option")
            }
            if (attrError) AnnotationInfo(ErrorType, List(), List())
            else AnnotationInfo(annType, constrArgs, nvPairs)
          }
      }
    }

    def isRawParameter(sym: Symbol) = // is it a type parameter leaked by a raw type?
      sym.isTypeParameter && sym.owner.hasFlag(JAVA)

    /** Given a set `rawSyms' of term- and type-symbols, and a type `tp'.
     *  produce a set of fresh type parameters and a type so that it can be 
     *  abstracted to an existential type.
     *  Every type symbol `T' in `rawSyms' is mapped to a clone.
     *  Every term symbol `x' of type `T' in `rawSyms' is given an
     *  associated type symbol of the following form:
     *
     *    type x.type <: T with <singleton>
     *
     *  The name of the type parameter is `x.type', to produce nice diagnostics.
     *  The <singleton> parent ensures that the type parameter is still seen as a stable type.
     *  Type symbols in rawSyms are fully replaced by the new symbols.
     *  Term symbols are also replaced, except when they are the term
     *  symbol of an Ident tree, in which case only the type of the
     *  Ident is changed.
     */
    protected def existentialTransform(rawSyms: List[Symbol], tp: Type) = {
      val typeParams: List[Symbol] = rawSyms map { sym =>
        val name = if (sym.isType) sym.name else newTypeName(sym.name+".type")
        val bound = sym.existentialBound
        val sowner = if (isRawParameter(sym)) context.owner else sym.owner
        val quantified: Symbol = recycle(sowner.newAbstractType(sym.pos, name)) 
        trackSetInfo(quantified setFlag EXISTENTIAL)(bound.cloneInfo(quantified))
      }
      val typeParamTypes = typeParams map (_.tpe) // don't trackSetInfo here, since type already set!
      //println("ex trans "+rawSyms+" . "+tp+" "+typeParamTypes+" "+(typeParams map (_.info)))//DEBUG
      for (tparam <- typeParams) tparam.setInfo(tparam.info.subst(rawSyms, typeParamTypes))
      (typeParams, tp.subst(rawSyms, typeParamTypes))
    }

    /** Compute an existential type from raw hidden symbols `syms' and type `tp'
     */
    def packSymbols(hidden: List[Symbol], tp: Type): Type = 
      if (hidden.isEmpty) tp
      else {
//          Console.println("original type: "+tp)
//          Console.println("hidden symbols: "+hidden)
        val (tparams, tp1) = existentialTransform(hidden, tp)
//          Console.println("tparams: "+tparams+", result: "+tp1)
        val res = existentialAbstraction(tparams, tp1)
//          Console.println("final result: "+res)
        res
      }

    class SymInstance(val sym: Symbol, val tp: Type) {
      override def equals(other: Any): Boolean = other match {
        case that: SymInstance =>
          this.sym == that.sym && this.tp =:= that.tp
        case _ =>
          false
      }
      override def hashCode: Int = sym.hashCode * 41 + tp.hashCode
    }

    /** convert skolems to existentials */
    def packedType(tree: Tree, owner: Symbol): Type = {
      def defines(tree: Tree, sym: Symbol) = 
        sym.isExistentialSkolem && sym.unpackLocation == tree ||
        tree.isDef && tree.symbol == sym
      def isVisibleParameter(sym: Symbol) = 
        (sym hasFlag PARAM) && (sym.owner == owner) && (sym.isType || !owner.isAnonymousFunction)
      def containsDef(owner: Symbol, sym: Symbol): Boolean = 
        (!(sym hasFlag PACKAGE)) && {
          var o = sym.owner
          while (o != owner && o != NoSymbol && !(o hasFlag PACKAGE)) o = o.owner
          o == owner && !isVisibleParameter(sym)
        }
      var localSyms = collection.immutable.Set[Symbol]()
      var boundSyms = collection.immutable.Set[Symbol]()
      var localInstances = collection.immutable.Map[SymInstance, Symbol]() 
      def isLocal(sym: Symbol): Boolean =
        if (sym == NoSymbol) false
        else if (owner == NoSymbol) tree exists (defines(_, sym))
        else containsDef(owner, sym) || isRawParameter(sym)
      def containsLocal(tp: Type): Boolean = 
        tp exists (t => isLocal(t.typeSymbol) || isLocal(t.termSymbol))
      val normalizeLocals = new TypeMap {
        def apply(tp: Type): Type = tp match {
          case TypeRef(pre, sym, args) =>
            if (sym.isAliasType && containsLocal(tp)) apply(tp.normalize)
            else {
              if (pre.isVolatile) 
                context.error(tree.pos, "Inferred type "+tree.tpe+" contains type selection from volatile type "+pre)
              mapOver(tp) 
            }
          case _ =>
            mapOver(tp)
        }
      }
      // add all local symbols of `tp' to `localSyms'
      // expanding higher-kinded types into individual copies for each instance.
      def addLocals(tp: Type) {
        def addIfLocal(sym: Symbol, tp: Type) {
          if (sym != NoSymbol && !sym.isRefinementClass && isLocal(sym) &&   
              !(localSyms contains sym) && !(boundSyms contains sym) ) {
            if (sym.typeParams.isEmpty) {
              localSyms += sym
              addLocals(sym.existentialBound)
            } else if (tp.typeArgs.isEmpty) {
              unit.error(tree.pos, 
                "implementation restriction: can't existentially abstract over higher-kinded type" + tp)
            } else {
              val inst = new SymInstance(sym, tp)
              if (!(localInstances contains inst)) {
                val bound = sym.existentialBound match {
                  case PolyType(tparams, restpe) => 
                    restpe.subst(tparams, tp.typeArgs)
                  case t =>
                    t
                }
                val local = trackSetInfo(recycle(sym.owner.newAbstractType(
                  sym.pos, unit.fresh.newName(sym.pos, sym.name.toString))
                    .setFlag(sym.flags)))(bound)
                localInstances += (inst -> local)
                addLocals(bound)
              }
            }
          }
        }
        for (t <- tp) {
          t match {
            case ExistentialType(tparams, _) => 
              boundSyms ++= tparams
	    case AnnotatedType(annots, _, _) =>
	      for (annot <- annots; arg <- annot.args; t <- arg.intTree) {
		t match {
		  case Ident(_) =>
		    // Check the symbol of an Ident, unless the
		    // Ident's type is already over an existential.
		    // (If the type is already over an existential,
                    // then remap the type, not the core symbol.)
		    if (!t.tpe.typeSymbol.hasFlag(EXISTENTIAL))
		      addIfLocal(t.symbol, t.tpe)
		  case _ => ()
		}
	      }
            case _ =>
          }
          addIfLocal(t.termSymbol, t)
          addIfLocal(t.typeSymbol, t)
        }
      }

      object substLocals extends TypeMap {
	    override val dropNonConstraintAnnotations = true

        def apply(t: Type): Type = t match {
          case TypeRef(_, sym, args) if (sym.isLocal && args.length > 0) =>
            localInstances.get(new SymInstance(sym, t)) match {
              case Some(local) => typeRef(NoPrefix, local, List())
              case None => mapOver(t)
            }
          case _ => mapOver(t)
        }

	override def mapOver(arg: Tree, giveup: ()=>Nothing) = {
	  object substLocalTrees extends TypeMapTransformer {
	    override def transform(tr: Tree) = {
              localInstances.get(new SymInstance(tr.symbol, tr.tpe)) match {
		case Some(local) => 
		  Ident(local.existentialToString)
		    .setSymbol(tr.symbol).copyAttrs(tr).setType(
		      typeRef(NoPrefix, local, List()))
		
		case None => super.transform(tr)
	      }
	    }
	  }

	  substLocalTrees.transform(arg)
	}
      }

      val normalizedTpe = normalizeLocals(tree.tpe)
      addLocals(normalizedTpe)

      packSymbols(localSyms.toList ::: localInstances.values.toList, substLocals(normalizedTpe))
    }

    protected def typedExistentialTypeTree(tree: ExistentialTypeTree, mode: Int): Tree = {
      for (wc <- tree.whereClauses)
        if (wc.symbol == NoSymbol) { namer.enterSym(wc); wc.symbol setFlag EXISTENTIAL }
        else context.scope enter wc.symbol
      val whereClauses1 = typedStats(tree.whereClauses, context.owner)
      for (vd @ ValDef(_, _, _, _) <- tree.whereClauses)
        if (vd.symbol.tpe.isVolatile)
          error(vd.pos, "illegal abstraction from value with volatile type "+vd.symbol.tpe)
      val tpt1 = typedType(tree.tpt, mode)
      val (typeParams, tpe) = existentialTransform(tree.whereClauses map (_.symbol), tpt1.tpe)
      //println(tpe + ": " + tpe.getClass )
      TypeTree(ExistentialType(typeParams, tpe)) setOriginal tree
    }

    /**
     *  @param tree ...
     *  @param mode ...
     *  @param pt   ...
     *  @return     ...
     */
    protected def typed1(tree: Tree, mode: Int, pt: Type): Tree = {
      //Console.println("typed1("+tree.getClass()+","+Integer.toHexString(mode)+","+pt+")")
      def ptOrLub(tps: List[Type]) = if (isFullyDefined(pt)) pt else lub(tps map (_.deconst))
      
      //@M! get the type of the qualifier in a Select tree, otherwise: NoType
      def prefixType(fun: Tree): Type = fun match { 
        case Select(qualifier, _) => qualifier.tpe
//        case Ident(name) => ??
        case _ => NoType
      }
      
      def typedAnnotated(annot: Annotation, arg1: Tree): Tree = {
        /** mode for typing the annotation itself */
        val annotMode = mode & ~TYPEmode | EXPRmode

        if (arg1.isType) {
          val selfsym =
            if (!settings.selfInAnnots.value)
              NoSymbol
            else
              arg1.tpe.selfsym match {
                case NoSymbol =>
                  /* Implementation limitation: Currently this
                   * can cause cyclical reference errors even
                   * when the self symbol is not referenced at all.
                   * Surely at least some of these cases can be
                   * fixed by proper use of LazyType's.  Lex tinkered
                   * on this but did not succeed, so is leaving
                   * it alone for now. Example code with the problem:
                   *  class peer extends Annotation
                   *  class NPE[T <: NPE[T] @peer]
                   *
                   * (Note: -Yself-in-annots must be on to see the problem)
                   **/
                  val sym = 
                    newLocalDummy(context.owner, annot.pos)
                      .newValue(annot.pos, nme.self)
                  sym.setInfo(arg1.tpe.withoutAttributes)
                  sym
                case sym => sym
              }

          // use the annotation context's owner as parent for symbols defined
          // inside a type annotation
          val ainfo = typedAnnotation(annot, context.owner, annotMode, selfsym)
          val atype0 = arg1.tpe.withAttribute(ainfo)
          val atype =
            if ((selfsym != NoSymbol) && (ainfo.refsSymbol(selfsym)))
              atype0.withSelfsym(selfsym)
            else
              atype0 // do not record selfsym if
                     // this annotation did not need it

          if (ainfo.isErroneous)
            arg1  // simply drop erroneous annotations
          else
            TypeTree(atype) setOriginal tree
        } else {
          def annotTypeTree(ainfo: AnnotationInfo): Tree = 
            TypeTree(arg1.tpe.withAttribute(ainfo)) setOriginal tree

          val annotInfo = typedAnnotation(annot, context.owner, annotMode)

          arg1 match {
            case _: DefTree => 
              if (!annotInfo.atp.isError) {
                val attributed =
                  if (arg1.symbol.isModule) arg1.symbol.moduleClass else arg1.symbol
                attributed.attributes = annotInfo :: attributed.attributes
              }
              arg1
            case _ =>
              val atpt = annotTypeTree(annotInfo)
              Typed(arg1, atpt) setPos tree.pos setType atpt.tpe
          }
        }
      }

      def typedBind(name: Name, body: Tree) = {
        var vble = tree.symbol
        if (name.isTypeName) {
          assert(body == EmptyTree)
          if (vble == NoSymbol) 
            vble = 
              if (isFullyDefined(pt))
                context.owner.newAliasType(tree.pos, name) setInfo pt
              else 
                context.owner.newAbstractType(tree.pos, name) setInfo
                  mkTypeBounds(NothingClass.tpe, AnyClass.tpe)
          val rawInfo = vble.rawInfo
          vble = if (vble.name == nme.WILDCARD.toTypeName) context.scope.enter(vble)
                 else namer.enterInScope(vble) 
          trackSetInfo(vble)(rawInfo) // vble could have been recycled, detect changes in type       
          tree setSymbol vble setType vble.tpe
        } else {
          if (vble == NoSymbol) 
            vble = context.owner.newValue(tree.pos, name)
          if (vble.name.toTermName != nme.WILDCARD) {
/*
          if (namesSomeIdent(vble.name))
            context.warning(tree.pos,
              "pattern variable"+vble.name+" shadows a value visible in the environment;\n"+
              "use backquotes `"+vble.name+"` if you mean to match against that value;\n" +
              "or rename the variable or use an explicit bind "+vble.name+"@_ to avoid this warning.")
*/
            if ((mode & ALTmode) != 0)
              error(tree.pos, "illegal variable in pattern alternative")
            vble = namer.enterInScope(vble)
          }
          val body1 = typed(body, mode, pt)
          trackSetInfo(vble)(
            if (treeInfo.isSequenceValued(body)) seqType(body1.tpe)
            else body1.tpe)
          copy.Bind(tree, name, body1) setSymbol vble setType body1.tpe   // buraq, was: pt
        }
      }

      def typedArrayValue(elemtpt: Tree, elems: List[Tree]) = {
        val elemtpt1 = typedType(elemtpt, mode)
        val elems1 = List.mapConserve(elems)(elem => typed(elem, mode, elemtpt1.tpe))
        copy.ArrayValue(tree, elemtpt1, elems1)
          .setType(
            (if (isFullyDefined(pt) && !phase.erasedTypes) pt
             else appliedType(ArrayClass.typeConstructor, List(elemtpt1.tpe))).notNull)
      }

      def typedAssign(lhs: Tree, rhs: Tree): Tree = {
        def mayBeVarGetter(sym: Symbol) = sym.info match {
          case PolyType(List(), _) => sym.owner.isClass && !sym.isStable
          case _: ImplicitMethodType => sym.owner.isClass && !sym.isStable
          case _ => false
        }
        val lhs1 = typed(lhs, EXPRmode | LHSmode, WildcardType)
        val varsym = lhs1.symbol
        if ((varsym ne null) && mayBeVarGetter(varsym))
          lhs1 match {
            case Select(qual, name) =>
              return typed(
                Apply(
                  Select(qual, nme.getterToSetter(name)) setPos lhs.pos,
                  List(rhs)) setPos tree.pos, 
                mode, pt)

            case _ =>

          }
        if ((varsym ne null) && (varsym.isVariable || varsym.isValue && phase.erasedTypes)) {
          val rhs1 = typed(rhs, lhs1.tpe)
          copy.Assign(tree, lhs1, checkDead(rhs1)) setType UnitClass.tpe
        } else {
          if (!lhs1.tpe.isError) {
            //println(lhs1+" = "+rhs)//DEBUG
            error(tree.pos, 
                  if ((varsym ne null) && varsym.isValue) "reassignment to val"
                  else "assignment to non variable")
          }
          setError(tree)
        }
      }

      def typedIf(cond: Tree, thenp: Tree, elsep: Tree) = {
        val cond1 = checkDead(typed(cond, BooleanClass.tpe))
        if (elsep.isEmpty) {
          val thenp1 = typed(thenp, UnitClass.tpe)
          copy.If(tree, cond1, thenp1, elsep) setType UnitClass.tpe
        } else {
          val thenp1 = typed(thenp, pt)
          val elsep1 = typed(elsep, pt)
          copy.If(tree, cond1, thenp1, elsep1) setType ptOrLub(List(thenp1.tpe, elsep1.tpe))
        }
      }

      def typedReturn(expr: Tree) = {
        val enclMethod = context.enclMethod
        if (enclMethod == NoContext || 
            enclMethod.owner.isConstructor || 
            context.enclClass.enclMethod == enclMethod // i.e., we are in a constructor of a local class
            ) {
          errorTree(tree, "return outside method definition")
        } else {
          val DefDef(_, _, _, _, restpt, _) = enclMethod.tree
          var restpt0 = restpt
          if (inIDE && (restpt0.tpe eq null)) {
            restpt0 = typed(restpt0, TYPEmode, WildcardType)
          }
          if (restpt0.tpe eq null) {
            errorTree(tree, "" + enclMethod.owner +
                      " has return statement; needs result type")
          } else {
            context.enclMethod.returnsSeen = true
            val expr1: Tree = typed(expr, restpt0.tpe)
            copy.Return(tree, checkDead(expr1)) setSymbol enclMethod.owner setType NothingClass.tpe
          }
        }
      }

      def typedNew(tpt: Tree) = {
        var tpt1 = typedTypeConstructor(tpt)
        checkClassType(tpt1, false)
        if (tpt1.hasSymbol && !tpt1.symbol.typeParams.isEmpty) {
          context.undetparams = cloneSymbols(tpt1.symbol.typeParams)
          tpt1 = TypeTree()
            .setOriginal(tpt1) /* .setPos(tpt1.pos) */
            .setType(appliedType(tpt1.tpe, context.undetparams map (_.tpe)))
        }
        /** If current tree <tree> appears in <val x(: T)? = <tree>>
         *  return `tp with x.type' else return `tp'.
         */
        def narrowRhs(tp: Type) = {
          var sym = context.tree.symbol
          if (sym != null && sym != NoSymbol && sym.owner.isClass && sym.getter(sym.owner) != NoSymbol) 
            sym = sym.getter(sym.owner)
          context.tree match {
            case ValDef(mods, _, _, Apply(Select(`tree`, _), _)) if !(mods hasFlag MUTABLE) =>
              val pre = if (sym.owner.isClass) sym.owner.thisType else NoPrefix
              intersectionType(List(tp, singleType(pre, sym)))
            case _ =>
              tp
          }
        }
        if (tpt1.tpe.typeSymbol.isAbstractType || (tpt1.tpe.typeSymbol hasFlag ABSTRACT))
          error(tree.pos, tpt1.tpe.typeSymbol + " is abstract; cannot be instantiated")
        else if (tpt1.tpe.typeSymbol.initialize.thisSym != tpt1.tpe.typeSymbol &&
                 !(narrowRhs(tpt1.tpe) <:< tpt1.tpe.typeOfThis) && 
                 !phase.erasedTypes) {
          error(tree.pos, tpt1.tpe.typeSymbol + 
                " cannot be instantiated because it does not conform to its self-type "+
                tpt1.tpe.typeOfThis)
        }
        copy.New(tree, tpt1).setType(tpt1.tpe)
      }

      def typedEta(expr1: Tree): Tree = expr1.tpe match {
        case TypeRef(_, sym, _) if (sym == ByNameParamClass) =>
          val expr2 = Function(List(), expr1) setPos expr1.pos
          new ChangeOwnerTraverser(context.owner, expr2.symbol).traverse(expr2)
          typed1(expr2, mode, pt)
        case PolyType(List(), restpe) =>
          val expr2 = Function(List(), expr1) setPos expr1.pos
          new ChangeOwnerTraverser(context.owner, expr2.symbol).traverse(expr2)
          typed1(expr2, mode, pt)
        case PolyType(_, MethodType(formals, _)) =>
          if (isFunctionType(pt)) expr1
          else adapt(expr1, mode, functionType(formals map (t => WildcardType), WildcardType))
        case MethodType(formals, _) =>
          if (isFunctionType(pt)) expr1
          else expr1 match {
            case Select(qual, name) if (forMSIL && 
                                        pt != WildcardType && 
                                        pt != ErrorType && 
                                        isSubType(pt, DelegateClass.tpe)) =>
              val scalaCaller = newScalaCaller(pt);
              addScalaCallerInfo(scalaCaller, expr1.symbol)
              val n: Name = scalaCaller.name
              val del = Ident(DelegateClass) setType DelegateClass.tpe
              val f = Select(del, n)
              //val f1 = TypeApply(f, List(Ident(pt.symbol) setType pt))
              val args: List[Tree] = if(expr1.symbol.isStatic) List(Literal(Constant(null)))
                                     else List(qual) // where the scala-method is located
              val rhs = Apply(f, args);
              typed(rhs)
            case _ => 
              adapt(expr1, mode, functionType(formals map (t => WildcardType), WildcardType))
          }
        case ErrorType =>
          expr1
        case _ =>
          errorTree(expr1, "_ must follow method; cannot follow " + expr1.tpe)
      }

      def typedTypeApply(fun: Tree, args: List[Tree]): Tree = fun.tpe match {
        case OverloadedType(pre, alts) =>
          inferPolyAlternatives(fun, args map (_.tpe))
          val tparams = fun.symbol.typeParams //@M TODO: fun.symbol.info.typeParams ? (as in typedAppliedTypeTree)
          val args1 = if(args.length == tparams.length) {
            //@M: in case TypeApply we can't check the kind-arities of the type arguments,
            // as we don't know which alternative to choose... here we do
            map2Conserve(args, tparams) { 
              //@M! the polytype denotes the expected kind
              (arg, tparam) => typedHigherKindedType(arg, mode, polyType(tparam.typeParams, AnyClass.tpe)) 
            }          
          } else // @M: there's probably something wrong when args.length != tparams.length... (triggered by bug #320)
           // Martin, I'm using fake trees, because, if you use args or arg.map(typedType), 
           // inferPolyAlternatives loops...  -- I have no idea why :-(
            args.map(t => errorTree(tree, "wrong number of type parameters for "+treeSymTypeMsg(fun))) 
          
          typedTypeApply(fun, args1)
        case SingleType(_, _) =>
          typedTypeApply(fun setType fun.tpe.widen, args)
        case PolyType(tparams, restpe) if (tparams.length != 0) =>
          if (tparams.length == args.length) {
            val targs = args map (_.tpe)
            checkBounds(tree.pos, NoPrefix, NoSymbol, tparams, targs, "")
            if (fun.symbol == Predef_classOf) {
              checkClassType(args.head, true) 
              atPos(tree.pos) { gen.mkClassOf(targs.head) }
            } else {
              if (phase.id <= currentRun.typerPhase.id &&
                  fun.symbol == Any_isInstanceOf && !targs.isEmpty)
                checkCheckable(tree.pos, targs.head, "")
              val resultpe = restpe.instantiateTypeParams(tparams, targs)
              //@M substitution in instantiateParams needs to be careful!
              //@M example: class Foo[a] { def foo[m[x]]: m[a] = error("") } (new Foo[Int]).foo[List] : List[Int]
              //@M    --> first, m[a] gets changed to m[Int], then m gets substituted for List, 
              //          this must preserve m's type argument, so that we end up with List[Int], and not List[a]
              //@M related bug: #1438 
              //println("instantiating type params "+restpe+" "+tparams+" "+targs+" = "+resultpe)
              copy.TypeApply(tree, fun, args) setType resultpe
            }
          } else {
            errorTree(tree, "wrong number of type parameters for "+treeSymTypeMsg(fun))
          }
        case ErrorType =>
          setError(tree)
        case _ =>
          errorTree(tree, treeSymTypeMsg(fun)+" does not take type parameters.")
        }

      /**
       *  @param args ...
       *  @return     ...
       */
      def tryTypedArgs(args: List[Tree], mode: Int, other: TypeError): List[Tree] = {
        val c = context.makeSilent(false)
        c.retyping = true
        try {
          newTyper(c).typedArgs(args, mode)
        } catch {
          case ex: TypeError =>
            null
        }
      }

      /** Try to apply function to arguments; if it does not work try to
       *  insert an implicit conversion.
       *
       *  @param fun  ...
       *  @param args ...
       *  @return     ...
       */
      def tryTypedApply(fun: Tree, args: List[Tree]): Tree = 
        silent(_.doTypedApply(tree, fun, args, mode, pt)) match {
          case t: Tree => 
            t
          case ex: TypeError =>
            def errorInResult(tree: Tree): Boolean = tree.pos == ex.pos || {
              tree match {
                case Block(_, r) => errorInResult(r)
                case Match(_, cases) => cases exists errorInResult
                case CaseDef(_, _, r) => errorInResult(r)
                case Annotated(_, r) => errorInResult(r)
                case If(_, t, e) => errorInResult(t) || errorInResult(e)
                case Try(b, catches, _) => errorInResult(b) || (catches exists errorInResult)
                case Typed(r, Function(List(), EmptyTree)) => errorInResult(r)
                case _ => false
              }
            }
            if (errorInResult(fun) || (args exists errorInResult)) {
              val Select(qual, name) = fun
              val args1 = tryTypedArgs(args, argMode(fun, mode), ex)
              val qual1 =
                if ((args1 ne null) && !pt.isError) {
                  def templateArgType(arg: Tree) =
                    new BoundedWildcardType(mkTypeBounds(arg.tpe, AnyClass.tpe))
                  adaptToMember(qual, name, MethodType(args1 map templateArgType, pt))
                } else qual
              if (qual1 ne qual) {
                val tree1 = Apply(Select(qual1, name) setPos fun.pos, args1) setPos tree.pos
                return typed1(tree1, mode | SNDTRYmode, pt)
              }
            } 
            reportTypeError(tree.pos, ex)
            setError(tree)
        }

      def typedApply(fun: Tree, args: List[Tree]) = {
        val stableApplication = (fun.symbol ne null) && fun.symbol.isMethod && fun.symbol.isStable
        if (stableApplication && (mode & PATTERNmode) != 0) {
          // treat stable function applications f() as expressions.
          typed1(tree, mode & ~PATTERNmode | EXPRmode, pt)
        } else {
          val funpt = if ((mode & PATTERNmode) != 0) pt else WildcardType
          silent(_.typed(fun, funMode(mode), funpt)) match {
            case fun1: Tree =>
              val fun2 = if (stableApplication) stabilizeFun(fun1, mode, pt) else fun1
              if (util.Statistics.enabled) appcnt += 1
              val res = 
                if (phase.id <= currentRun.typerPhase.id &&
                    fun2.isInstanceOf[Select] && 
                    !fun2.tpe.isInstanceOf[ImplicitMethodType] &&
                    ((fun2.symbol eq null) || !fun2.symbol.isConstructor) &&
                    (mode & (EXPRmode | SNDTRYmode)) == EXPRmode) {
                      tryTypedApply(fun2, args)
                    } else {
                      doTypedApply(tree, fun2, args, mode, pt)
                    }
            /*
              if (fun2.hasSymbol && fun2.symbol.isConstructor && (mode & EXPRmode) != 0) {
                res.tpe = res.tpe.notNull
              }
              */
              if (fun2.symbol == Array_apply) typed { atPos(tree.pos) { gen.mkCheckInit(res) } }
              else res
                
            case ex: TypeError =>
              fun match {
                case Select(qual, name) 
                if (mode & PATTERNmode) == 0 && nme.isOpAssignmentName(name.decode) =>
                  val qual1 = typedQualifier(qual)
                  if (treeInfo.isVariableOrGetter(qual1)) {
                    convertToAssignment(fun, qual1, name, args, ex) 
                  } else {
		    if ((qual1.symbol ne null) && qual1.symbol.isValue) 
		      error(tree.pos, "reassignment to val")
		    else
                      reportTypeError(fun.pos, ex)
                    setError(tree)                           
                  }
                case _ =>
                  reportTypeError(fun.pos, ex)
                  setError(tree)                           
              }
          }
        }
      }

      def convertToAssignment(fun: Tree, qual: Tree, name: Name, args: List[Tree], ex: TypeError): Tree = {
        val prefix = name.subName(0, name.length - nme.EQL.length)
        def mkAssign(vble: Tree): Tree = 
          Assign(
            vble,
              Apply(Select(vble.duplicate, prefix) setPos fun.pos, args) setPos tree.pos
          ) setPos tree.pos
        val tree1 = qual match {
          case Select(qualqual, vname) =>
            gen.evalOnce(qualqual, context.owner, context.unit) { qq =>
              mkAssign(Select(qq(), vname) setPos qual.pos)
            }
          case Apply(Select(table, nme.apply), indices) =>
            gen.evalOnceAll(table :: indices, context.owner, context.unit) { ts =>
              val tab = ts.head
              val is = ts.tail
              Apply(
                 Select(tab(), nme.update) setPos table.pos,
                 ((is map (i => i())) ::: List(
                   Apply(
                     Select(
                       Apply(
                         Select(tab(), nme.apply) setPos table.pos,
                         is map (i => i())) setPos qual.pos,
                       prefix) setPos fun.pos, 
                     args) setPos tree.pos)
                 )
               ) setPos tree.pos
             }
           case Ident(_) =>
             mkAssign(qual)
        }                
        typed1(tree1, mode, pt)
/*
        if (settings.debug.value) log("retry assign: "+tree1)
        silent(_.typed1(tree1, mode, pt)) match {
          case t: Tree => 
            t
          case _ =>
            reportTypeError(tree.pos, ex)
            setError(tree)                           
        }
*/
      }

      def typedSuper(qual: Name, mix: Name) = {
        val (clazz, selftype) =
          if (tree.symbol != NoSymbol) {
            (tree.symbol, tree.symbol.thisType)
          } else {
            val clazzContext = qualifyingClassContext(tree, qual)
            (clazzContext.owner, clazzContext.prefix)
          }
        if (clazz == NoSymbol) setError(tree)
        else {
          def findMixinSuper(site: Type): Type = {
            val ps = site.parents filter (p => compare(p.typeSymbol, mix))
            if (ps.isEmpty) {
              if (settings.debug.value)
                Console.println(site.parents map (_.typeSymbol.name))//debug
              if (phase.erasedTypes && context.enclClass.owner.isImplClass) {
                // the reference to super class got lost during erasure
                unit.error(tree.pos, "implementation restriction: traits may not select fields or methods from to super[C] where C is a class")
              } else {
                error(tree.pos, mix+" does not name a parent class of "+clazz)
              }
              ErrorType
            } else if (!ps.tail.isEmpty) {
              error(tree.pos, "ambiguous parent class qualifier")
              ErrorType
            } else {
              ps.head
            }
          }
          val owntype =
            if (mix.isEmpty) {
              if ((mode & SUPERCONSTRmode) != 0) 
                if (clazz.info.parents.isEmpty) AnyRefClass.tpe // can happen due to cyclic references ==> #1036
                else clazz.info.parents.head
              else intersectionType(clazz.info.parents)
            } else {
              findMixinSuper(clazz.info)
            }
          tree setSymbol clazz setType mkSuperType(selftype, owntype)
        }
      }

      def typedThis(qual: Name) = {
        val (clazz, selftype) =
          if (tree.symbol != NoSymbol) {
            (tree.symbol, tree.symbol.thisType)
          } else {
            val clazzContext = qualifyingClassContext(tree, qual)
            (clazzContext.owner, clazzContext.prefix)
          }
        if (clazz == NoSymbol) setError(tree)
        else {
          tree setSymbol clazz setType selftype.underlying
          if (isStableContext(tree, mode, pt)) tree setType selftype
          tree
        }
      }
        

      /** Attribute a selection where <code>tree</code> is <code>qual.name</code>.
       *  <code>qual</code> is already attributed.
       *
       *  @param qual ...
       *  @param name ...
       *  @return     ...
       */
      def typedSelect(qual: Tree, name: Name): Tree = {
        val sym =
          if (tree.symbol != NoSymbol) {
            if (phase.erasedTypes && qual.isInstanceOf[Super])
              qual.tpe = tree.symbol.owner.tpe
            if (false && settings.debug.value) { // todo: replace by settings.check.value?
              val alts = qual.tpe.member(tree.symbol.name).alternatives
              if (!(alts exists (alt =>
                alt == tree.symbol || alt.isTerm && (alt.tpe matches tree.symbol.tpe))))
                assert(false, "symbol "+tree.symbol+tree.symbol.locationString+" not in "+alts+" of "+qual.tpe+
                       "\n members = "+qual.tpe.members+
                       "\n type history = "+qual.tpe.termSymbol.infosString+
                       "\n phase = "+phase)
            }
            tree.symbol
          } else {
            if (!inIDE) member(qual, name)(context.owner)
            else verifyAndPrioritize(_ filter (alt => context.isAccessible(alt, qual.tpe, qual.isInstanceOf[Super])))(pt)(member(qual,name)(context.owner))
          }
        if (sym == NoSymbol && name != nme.CONSTRUCTOR && (mode & EXPRmode) != 0) {
          val qual1 = adaptToName(qual, name)
          if (qual1 ne qual) return typed(copy.Select(tree, qual1, name), mode, pt)
        }
        if (!sym.exists) {
          if (settings.debug.value) Console.err.println("qual = "+qual+":"+qual.tpe+"\nSymbol="+qual.tpe.termSymbol+"\nsymbol-info = "+qual.tpe.termSymbol.info+"\nscope-id = "+qual.tpe.termSymbol.info.decls.hashCode()+"\nmembers = "+qual.tpe.members+"\nname = "+name+"\nfound = "+sym+"\nowner = "+context.enclClass.owner)
          if (!qual.tpe.widen.isErroneous) {
            error(tree.pos,
              if (name == nme.CONSTRUCTOR) 
                qual.tpe.widen+" does not have a constructor"
              else 
                decode(name)+" is not a member of "+qual.tpe.widen +
                (if (!inIDE && (context.unit ne null) && 
                    ((for(a <- qual.pos.line; b <- tree.pos.line) yield a < b).getOrElse(false)))
                  "\npossible cause: maybe a semicolon is missing before `"+decode(name)+"'?"
                 else ""))
          }
          setError(tree)
        } else {
          val tree1 = tree match {
            case Select(_, _) => copy.Select(tree, qual, name)
            case SelectFromTypeTree(_, _) => copy.SelectFromTypeTree(tree, qual, name)
          }
          val result = stabilize(checkAccessible(tree1, sym, qual.tpe, qual), qual.tpe, mode, pt)
          if (!global.phase.erasedTypes && settings.Xchecknull.value && 
              !sym.isConstructor &&
              !(qual.tpe <:< NotNullClass.tpe) && !qual.tpe.isNotNull)
            unit.warning(tree.pos, "potential null pointer dereference: "+tree)
          result
        }
      }

      /** does given name name an identifier visible at this point?
       *
       *  @param name the given name
       *  @return     <code>true</code> if an identifier with the given name is visible.
       */
      def namesSomeIdent(name: Name): Boolean = {
        var cx = context
        while (cx != NoContext) {
          val pre = cx.enclClass.prefix
          val defEntry = cx.scope.lookupEntryWithContext(name)(context.owner)
          if ((defEntry ne null) && defEntry.sym.exists) return true
          cx = cx.enclClass
          if ((pre.member(name) filter (
            sym => sym.exists && context.isAccessible(sym, pre, false))) != NoSymbol) return true
          cx = cx.outer
        }
        var imports = context.imports      // impSym != NoSymbol => it is imported from imports.head
        while (!imports.isEmpty) {
          if (imports.head.importedSymbol(name) != NoSymbol) return true
          imports = imports.tail
        }
        false
      }

      /** Attribute an identifier consisting of a simple name or an outer reference.
       *
       *  @param tree      The tree representing the identifier. 
       *  @param name      The name of the identifier.
       *  Transformations: (1) Prefix class members with this.
       *                   (2) Change imported symbols to selections
       */
      def typedIdent(name: Name): Tree = {
        def ambiguousError(msg: String) =
          error(tree.pos, "reference to " + name + " is ambiguous;\n" + msg)

        var defSym: Symbol = tree.symbol // the directly found symbol
        var pre: Type = NoPrefix         // the prefix type of defSym, if a class member
        var qual: Tree = EmptyTree       // the qualififier tree if transformed tree is a select

        // if we are in a constructor of a pattern, ignore all definitions
        // which are methods (note: if we don't do that
        // case x :: xs in class List would return the :: method).
        def qualifies(sym: Symbol): Boolean = 
          sym.exists && 
          ((mode & PATTERNmode | FUNmode) != (PATTERNmode | FUNmode) || !sym.isSourceMethod)
           
        if (defSym == NoSymbol) {
          var defEntry: ScopeEntry = null // the scope entry of defSym, if defined in a local scope

          var cx = context
          if ((mode & (PATTERNmode | TYPEPATmode)) != 0) {
            // println("ignoring scope: "+name+" "+cx.scope+" "+cx.outer.scope)
            // ignore current variable scope in patterns to enforce linearity
            cx = cx.outer 
          }
          
          while (defSym == NoSymbol && cx != NoContext) {
            pre = cx.enclClass.prefix
            defEntry = if (!inIDE) cx.scope.lookupEntryWithContext(name)(context.owner)
                       else verifyAndPrioritize(sym => sym)(pt)(cx.scope.lookupEntryWithContext(name)(context.owner))
            if ((defEntry ne null) && qualifies(defEntry.sym)) {
              defSym = defEntry.sym
            } else if (inIDE) { // IDE: cannot rely on linked scopes.
              if (cx.outer.owner eq cx.enclClass.owner) {
                //cx = cx.outer
                defSym = 
                  verifyAndPrioritize{ // enables filtering of auto completion
                  _ filter (sym => qualifies(sym) && context.isAccessible(sym, pre, false))
                }(pt)(pre.member(name) filter (
                    sym => qualifies(sym) && context.isAccessible(sym, pre, false)))
              }
              val oldScope = cx.scope
              cx = cx.outer
              while (cx.scope == oldScope && !(cx.outer.owner eq cx.enclClass.owner)) // can't skip          
                cx = cx.outer
            } else {
              cx = cx.enclClass
              defSym = pre.member(name) filter (
                sym => qualifies(sym) && context.isAccessible(sym, pre, false))
              if (defSym == NoSymbol) cx = cx.outer
            }
          }

          val symDepth = if (defEntry eq null) cx.depth
                         else cx.depth - (cx.scope.nestingLevel - defEntry.owner.nestingLevel)
          var impSym: Symbol = NoSymbol;      // the imported symbol
          var imports = context.imports;      // impSym != NoSymbol => it is imported from imports.head
          while (!impSym.exists && !imports.isEmpty && imports.head.depth > symDepth) {
            impSym = imports.head.importedSymbol(name)
            if (!impSym.exists) imports = imports.tail
          }

          // detect ambiguous definition/import,
          // update `defSym' to be the final resolved symbol,
          // update `pre' to be `sym's prefix type in case it is an imported member,
          // and compute value of:

          if (defSym.exists && impSym.exists) {
            // imported symbols take precedence over package-owned symbols in different
            // compilation units. Defined symbols take precedence over errenous imports.
            if (defSym.owner.isPackageClass && 
                ((!inIDE && !currentRun.compiles(defSym)) ||
                 (context.unit ne null) && defSym.sourceFile != context.unit.source.file))
              defSym = NoSymbol
            else if (impSym.isError)
              impSym = NoSymbol
          }
          if (defSym.exists) {
            if (impSym.exists)
              ambiguousError(
                "it is both defined in "+defSym.owner +
                " and imported subsequently by \n"+imports.head)
            else if (!defSym.owner.isClass || defSym.owner.isPackageClass || defSym.isTypeParameterOrSkolem)
              pre = NoPrefix
            else
              qual = atPos(tree.pos)(gen.mkAttributedQualifier(pre))
          } else {
            if (impSym.exists) {
              var impSym1 = NoSymbol
              var imports1 = imports.tail
              def ambiguousImport() = {
                if (!(imports.head.qual.tpe =:= imports1.head.qual.tpe))
                  ambiguousError(
                    "it is imported twice in the same scope by\n"+imports.head +  "\nand "+imports1.head)
              }
              while (!imports1.isEmpty && 
                     (!imports.head.isExplicitImport(name) ||
                      imports1.head.depth == imports.head.depth)) {
                var impSym1 = imports1.head.importedSymbol(name)
                if (impSym1.exists) {
                  if (imports1.head.isExplicitImport(name)) {
                    if (imports.head.isExplicitImport(name) ||
                        imports1.head.depth != imports.head.depth) ambiguousImport()
                    impSym = impSym1
                    imports = imports1
                  } else if (!imports.head.isExplicitImport(name) &&
                             imports1.head.depth == imports.head.depth) ambiguousImport()
                }
                imports1 = imports1.tail
              }
              defSym = impSym
              qual = atPos(tree.pos)(resetPos(imports.head.qual.duplicate))
              pre = qual.tpe
            } else {
              if (settings.debug.value) {
                log(context.imports)//debug
              }
              error(tree.pos, "not found: "+decode(name))
              defSym = context.owner.newErrorSymbol(name)
            }
          }
        }
        if (defSym.owner.isPackageClass) pre = defSym.owner.thisType
        if (defSym.isThisSym) {
          val tree1 = typed1(This(defSym.owner) setPos tree.pos, mode, pt)
          if (inIDE) {
            Ident(defSym.name) setType tree1.tpe setSymbol defSym setPos tree.pos
          } else tree1
        } else {
          val tree1 = if (qual == EmptyTree) tree
                      else atPos(tree.pos)(Select(qual, name))
                    // atPos necessary because qualifier might come from startContext
          stabilize(checkAccessible(tree1, defSym, pre, qual), pre, mode, pt)
        }
      }

      def typedCompoundTypeTree(templ: Template) = {
        val parents1 = List.mapConserve(templ.parents)(typedType(_, mode))
        if (parents1 exists (_.tpe.isError)) tree setType ErrorType
        else {
          val decls = scopeFor(tree, CompoundTreeScopeKind)
          //Console.println("Owner: " + context.enclClass.owner + " " + context.enclClass.owner.id)
          val self = refinedType(parents1 map (_.tpe), context.enclClass.owner, decls, templ.pos)
          newTyper(context.make(templ, self.typeSymbol, decls)).typedRefinement(templ.body)
          tree setType self
        }
      }

      def typedAppliedTypeTree(tpt: Tree, args: List[Tree]) = {
        val tpt1 = typed1(tpt, mode | FUNmode | TAPPmode, WildcardType)
        if (tpt1.tpe.isError) {
          setError(tree)
        } else if (!tpt1.hasSymbol) {
          errorTree(tree, tpt1.tpe+" does not take type parameters")
        } else {
          val tparams = tpt1.symbol.typeParams 
          if (tparams.length == args.length) {
          // @M: kind-arity checking is done here and in adapt, full kind-checking is in checkKindBounds (in Infer)
            val args1 = 
              if(!tpt1.symbol.rawInfo.isComplete) 
                List.mapConserve(args){(x: Tree) => typedHigherKindedType(x, mode)} 
                // if symbol hasn't been fully loaded, can't check kind-arity
              else map2Conserve(args, tparams) { 
                (arg, tparam) => 
                  typedHigherKindedType(arg, mode, polyType(tparam.typeParams, AnyClass.tpe)) 
                  //@M! the polytype denotes the expected kind
              }
            val argtypes = args1 map (_.tpe)
            val owntype = if (tpt1.symbol.isClass || tpt1.symbol.isTypeMember) 
                             // @M! added the latter condition
                             appliedType(tpt1.tpe, argtypes) 
                          else tpt1.tpe.instantiateTypeParams(tparams, argtypes)
            List.map2(args, tparams) { (arg, tparam) => arg match {
              // note: can't use args1 in selector, because Bind's got replaced 
              case Bind(_, _) => 
                if (arg.symbol.isAbstractType)
                  arg.symbol setInfo // XXX, feedback. don't trackSymInfo here! 
                    TypeBounds(
                      lub(List(arg.symbol.info.bounds.lo, tparam.info.bounds.lo.subst(tparams, argtypes))),
                      glb(List(arg.symbol.info.bounds.hi, tparam.info.bounds.hi.subst(tparams, argtypes))))
              case _ =>
            }}
            TypeTree(owntype) setOriginal(tree) // setPos tree.pos
          } else if (tparams.length == 0) {
            if (onePointFourMode && (tpt1.symbol hasFlag JAVA)) tpt1
            else errorTree(tree, tpt1.tpe+" does not take type parameters")
          } else {
            //Console.println("\{tpt1}:\{tpt1.symbol}:\{tpt1.symbol.info}")
            if (settings.debug.value) Console.println(tpt1+":"+tpt1.symbol+":"+tpt1.symbol.info);//debug
            errorTree(tree, "wrong number of type arguments for "+tpt1.tpe+", should be "+tparams.length)
          }
        }
      }

      // begin typed1
      implicit val scopeKind = TypedScopeKind
      val sym: Symbol = tree.symbol
      if ((sym ne null) && (sym ne NoSymbol)) sym.initialize 
      //if (settings.debug.value && tree.isDef) log("typing definition of "+sym);//DEBUG
      tree match {
        case PackageDef(name, stats) =>
          assert(sym.moduleClass ne NoSymbol)
          val stats1 = newTyper(context.make(tree, sym.moduleClass, sym.info.decls))
            .typedStats(stats, NoSymbol)
          copy.PackageDef(tree, name, stats1) setType NoType

        case tree @ ClassDef(_, _, _, _) =>
          newTyper(context.makeNewScope(tree, sym)).typedClassDef(tree)

        case tree @ ModuleDef(_, _, _) =>
          newTyper(context.makeNewScope(tree, sym.moduleClass)).typedModuleDef(tree)

        case vdef @ ValDef(_, _, _, _) =>
          typedValDef(vdef)

        case ddef @ DefDef(_, _, _, _, _, _) => 
          newTyper(context.makeNewScope(tree, sym)).typedDefDef(ddef)

        case tdef @ TypeDef(_, _, _, _) =>
          newTyper(context.makeNewScope(tree, sym)).typedTypeDef(tdef)

        case ldef @ LabelDef(_, _, _) =>
          labelTyper(ldef).typedLabelDef(ldef)

        case DocDef(comment, defn) =>
          val ret = typed(defn, mode, pt)
          if ((comments ne null) && (defn.symbol ne null) && (defn.symbol ne NoSymbol)) comments(defn.symbol) = comment
          ret

	case Annotation(constr, elements) =>
	  val typedConstr = typed(constr, mode, WildcardType)
	  val typedElems = elements.map(typed(_, mode, WildcardType))
          (copy.Annotation(tree, typedConstr, typedElems)
	    setType typedConstr.tpe)

        case Annotated(annot, arg) =>
          typedAnnotated(annot, typed(arg, mode, pt))

        case tree @ Block(_, _) =>
          newTyper(context.makeNewScope(tree, context.owner)(BlockScopeKind(context.depth)))
            .typedBlock(tree, mode, pt)

        case Sequence(elems) =>
          checkRegPatOK(tree.pos, mode)
          val elems1 = List.mapConserve(elems)(elem => typed(elem, mode, pt))
          copy.Sequence(tree, elems1) setType pt

        case Alternative(alts) =>
          val alts1 = List.mapConserve(alts)(alt => typed(alt, mode | ALTmode, pt))
          copy.Alternative(tree, alts1) setType pt

        case Star(elem) =>
          checkRegPatOK(tree.pos, mode)
          val elem1 = typed(elem, mode, pt)
          copy.Star(tree, elem1) setType pt

        case Bind(name, body) =>
          typedBind(name, body)

        case UnApply(fun, args) =>
          val fun1 = typed(fun)
          val tpes = formalTypes(unapplyTypeList(fun.symbol, fun1.tpe), args.length)
          val args1 = List.map2(args, tpes)(typedPattern(_, _))
          copy.UnApply(tree, fun1, args1) setType pt

        case ArrayValue(elemtpt, elems) =>
          typedArrayValue(elemtpt, elems)

        case tree @ Function(_, _) =>
          if (tree.symbol == NoSymbol)
            tree.symbol = recycle(context.owner.newValue(tree.pos, nme.ANON_FUN_NAME)
              .setFlag(SYNTHETIC).setInfo(NoType))
          newTyper(context.makeNewScope(tree, tree.symbol)).typedFunction(tree, mode, pt)

        case Assign(lhs, rhs) =>
          typedAssign(lhs, rhs)

        case If(cond, thenp, elsep) =>
          typedIf(cond, thenp, elsep)

        case tree @ Match(selector, cases) =>
          if (selector == EmptyTree) {
            val arity = if (isFunctionType(pt)) pt.normalize.typeArgs.length - 1 else 1
            val params = for (i <- List.range(0, arity)) yield 
              ValDef(Modifiers(PARAM | SYNTHETIC), 
                  unit.fresh.newName(tree.pos, "x" + i + "$"), TypeTree(), EmptyTree)
            val ids = for (p <- params) yield Ident(p.name)
            val selector1 = atPos(tree.pos) { if (arity == 1) ids.head else gen.mkTuple(ids) }
            val body = copy.Match(tree, selector1, cases)
            typed1(atPos(tree.pos) { Function(params, body) }, mode, pt)
          } else {
            val selector1 = checkDead(typed(selector))
            val cases1 = typedCases(tree, cases, selector1.tpe.widen, pt)
            copy.Match(tree, selector1, cases1) setType ptOrLub(cases1 map (_.tpe))
          }

        case Return(expr) =>
          typedReturn(expr)

        case Try(block, catches, finalizer) =>
          val block1 = typed(block, pt)
          val catches1 = typedCases(tree, catches, ThrowableClass.tpe, pt)
          val finalizer1 = if (finalizer.isEmpty) finalizer
                           else typed(finalizer, UnitClass.tpe)
          copy.Try(tree, block1, catches1, finalizer1)
            .setType(ptOrLub(block1.tpe :: (catches1 map (_.tpe))))

        case Throw(expr) =>
          val expr1 = typed(expr, ThrowableClass.tpe)
          copy.Throw(tree, expr1) setType NothingClass.tpe

        case New(tpt: Tree) =>
          typedNew(tpt)

        case Typed(expr, Function(List(), EmptyTree)) =>
          typedEta(checkDead(typed1(expr, mode, pt)))

        case Typed(expr, tpt) =>
          if (treeInfo.isWildcardStarArg(tree)) {
            val expr1 = typed(expr, mode & stickyModes, seqType(pt))
            expr1.tpe.baseType(SeqClass) match {
              case TypeRef(_, _, List(elemtp)) =>
                copy.Typed(tree, expr1, tpt setType elemtp) setType elemtp
              case _ =>
                setError(tree)
            }
          } else {
            val tpt1 = typedType(tpt, mode)
            val expr1 = typed(expr, mode & stickyModes, tpt1.tpe.deconst)
            val owntype = 
              if ((mode & PATTERNmode) != 0) inferTypedPattern(tpt1.pos, tpt1.tpe, pt) 
              else tpt1.tpe
            //Console.println(typed pattern: "+tree+":"+", tp = "+tpt1.tpe+", pt = "+pt+" ==> "+owntype)//DEBUG
            copy.Typed(tree, expr1, tpt1) setType owntype
          }

        case TypeApply(fun, args) =>
          // @M: kind-arity checking is done here and in adapt, full kind-checking is in checkKindBounds (in Infer)        
          //@M! we must type fun in order to type the args, as that requires the kinds of fun's type parameters.
          // However, args should apparently be done first, to save context.undetparams. Unfortunately, the args
          // *really* have to be typed *after* fun. We escape from this classic Catch-22 by simply saving&restoring undetparams.

          // @M TODO: the compiler still bootstraps&all tests pass when this is commented out..
          //val undets = context.undetparams 
          
          // @M: fun is typed in TAPPmode because it is being applied to its actual type parameters
          val fun1 = typed(fun, funMode(mode) | TAPPmode, WildcardType) 
          val tparams = fun1.symbol.typeParams

          //@M TODO: val undets_fun = context.undetparams  ?
          // "do args first" (by restoring the context.undetparams) in order to maintain context.undetparams on the function side.
          
          // @M TODO: the compiler still bootstraps when this is commented out.. TODO: run tests
          //context.undetparams = undets
          
          // @M maybe the well-kindedness check should be done when checking the type arguments conform to the type parameters' bounds?          
          val args1 = if(args.length == tparams.length) map2Conserve(args, tparams) { 
                        //@M! the polytype denotes the expected kind
                        (arg, tparam) => typedHigherKindedType(arg, mode, polyType(tparam.typeParams, AnyClass.tpe)) 
                      } else { 
                      //@M  this branch is correctly hit for an overloaded polymorphic type. It also has to handle erroneous cases.
                      // Until the right alternative for an overloaded method is known, be very liberal, 
                      // typedTypeApply will find the right alternative and then do the same check as 
                      // in the then-branch above. (see pos/tcpoly_overloaded.scala)
                      // this assert is too strict: be tolerant for errors like trait A { def foo[m[x], g]=error(""); def x[g] = foo[g/*ERR: missing argument type*/] }
                      //assert(fun1.symbol.info.isInstanceOf[OverloadedType] || fun1.symbol.isError) //, (fun1.symbol,fun1.symbol.info,fun1.symbol.info.getClass,args,tparams))
                        List.mapConserve(args)(typedHigherKindedType(_, mode)) 
                      }

          //@M TODO: context.undetparams = undets_fun ?
          typedTypeApply(fun1, args1)

        case Apply(Block(stats, expr), args) =>
          typed1(atPos(tree.pos)(Block(stats, Apply(expr, args))), mode, pt)

        case Apply(fun, args) =>
          typedApply(fun, args)

        case ApplyDynamic(qual, args) =>
          val qual1 = typed(qual, AnyRefClass.tpe)
          val args1 = List.mapConserve(args)(arg => typed(arg, AnyRefClass.tpe))
          copy.ApplyDynamic(tree, qual1, args1) setType AnyRefClass.tpe

        case Super(qual, mix) =>
          typedSuper(qual, mix)

        case This(qual) =>
          typedThis(qual)

        case Select(qual @ Super(_, _), nme.CONSTRUCTOR) =>
          val qual1 = 
            typed(qual, EXPRmode | QUALmode | POLYmode | SUPERCONSTRmode, WildcardType)
          // the qualifier type of a supercall constructor is its first parent class
          typedSelect(qual1, nme.CONSTRUCTOR)

        case Select(qual, name) =>
          if (util.Statistics.enabled) selcnt += 1
          var qual1 = checkDead(typedQualifier(qual, mode))
          if (name.isTypeName) qual1 = checkStable(qual1)
          val tree1 = typedSelect(qual1, name)
          if (qual1.symbol == RootPackage) copy.Ident(tree1, name)
          else tree1

        case Ident(name) =>
          if (util.Statistics.enabled) idcnt += 1
          if ((name == nme.WILDCARD && (mode & (PATTERNmode | FUNmode)) == PATTERNmode) ||
              (name == nme.WILDCARD.toTypeName && (mode & TYPEmode) != 0))
            tree setType pt
          else 
            typedIdent(name)
          
        case Literal(value) =>
          tree setType (
            if (value.tag == UnitTag) UnitClass.tpe
            else mkConstantType(value))

        case SingletonTypeTree(ref) =>
          val ref1 = checkStable(
            typed(ref, EXPRmode | QUALmode | (mode & TYPEPATmode), AnyRefClass.tpe))
          tree setType ref1.tpe.resultType

        case SelectFromTypeTree(qual, selector) =>
          val qual1 = typedType(qual, mode)
          if (qual1.tpe.isVolatile) error(tree.pos, "illegal type selection from volatile type "+qual.tpe) 
          typedSelect(typedType(qual, mode), selector)

        case CompoundTypeTree(templ) =>
          typedCompoundTypeTree(templ)

        case AppliedTypeTree(tpt, args) =>
          typedAppliedTypeTree(tpt, args)

        case TypeBoundsTree(lo, hi) =>
          val lo1 = typedType(lo, mode)
          val hi1 = typedType(hi, mode)
          copy.TypeBoundsTree(tree, lo1, hi1) setType mkTypeBounds(lo1.tpe, hi1.tpe)

        case etpt @ ExistentialTypeTree(_, _) =>
          newTyper(context.makeNewScope(tree, context.owner)).typedExistentialTypeTree(etpt, mode)

        case TypeTree() =>
          // we should get here only when something before failed 
          // and we try again (@see tryTypedApply). In that case we can assign 
          // whatever type to tree; we just have to survive until a real error message is issued.
          tree setType AnyClass.tpe
        case EmptyTree if inIDE => EmptyTree // just tolerate it in the IDE.
        case _ =>
          throw new Error("unexpected tree: " + tree.getClass + "\n" + tree)//debug
      }
    }

    /**
     *  @param tree ...
     *  @param mode ...
     *  @param pt   ...
     *  @return     ...
     */
    def typed(tree: Tree, mode: Int, pt: Type): Tree =
      try {
        if (settings.debug.value)
          assert(pt ne null, tree)//debug
        if (context.retyping &&
            (tree.tpe ne null) && (tree.tpe.isErroneous || !(tree.tpe <:< pt))) {
          tree.tpe = null
          if (tree.hasSymbol) tree.symbol = NoSymbol
        }
        if (printTypings) println("typing "+tree+", "+context.undetparams+(mode & TYPEPATmode));//DEBUG
        def dropExistential(tp: Type): Type = tp match {
          case ExistentialType(tparams, tpe) => 
            if (settings.debug.value) println("drop ex "+tree+" "+tp)
            new SubstWildcardMap(tparams).apply(tp)
          case TypeRef(_, sym, _) if sym.isAliasType =>
            val tp0 = tp.normalize
            val tp1 = dropExistential(tp0)
            if (tp1 eq tp0) tp else tp1
          case _ => tp
        }
        var tree1 = if (tree.tpe ne null) tree else typed1(tree, mode, dropExistential(pt))
        if (printTypings) println("typed "+tree1+":"+tree1.tpe+", "+context.undetparams+", pt = "+pt);//DEBUG
       
        tree1.tpe = addAnnotations(tree1, tree1.tpe)

        val result = if (tree1.isEmpty || (inIDE && tree1.tpe == null)) tree1 else adapt(tree1, mode, pt)
        if (printTypings) println("adapted "+tree1+":"+tree1.tpe+" to "+pt+", "+context.undetparams);//DEBUG
//      if ((mode & TYPEmode) != 0) println("type: "+tree1+" has type "+tree1.tpe)
        result
      } catch {
        case ex: TypeError =>
          if (inIDE) throw ex
          tree.tpe = null
          //Console.println("caught "+ex+" in typed");//DEBUG
          reportTypeError(tree.pos, ex)
          setError(tree)
        case ex: Exception =>
//          if (settings.debug.value) // @M causes cyclic reference error
//            Console.println("exception when typing "+tree+", pt = "+pt)
          if ((context ne null) && (context.unit ne null) &&
              (context.unit.source ne null) && (tree ne null))
            logError("AT: " + (tree.pos).dbgString, ex);
          throw(ex)
      }

    def atOwner(owner: Symbol): Typer =
      newTyper(context.make(context.tree, owner))

    def atOwner(tree: Tree, owner: Symbol): Typer =
      newTyper(context.make(tree, owner))

    /** Types expression or definition <code>tree</code>.
     *
     *  @param tree ...
     *  @return     ...
     */
    def typed(tree: Tree): Tree = {
      val ret = typed(tree, EXPRmode, WildcardType)
      ret
    }

    /** Types expression <code>tree</code> with given prototype <code>pt</code>.
     *
     *  @param tree ...
     *  @param pt   ...
     *  @return     ...
     */
    def typed(tree: Tree, pt: Type): Tree =
      typed(tree, EXPRmode, pt)

    /** Types qualifier <code>tree</code> of a select node.
     *  E.g. is tree occurs in a context like <code>tree.m</code>.
     *
     *  @param tree ...
     *  @return     ...
     */
    def typedQualifier(tree: Tree, mode: Int): Tree =
      typed(tree, EXPRmode | QUALmode | POLYmode | mode & TYPEPATmode, WildcardType)

    def typedQualifier(tree: Tree): Tree = typedQualifier(tree, NOmode)

    /** Types function part of an application */
    def typedOperator(tree: Tree): Tree =
      typed(tree, EXPRmode | FUNmode | POLYmode | TAPPmode, WildcardType)

    /** Types a pattern with prototype <code>pt</code> */
    def typedPattern(tree: Tree, pt: Type): Tree =
      typed(tree, PATTERNmode, pt)

    /** Types a (fully parameterized) type tree */
    def typedType(tree: Tree, mode: Int): Tree =
      typed(tree, typeMode(mode), WildcardType)

    /** Types a (fully parameterized) type tree */
    def typedType(tree: Tree): Tree = typedType(tree, NOmode)

    /** Types a higher-kinded type tree -- pt denotes the expected kind*/
    def typedHigherKindedType(tree: Tree, mode: Int, pt: Type): Tree =
      if (pt.typeParams.isEmpty) typedType(tree, mode) // kind is known and it's *
      else typed(tree, HKmode, pt)
      
    def typedHigherKindedType(tree: Tree, mode: Int): Tree = 
      typed(tree, HKmode, WildcardType)
             
    def typedHigherKindedType(tree: Tree): Tree = typedHigherKindedType(tree, NOmode)

    /** Types a type constructor tree used in a new or supertype */
    def typedTypeConstructor(tree: Tree, mode: Int): Tree = {
      val result = typed(tree, typeMode(mode) | FUNmode, WildcardType)
      val restpe = result.tpe.normalize
      if (!phase.erasedTypes && restpe.isInstanceOf[TypeRef] && !restpe.prefix.isStable) {
        error(tree.pos, restpe.prefix+" is not a legal prefix for a constructor")
      }
      result setType restpe // @M: normalization is done during erasure
    }

    def typedTypeConstructor(tree: Tree): Tree = typedTypeConstructor(tree, NOmode)

    def computeType(tree: Tree, pt: Type): Type = {
      val tree1 = typed(tree, pt)
      transformed(tree) = tree1
      packedType(tree1, context.owner)
    }

    def transformedOrTyped(tree: Tree, pt: Type): Tree = transformed.get(tree) match {
      case Some(tree1) => transformed -= tree; tree1
      case None => typed(tree, pt)
    }

/*
    def convertToTypeTree(tree: Tree): Tree = tree match {
      case TypeTree() => tree
      case _ => TypeTree(tree.tpe)
    }
*/
    /* -- Views --------------------------------------------------------------- */

    private def tparamsToWildcards(tp: Type, tparams: List[Symbol]) =
      tp.instantiateTypeParams(tparams, tparams map (t => WildcardType))

    private def depoly(tp: Type): Type = tp match {
      case PolyType(tparams, restpe) => tparamsToWildcards(restpe, tparams)
      case _ => tp
    }

    private def containsError(tp: Type): Boolean = tp match {
      case PolyType(tparams, restpe) => containsError(restpe)
      case MethodType(formals, restpe) => (formals exists (_.isError)) || containsError(restpe)
      case _ => tp.isError
    }

    private def dominates(dtor: Type, dted: Type): Boolean = {
      def core(tp: Type): Type = tp.normalize match {
        case RefinedType(parents, defs) => intersectionType(parents map core, tp.typeSymbol.owner)
        case AnnotatedType(attribs, tp, selfsym) => core(tp)
        case ExistentialType(tparams, result) => core(result).subst(tparams, tparams map (t => core(t.info.bounds.hi)))
        case PolyType(tparams, result) => core(result).subst(tparams, tparams map (t => core(t.info.bounds.hi)))
        case _ => tp
      }
      def stripped(tp: Type): Type = {
        val tparams = freeTypeParametersNoSkolems.collect(tp)
        tp.subst(tparams, tparams map (t => WildcardType))
      }
      def sum(xs: List[Int]) = (0 /: xs)(_ + _)
      def complexity(tp: Type): Int = tp.normalize match {
        case NoPrefix =>
          0
        case SingleType(pre, sym) => 
          if (sym.isPackage) 0 else complexity(tp.widen)
        case TypeRef(pre, sym, args) => 
          complexity(pre) + sum(args map complexity) + 1
        case RefinedType(parents, _) => 
          sum(parents map complexity) + 1
        case _ => 
          1
      }
      def overlaps(tp1: Type, tp2: Type): Boolean = (tp1, tp2) match {
        case (RefinedType(parents, _), _) => parents exists (overlaps(_, tp2))
        case (_, RefinedType(parents, _)) => parents exists (overlaps(tp1, _))
        case _ => tp1.typeSymbol == tp2.typeSymbol
      }
      val dtor1 = stripped(core(dtor))
      val dted1 = stripped(core(dted))
      overlaps(dtor1, dted1) && (dtor1 =:= dted1 || complexity(dtor1) > complexity(dted1))
    }

    /** Try to construct a typed tree from given implicit info with given
     *  expected type.
     *
     *  @param pos     Position for error reporting
     *  @param info    The given implicit info describing the implicit definition
     *  @param pt0     The unnormalized expected type
     *  @param pt      The normalized expected type
     *  @param isLocal Is implicit definition visible without prefix?
     *  @return        A typed tree if the implicit info can be made to conform
     *                 to <code>pt</code>, EmptyTree otherwise.
     *  @pre           <code>info.tpe</code> does not contain an error
     */
    private def typedImplicit(pos: Position, info: ImplicitInfo, pt0: Type, pt: Type, isLocal: Boolean): Tree =
       context.openImplicits find (dominates(pt, _)) match {
         case Some(pending) =>
           throw DivergentImplicit
           EmptyTree
         case None =>
           try {
             context.openImplicits = pt :: context.openImplicits
             typedImplicit0(pos, info, pt0, pt, isLocal)
           } catch {
             case DivergentImplicit => 
               if (context.openImplicits.tail.isEmpty) {
                 if (!(pt.isErroneous))
                   context.unit.error(
                     pos, "diverging implicit expansion for type "+pt+"\nstarting with "+
                     info.sym+info.sym.locationString)
                 EmptyTree
               } else {
                 throw DivergentImplicit
               }
           } finally {
             context.openImplicits = context.openImplicits.tail
           }
       }

    private def typedImplicit0(pos: Position, info: ImplicitInfo, pt0: Type, pt: Type, isLocal: Boolean): Tree = {
      def isStable(tp: Type): Boolean = tp match {
        case TypeRef(pre, sym, _) => sym.isPackageClass || sym.isModuleClass && isStable(pre)
        case _ => tp.isStable
      }
      // println("typed impl for "+pt+"? "+info.name+":"+info.tpe+(isPlausiblyCompatible(info.tpe, pt))+isCompatible(depoly(info.tpe), pt)+isStable(info.pre))
      if (isPlausiblyCompatible(info.tpe, pt) && isCompatible(depoly(info.tpe), pt) && isStable(info.pre)) {
        val tree = atPos(pos) {
          if (info.pre == NoPrefix/*isLocal*/) Ident(info.name)
          else Select(gen.mkAttributedQualifier(info.pre), info.name)
        }
        // println("typed impl?? "+info.name+":"+info.tpe+" ==> "+tree+" with "+pt)
        def fail(reason: String): Tree = {
          if (settings.XlogImplicits.value)
            inform(tree+" is not a valid implicit value for "+pt+" because:\n"+reason)
          EmptyTree
        }
        try {
          // if (!isLocal) tree setSymbol info.sym
          val isView = pt0 match {
            case MethodType(_, _) | PolyType(_, _) => true
            case _ => false
          }
          val tree1 = 
            if (isView) 
              typed1(Apply(tree, List(Ident("<argument>") setType pt0.paramTypes.head)), EXPRmode, pt0.resultType)
            else
              typed1(tree, EXPRmode, pt)
          //if (settings.debug.value) println("typed implicit "+tree1+":"+tree1.tpe+", pt = "+pt)
          val tree2 = if (isView) (tree1: @unchecked) match { case Apply(fun, _) => fun }
                      else adapt(tree1, EXPRmode, pt)
          //if (settings.debug.value) println("adapted implicit "+tree1.symbol+":"+tree2.tpe+" to "+pt)("adapted implicit "+tree1.symbol+":"+tree2.tpe+" to "+pt)
          def hasMatchingSymbol(tree: Tree): Boolean = (tree.symbol == info.sym) || {
            tree match {
              case Apply(fun, _) => hasMatchingSymbol(fun)
              case TypeApply(fun, _) => hasMatchingSymbol(fun)
              case Select(pre, name) => name == nme.apply && pre.symbol == info.sym
              case _ => false
            }
          }
          if (tree2.tpe.isError) EmptyTree
          else if (hasMatchingSymbol(tree1)) tree2
          else if (settings.XlogImplicits.value) 
            fail("candidate implicit "+info.sym+info.sym.locationString+
                 " is shadowed by other implicit: "+tree1.symbol+tree1.symbol.locationString)
          else EmptyTree
        } catch {
          case ex: TypeError => fail(ex.getMessage())
        }
      } else EmptyTree
    }

    /** Infer implicit argument or view.
     *
     *  @param  pos             position for error reporting
     *  @param  pt0             the expected type of the implicit
     *  @param  isView          are we searching for a view? (this affects the error message)
     *  @param  reportAmbiguous should ambiguous errors be reported?
     *                          False iff we search for a view to find out
     *                          whether one type is coercible to another
     *  @return                 ...
     *  @see                    <code>isCoercible</code>
     */
    private def inferImplicit(pos: Position, pt0: Type, reportAmbiguous: Boolean): Tree = {
      val pt = normalize(pt0)
      val isView = pt0.isInstanceOf[MethodType]

      if (util.Statistics.enabled) implcnt += 1
      val startTime = if (util.Statistics.enabled) currentTime else 0l

      val tc = newTyper(context.makeImplicit(reportAmbiguous))

      def ambiguousImplicitError(info1: ImplicitInfo, info2: ImplicitInfo, 
                                 pre1: String, pre2: String, trailer: String) =
        if (!info1.tpe.isErroneous && !info2.tpe.isErroneous) {
          val coreMsg = 
            pre1+" "+info1.sym+info1.sym.locationString+" of type "+info1.tpe+"\n "+
            pre2+" "+info2.sym+info2.sym.locationString+" of type "+info2.tpe+"\n "+
            trailer
          error(pos, 
            if (isView) {
              val found = pt.typeArgs(0)
              val req = pt.typeArgs(1)
              typeErrorMsg(found, req)+
              "\nNote that implicit conversions are not applicable because they are ambiguous:\n "+
              coreMsg+"are possible conversion functions from "+ found+" to "+req
            } else {
              "ambiguous implicit values:\n "+coreMsg + "match expected type "+pt
            })
        }

      /** Search list of implicit info lists for one matching prototype
       *  <code>pt</code>. If found return a tree from found implicit info
       *  which is typed with expected type <code>pt</code>.
       *  Otherwise return EmptyTree
       *
       *  @param implicitInfoss The given list of lists of implicit infos
       *  @param isLocal        Is implicit definition visible without prefix?
       *                        If this is the case then symbols in preceding lists shadow 
       *                        symbols of the same name in succeeding lists.
       *  @return               ...
       */
      def searchImplicit(implicitInfoss: List[List[ImplicitInfo]], isLocal: Boolean): Tree = {
        def isSubClassOrObject(sym1: Symbol, sym2: Symbol): Boolean =
          sym1 != NoSymbol && (sym1 isSubClass sym2) ||
          sym1.isModuleClass && isSubClassOrObject(sym1.linkedClassOfClass, sym2) ||
          sym2.isModuleClass && isSubClassOrObject(sym1, sym2.linkedClassOfClass)
        def improves(info1: ImplicitInfo, info2: ImplicitInfo) =
          (info2 == NoImplicitInfo) ||
          (info1 != NoImplicitInfo) &&
          isStrictlyMoreSpecific(info1.tpe, info2.tpe)
        val shadowed = new HashSet[Name](8)
        def hasExplicitResultType(sym: Symbol) = {
          def hasExplicitRT(tree: Tree) = tree match {
            case ValDef(_, _, tpt, _) => !tpt.isEmpty
            case DefDef(_, _, _, _, tpt, _) => !tpt.isEmpty
            case _ => false
          }
          sym.rawInfo match {
            case tc: TypeCompleter => hasExplicitRT(tc.tree)
            case PolyType(_, tc: TypeCompleter) => hasExplicitRT(tc.tree)
            case _ => true
          }
        }
        def comesBefore(sym: Symbol, owner: Symbol) =
          sym.pos.offset.getOrElse(0) < owner.pos.offset.getOrElse(Integer.MAX_VALUE) &&
          !(owner.ownerChain contains sym)

        /** Should implicit definition symbol `sym' be considered for applicability testing?
         *  This is the case if one of the following holds:
         *   - the symbol's type is initialized
         *   - the symbol comes from a classfile
         *   - the symbol comes from a different sourcefile than the current one
         *   - the symbol's definition comes before, and does not contain the closest enclosing definition,
         *   - the symbol's definition is a val, var, or def with an explicit result type
         *  The aim of this method is to prevent premature cyclic reference errors
         *  by computing the types of only those implicitis for which one of these 
         *  conditions is true.
         */
        def isValid(sym: Symbol) = {
          sym.isInitialized ||
          sym.sourceFile == null ||
          (sym.sourceFile ne context.unit.source.file) || 
          hasExplicitResultType(sym) ||
          comesBefore(sym, context.owner)
        }
        val lateImpls = new ListBuffer[Symbol]
        def isApplicable(info: ImplicitInfo): Boolean =
          !containsError(info.tpe) &&
          !(isLocal && shadowed.contains(info.name)) &&
          (!isView || info.sym != Predef_identity) &&
          tc.typedImplicit(pos, info, pt0, pt, isLocal) != EmptyTree
        def applicableInfos(is: List[ImplicitInfo]): List[ImplicitInfo] = {
          val applicable = new ListBuffer[ImplicitInfo]
          for (i <- is)
            if (!isValid(i.sym)) lateImpls += i.sym
            else if (isApplicable(i)) applicable += i
          if (isLocal)
            for (i <- is) shadowed addEntry i.name
          applicable.toList
        }
        val applicable = implicitInfoss flatMap applicableInfos
        if (applicable.isEmpty && !lateImpls.isEmpty) {
          infer.setAddendum(pos, () =>
            "\n Note: implicit "+lateImpls.first+" is not applicable here"+
            "\n because it comes after the application point and it lacks an explicit result type")
        }
        val best = (NoImplicitInfo /: applicable) ((best, alt) => if (improves(alt, best)) alt else best)
        if (best == NoImplicitInfo) EmptyTree
        else {
          val competing = applicable dropWhile (alt => best == alt || improves(best, alt))
          if (!competing.isEmpty) ambiguousImplicitError(best, competing.head, "both", "and", "")
          for (alt <- applicable)
            if (alt.sym.owner != best.sym.owner && isSubClassOrObject(alt.sym.owner, best.sym.owner)) {
              ambiguousImplicitError(best, alt, 
                                     "most specific definition is:",
                                     "yet alternative definition  ",
                                     "is defined in a subclass.\n Both definitions ")
            }
          tc.typedImplicit(pos, best, pt0, pt, isLocal)
        }
      }

      def implicitsOfType(tp: Type): List[List[ImplicitInfo]] = {
        def getParts(tp: Type, s: collection.jcl.Set[Type]) {
          tp match {
            case TypeRef(pre, sym, args) if (!sym.isPackageClass) =>
              for (bc <- sym.info.baseClasses)
                if (sym.isClass) s add (tp.baseType(bc))
              getParts(pre, s)
              for (arg <- args) getParts(arg, s)
            case ThisType(_) =>
              getParts(tp.widen, s)
            case _: SingletonType =>
              getParts(tp.widen, s)
            case RefinedType(ps, _) =>
              for (p <- ps) getParts(p, s)
            case AnnotatedType(_, t, _) =>
	      getParts(t, s)
            case _ =>
          }
        }
        val tps = new collection.jcl.LinkedHashSet[Type]
        getParts(tp, tps)
        tps.elements.map(implicitsOfClass).toList
      }

      def implicitsOfClass(tp: Type): List[ImplicitInfo] = tp match {
        case TypeRef(pre, clazz, _) =>
          clazz.initialize.linkedClassOfClass.info.members.toList.filter(_.hasFlag(IMPLICIT)) map
            (sym => new ImplicitInfo(sym.name, pre.memberType(clazz.linkedModuleOfClass), sym))
        case _ =>
          List()
      }

      def implicitManifest(pt: Type): Tree = {
        // test below is designed so that ManifestClass need not be loaded
        // (because it's not available everywhere)
        if (pt.typeSymbol.fullNameString == "scala.reflect.Manifest")
          pt match {
            case TypeRef(_, ManifestClass, List(arg)) => manifestOfType(pos, arg)
          }
        else EmptyTree
      }
            
      var tree = searchImplicit(context.implicitss, true)
      if (tree == EmptyTree) tree = searchImplicit(implicitsOfType(pt), false)
      if (tree == EmptyTree) tree = implicitManifest(pt)
      if (util.Statistics.enabled)
        impltime = impltime + currentTime - startTime
      tree
    }
    
    /** Creates a tree that calls the relevant factory method in object
      * reflect.Manifest for type 'tp'. An EmptyTree is returned if  */
    def manifestOfType(pos: Position, tp: Type): Tree = {
      
      /** Creates a tree that calls the factory method called constructor in object reflect.Manifest */
      def manifestFactoryCall(constructor: String, args: Tree*): Tree =
        if (args contains EmptyTree) EmptyTree
        else 
          typed(atPos(pos) {
            Apply(
              TypeApply(
                Select(gen.mkAttributedRef(ManifestModule), constructor),
                List(TypeTree(tp))
              ),
              args.toList
            )
          })
      
      /** Re-wraps a type in a manifest before calling inferImplicit on the result */
      def findManifest(tp: Type): Tree = 
        inferImplicit(pos, appliedType(ManifestClass.typeConstructor, List(tp)), true)
        
      tp.normalize match {
        case ThisType(_) | SingleType(_, _) =>
          manifestFactoryCall("singleType", gen.mkAttributedQualifier(tp)) 
        case ConstantType(value) =>
          findManifest(tp.deconst)
        case TypeRef(pre, sym, args) =>
          if (sym.isClass) {
            val suffix = gen.mkClassOf(tp) :: (args map findManifest)
            manifestFactoryCall(
              "classType", 
              (if ((pre eq NoPrefix) || pre.typeSymbol.isStaticOwner) suffix
               else findManifest(pre) :: suffix): _*)
          }
          else if (sym.isTypeParameterOrSkolem) {
            EmptyTree  // a manifest should have been found by normal searchImplicit
          }
          else {
            manifestFactoryCall(
              "abstractType", 
              findManifest(pre) :: Literal(sym.name.toString) :: findManifest(tp.bounds.hi) :: (args map findManifest): _*)
          }
        case RefinedType(parents, decls) =>
          // refinement is not generated yet
          if (parents.length == 1) findManifest(parents.head)
          else manifestFactoryCall("intersectionType", parents map findManifest: _*)
        case _ =>
          EmptyTree
      }
      
    }

    def applyImplicitArgs(tree: Tree): Tree = tree.tpe match {
      case MethodType(formals, _) =>
        def implicitArg(pt: Type) = {
          val arg = inferImplicit(tree.pos, pt, true)
          if (arg != EmptyTree) arg
          else errorTree(tree, "no implicit argument matching parameter type "+pt+" was found.")
        }
        Apply(tree, formals map implicitArg) setPos tree.pos
      case ErrorType =>
        tree
    }
  }
}
