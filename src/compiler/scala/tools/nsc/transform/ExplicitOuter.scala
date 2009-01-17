/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author Martin Odersky
 */
// $Id: ExplicitOuter.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc.transform

import symtab._
import Flags._
import scala.collection.mutable.{HashMap, ListBuffer}
import matching.{TransMatcher, PatternNodes, CodeFactory, ParallelMatching}

/** This class ...
 *
 *  @author  Martin Odersky
 *  @version 1.0
 */
abstract class ExplicitOuter extends InfoTransform with TransMatcher with PatternNodes with CodeFactory with ParallelMatching with TypingTransformers {
  import global._
  import definitions._
  import posAssigner.atPos

  /** The following flags may be set by this phase: */
  override def phaseNewFlags: Long = notPRIVATE | notPROTECTED | lateFINAL

  /** the name of the phase: */
  val phaseName: String = "explicitouter"

  /** This class does not change linearization */
  override def changesBaseClasses = false

  protected def newTransformer(unit: CompilationUnit): Transformer =
    new ExplicitOuterTransformer(unit)

  /** Is given <code>clazz</code> an inner class? */
  private def isInner(clazz: Symbol) =
    !clazz.isPackageClass && !clazz.outerClass.isStaticOwner

  /** Does given <code>clazz</code> define an outer field? */
  def hasOuterField(clazz: Symbol) = {
    def hasSameOuter(parent: Type) =
      parent.typeSymbol.isClass &&
      clazz.owner.isClass &&
      clazz.owner == parent.typeSymbol.owner &&
      parent.prefix =:= clazz.owner.thisType
    isInner(clazz) && !clazz.isTrait &&
    (clazz.info.parents.isEmpty || !hasSameOuter(clazz.info.parents.head))
  }

  private def outerField(clazz: Symbol): Symbol = {
    val result = clazz.info.member(nme.getterToLocal(nme.OUTER))
    if (result == NoSymbol)
      assert(false, "no outer field in "+clazz+clazz.info.decls+" at "+phase)
    result
  }

  def outerAccessor(clazz: Symbol): Symbol = {
    val firstTry = clazz.info.decl(clazz.expandedName(nme.OUTER))
    if (firstTry != NoSymbol && firstTry.outerSource == clazz) firstTry
    else {
      var e = clazz.info.decls.elems
      while ((e ne null) && e.sym.outerSource != clazz) e = e.next
      if (e ne null) e.sym else NoSymbol
    }
  }

  /** <p>
   *    The type transformation method:
   *  </p>
   *  <ol>
   *    <li>
   *      Add an outer parameter to the formal parameters of a constructor
   *      in a inner non-trait class;
   *    </li>
   *    <li>
   *      Add a protected <code>$outer</code> field to an inner class which is
   *      not a trait.
   *    </li>
   *    <li>
   *      <p>
   *        Add an outer accessor <code>$outer$$C</code> to every inner class
   *        with fully qualified name <code>C</code> that is not an interface.
   *        The outer accesssor is abstract for traits, concrete for other
   *        classes.
   *      </p>
   *      <p>
   *        3a. Also add overriding accessor defs to every class that inherits
   *        mixin classes with outer accessor defs (unless the superclass
   *        already inherits the same mixin).
   *      </p>
   *    </li>
   *    <li>
   *      Make all super accessors and modules in traits non-private, mangling
   *      their names.
   *    </li>
   *    <li>
   *      Remove protected flag from all members of traits.
   *    </li>
   *  </ol>
   */
  def transformInfo(sym: Symbol, tp: Type): Type = tp match {
    case MethodType(formals, restpe1) =>
      val restpe = transformInfo(sym, restpe1)
      if (sym.owner.isTrait && ((sym hasFlag SUPERACCESSOR) || sym.isModule)) { // 5 
        sym.makeNotPrivate(sym.owner)
      }
      // moved form the term transformer
      if (sym.owner.isTrait && (sym hasFlag (ACCESSOR | SUPERACCESSOR)))
        sym.makeNotPrivate(sym.owner); //(2)
      if (sym.owner.isTrait && (sym hasFlag PROTECTED)) sym setFlag notPROTECTED // 6
      if (sym.isClassConstructor && isInner(sym.owner)) // 1
        MethodType(sym.owner.outerClass.thisType :: formals, restpe)
      else if (restpe ne restpe1)
        MethodType(formals, restpe)
      else tp
    case ClassInfoType(parents, decls, clazz) =>
      var decls1 = decls
      if (isInner(clazz) && !(clazz hasFlag INTERFACE)) {
        decls1 = newScope(decls.toList)
        val outerAcc = clazz.newMethod(clazz.pos, nme.OUTER) // 3
        outerAcc.expandName(clazz)
        val restpe = if (clazz.isTrait) clazz.outerClass.tpe else clazz.outerClass.thisType
        decls1 enter clazz.newOuterAccessor(clazz.pos).setInfo(MethodType(List(), restpe))
        if (hasOuterField(clazz)) { //2
          val access = if (clazz.isFinal) PRIVATE | LOCAL else PROTECTED
          decls1 enter (
            clazz.newValue(clazz.pos, nme.getterToLocal(nme.OUTER))
            setFlag (SYNTHETIC | PARAMACCESSOR | access)
            setInfo clazz.outerClass.thisType)
        }
      }
      if (!clazz.isTrait && !parents.isEmpty) {
        for (val mc <- clazz.mixinClasses) {
          val mixinOuterAcc: Symbol = atPhase(phase.next)(outerAccessor(mc))
          if (mixinOuterAcc != NoSymbol) {
            if (decls1 eq decls) decls1 = newScope(decls.toList)
            val newAcc = mixinOuterAcc.cloneSymbol(clazz) 
            newAcc.resetFlag(DEFERRED)
            newAcc.setInfo(clazz.thisType.memberType(mixinOuterAcc))
            decls1 enter newAcc 
          }
        }
      }
      if (decls1 eq decls) tp else ClassInfoType(parents, decls1, clazz)
    case PolyType(tparams, restp) =>
      val restp1 = transformInfo(sym, restp)
      if (restp eq restp1) tp else PolyType(tparams, restp1)

    case _ =>
        tp
  }

  /** A base class for transformers that maintain <code>outerParam</code>
   *  values for outer parameters of constructors.
   *  The class provides methods for referencing via outer.
   */
  abstract class OuterPathTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {

    /** The directly enclosing outer parameter, if we are in a constructor */
    protected var outerParam: Symbol = NoSymbol

    /** The first outer selection from currently transformed tree.
     *  The result is typed but not positioned.
     */
    protected def outerValue: Tree =
      if (outerParam != NoSymbol) gen.mkAttributedIdent(outerParam)
      else outerSelect(gen.mkAttributedThis(currentClass))

    /** Select and apply outer accessor from 'base'
     *  The result is typed but not positioned.
     *  If the outer access is from current class and current class is final
     *  take outer field instead of accessor
     */
    private def outerSelect(base: Tree): Tree = {
      val outerAcc = outerAccessor(base.tpe.typeSymbol.toInterface)
      val currentClass = this.currentClass //todo: !!! if this line is removed, we get a build failure that protected$currentClass need an override modifier
      // outerFld is the $outer field of the current class, if the reference can
      // use it (i.e. reference is allowed to be of the form this.$outer),
      // otherwise it is NoSymbol
      val outerFld =         
        if (outerAcc.owner == currentClass && 
            base.tpe =:= currentClass.thisType &&
            outerAcc.owner.isFinal) 
          outerField(currentClass).suchThat(_.owner == currentClass)
        else
          NoSymbol
      val path = 
        if (outerFld != NoSymbol) Select(base, outerFld)
        else Apply(Select(base, outerAcc), List())
      localTyper.typed(path)
    }

    /** The path
     *  <blockquote><pre>`base'.$outer$$C1 ... .$outer$$Cn</pre></blockquote>
     *  which refers to the outer instance of class <code>to</code> of
     *  value <code>base</code>. The result is typed but not positioned.
     *
     *  @param base ...
     *  @param from ...
     *  @param to   ...
     *  @return     ...
     */
    protected def outerPath(base: Tree, from: Symbol, to: Symbol): Tree = {
      //Console.println("outerPath from "+from+" to "+to+" at "+base+":"+base.tpe)
      //assert(base.tpe.widen.baseType(from.toInterface) != NoType, ""+base.tpe.widen+" "+from.toInterface)//DEBUG
      if (from == to || from.isImplClass && from.toInterface == to) base
      else outerPath(outerSelect(base), from.outerClass, to)
    }

    override def transform(tree: Tree): Tree = {
      val savedOuterParam = outerParam
      try {
        tree match {
          case Template(_, _, _) =>
            outerParam = NoSymbol
          case DefDef(_, _, _, vparamss, _, _) =>
            if (tree.symbol.isClassConstructor && isInner(tree.symbol.owner)) {
              outerParam = vparamss.head.head.symbol
              assert(outerParam.name startsWith nme.OUTER, outerParam.name)
            }
          case _ =>
        }
        super.transform(tree)
      } catch {//debug
        case ex: Throwable =>
          Console.println("exception when transforming " + tree)
          //Console.println(ex.getMessage)
          //Console.println(ex.printStackTrace())
          //System.exit(-1);
          throw ex
      } finally {
        outerParam = savedOuterParam
      }
    }
  }

  /** <p>
   *    The phase performs the following transformations on terms:
   *  </p>
   *  <ol>
   *    <li> <!-- 1 -->
   *      <p>
   *        An class which is not an interface and is not static gets an outer
   *        accessor (@see outerDefs).
   *      </p>
   *      <p>
   *        1a. A class which is not a trait gets an outer field.
   *      </p>
   *    </li>
   *    <li> <!-- 4 -->
   *      A constructor of a non-trait inner class gets an outer parameter.
   *    </li>
   *    <li> <!-- 5 -->
   *      A reference <code>C.this</code> where <code>C</code> refers to an
   *      outer class is replaced by a selection
   *      <code>this.$outer$$C1</code> ... <code>.$outer$$Cn</code> (@see outerPath)
   *    </li>
   *    <li>
   *    </li>
   *    <li> <!-- 7 -->
   *      A call to a constructor Q.<init>(args) or Q.$init$(args) where Q != this and
   *      the constructor belongs to a non-static class is augmented by an outer argument.
   *      E.g. <code>Q.&lt;init&gt;(OUTER, args)</code> where <code>OUTER</code>
   *      is the qualifier corresponding to the singleton type <code>Q</code>.
   *    </li>
   *    <li>
   *      A call to a constructor <code>this.&lt;init&gt;(args)</code> in a
   *      secondary constructor is augmented to <code>this.&lt;init&gt;(OUTER, args)</code>
   *      where <code>OUTER</code> is the last parameter of the secondary constructor.
   *    </li>
   *    <li> <!-- 9 -->
   *      Remove <code>private</code> modifier from class members <code>M</code>
   *      that are accessed from an inner class.
   *    </li>
   *    <li> <!-- 10 -->
   *      Remove <code>protected</code> modifier from class members <code>M</code>
   *      that are accessed without a super qualifier accessed from an inner
   *      class or trait.
   *    </li>
   *    <li> <!-- 11 -->
   *      Remove <code>private</code> and <code>protected</code> modifiers
   *      from type symbols
   *    </li>
   *    <li> <!-- 12 -->
   *      Remove <code>private</code> modifiers from members of traits
   *    </li>
   *  </ol>
   *  <p>
   *    Note: The whole transform is run in phase <code>explicitOuter.next</code>.
   *  </p>
   */
  class ExplicitOuterTransformer(unit: CompilationUnit) extends OuterPathTransformer(unit) {

    /** The definition tree of the outer accessor of current class
     */
    def outerFieldDef: Tree = {
      val outerFld = outerField(currentClass)
      ValDef(outerFld, EmptyTree)
    }

    /** The definition tree of the outer accessor of current class
     */
    def outerAccessorDef: Tree = {
      val outerAcc = outerAccessor(currentClass)
      var rhs = if (outerAcc.isDeferred) EmptyTree
                else Select(This(currentClass), outerField(currentClass))
      localTyper.typed {
        atPos(currentClass.pos) {
          DefDef(outerAcc, {vparamss => rhs})
        }
      }
    }

    /** The definition tree of the outer accessor for class
     * <code>mixinClass</code>.
     *
     *  @param mixinClass The mixin class which defines the abstract outer
     *                    accessor which is implemented by the generated one.
     *  @pre mixinClass is an inner class
     */
    def mixinOuterAccessorDef(mixinClass: Symbol): Tree = {
      val outerAcc = outerAccessor(mixinClass).overridingSymbol(currentClass)
      assert(outerAcc != NoSymbol)
      val path = 
        if (mixinClass.owner.isTerm) gen.mkAttributedThis(mixinClass.owner.enclClass)
        else gen.mkAttributedQualifier(currentClass.thisType.baseType(mixinClass).prefix)
      val rhs = ExplicitOuterTransformer.this.transform(path)
      rhs.setPos(currentClass.pos) // see note below
      localTyper.typed {
        atPos(currentClass.pos) {  
          // @S: atPos not good enough because of nested atPos in DefDef method, which gives position from wrong class!
          DefDef(outerAcc, {vparamss=>rhs}).setPos(currentClass.pos)
        }
      }
    }

    /** The main transformation method */
    override def transform(tree: Tree): Tree = {

      val sym = tree.symbol
      if ((sym ne null) && sym.isType) {//(9)
        if (sym hasFlag PRIVATE) sym setFlag notPRIVATE
        if (sym hasFlag PROTECTED) sym setFlag notPROTECTED
      }
      tree match {
        case Template(parents, self, decls) =>
          val newDefs = new ListBuffer[Tree]
          atOwner(tree, currentOwner) {
            if (!(currentClass hasFlag INTERFACE) || (currentClass hasFlag lateINTERFACE)) {
              if (isInner(currentClass)) {
                if (hasOuterField(currentClass))
                  newDefs += outerFieldDef // (1a)
                newDefs += outerAccessorDef // (1)
              }
              if (!currentClass.isTrait)
                for (val mc <- currentClass.mixinClasses)
                  if (outerAccessor(mc) != NoSymbol)
                    newDefs += mixinOuterAccessorDef(mc)
            }
          }
          super.transform(
            copy.Template(tree, parents, self, 
                          if (newDefs.isEmpty) decls else decls ::: newDefs.toList)
          )
        case DefDef(mods, name, tparams, vparamss, tpt, rhs) =>
          if (sym.isClassConstructor) {
            rhs match {
              case Literal(_) =>
                Predef.error("unexpected case") //todo: remove
              case _ =>
                val clazz = sym.owner
                val vparamss1 =
                  if (isInner(clazz)) { // (4)
                    val outerParam =
                      sym.newValueParameter(sym.pos, nme.OUTER) setInfo outerField(clazz).info
                    ((ValDef(outerParam) setType NoType) :: vparamss.head) :: vparamss.tail
                  } else vparamss
                super.transform(copy.DefDef(tree, mods, name, tparams, vparamss1, tpt, rhs))
            }
          } else
            super.transform(tree)

        case This(qual) =>
          if (sym == currentClass || (sym hasFlag MODULE) && sym.isStatic) tree
          else atPos(tree.pos)(outerPath(outerValue, currentClass.outerClass, sym)) // (5)

        case Select(qual, name) =>
          if (currentClass != sym.owner/* && currentClass != sym.moduleClass*/) // (3)
            sym.makeNotPrivate(sym.owner)
          val qsym = qual.tpe.widen.typeSymbol
          if ((sym hasFlag PROTECTED) && //(4)
              (qsym.isTrait || !(qual.isInstanceOf[Super] || (qsym isSubClass currentClass))))
            sym setFlag notPROTECTED
          super.transform(tree)

        case Apply(sel @ Select(qual, name), args)
          if (name == nme.CONSTRUCTOR && isInner(sel.symbol.owner)) =>
            val outerVal = atPos(tree.pos) {
              if (qual.isInstanceOf[This]) { // it's a call between constructors of same class
                assert(outerParam != NoSymbol)
                outerValue
              } else {
                var pre = qual.tpe.prefix
                if (pre == NoPrefix) pre = sym.owner.outerClass.thisType
                gen.mkAttributedQualifier(pre)
              }
            }
            super.transform(copy.Apply(tree, sel, outerVal :: args))

        case mch @ Match(selector, cases) => // <----- transmatch hook
          val tid = if (settings.debug.value) {
            val q = unit.fresh.newName(mch.pos, "tidmark")
            Console.println("transforming patmat with tidmark "+q+" ncases = "+cases.length)
            q
          } else null


          var nselector = transform(selector)

          def makeGuardDef(vs:List[Symbol], guard:Tree) = {
            import symtab.Flags._
            val gdname = cunit.fresh.newName(guard.pos, "gd")
            val fmls = new ListBuffer[Type]
            val method = currentOwner.newMethod(mch.pos, gdname) setFlag (SYNTHETIC) 
            var vs1 = vs; while (vs1 ne Nil) {
              fmls += vs1.head.tpe
              vs1 = vs1.tail
            }
            val tpe    = new MethodType(fmls.toList, definitions.BooleanClass.tpe)
            method setInfo tpe
            localTyper.
            typed { 
              DefDef(method, 
                     {vparamss => 
                        new ChangeOwnerTraverser(currentOwner, method).traverse(guard);
                        new TreeSymSubstituter(vs, vparamss.head).traverse(guard);guard})}
          }

          val nguard = new ListBuffer[Tree]
          val ncases = new ListBuffer[CaseDef] 
          var cs = cases; while (cs ne Nil) {
            cs.head match {
              case CaseDef(p,EmptyTree,b) => 
                ncases += CaseDef(transform(p), EmptyTree, transform(b))
              case CaseDef(p, guard, b) => 
                val vs       = definedVars(p)
                val guardDef = makeGuardDef(vs, guard)
                nguard += transform(guardDef)
                val gdcall = localTyper.typed{Apply(Ident(guardDef.symbol),vs.map {Ident(_)})}
                ncases += CaseDef(transform(p), gdcall, transform(b))
            }
            cs = cs.tail
          }

          var checkExhaustive = true
          def isUncheckedAnnotation(tpe: Type) = tpe match {
            case AnnotatedType(List(AnnotationInfo(atp, _, _)), _, _) if atp.typeSymbol == UncheckedClass =>
              true
            case _ =>
              false
          }
          nselector match {
            case Typed(nselector1, tpt) if isUncheckedAnnotation(tpt.tpe) =>
              nselector = nselector1
              checkExhaustive = false
            case _ =>
          }

          ExplicitOuter.this.resultType = tree.tpe

          val t = atPos(tree.pos) { 
            val t_untyped = handlePattern(nselector, ncases.toList, checkExhaustive, currentOwner, transform)(localTyper)
            localTyper.typed(t_untyped, resultType) 
          }

          if (settings.debug.value)
            Console.println("finished translation of " + tid)

          if(nguard.isEmpty) {t} else Block(nguard.toList, t) setType t.tpe
        case _ =>
          val x = super.transform(tree)

          if (x.tpe eq null) x
          else x setType transformInfo(currentOwner, x.tpe)
      }
    }

    /** The transformation method for whole compilation units */
    override def transformUnit(unit: CompilationUnit) {
      cunit = unit
      atPhase(phase.next) { super.transformUnit(unit) }
    }
  }

  override def newPhase(prev: scala.tools.nsc.Phase): StdPhase =
    new Phase(prev)

  class Phase(prev: scala.tools.nsc.Phase) extends super.Phase(prev) {
    override val checkable = false
  }
}
