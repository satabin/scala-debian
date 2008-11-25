/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author Martin Odersky
 */
// $Id: SyntheticMethods.scala 15390 2008-06-17 17:44:30Z odersky $

package scala.tools.nsc.typechecker

import symtab.Flags._
import scala.collection.mutable.ListBuffer

/** <ul>
 *    <li>
 *      <code>productArity</code>, <code>element</code> implementations added
 *      to case classes
 *    </li>
 *    <li>
 *      <code>equals</code>, <code>hashCode</code> and </code>toString</code>
 *      methods are added to case classes, unless they are defined in the
 *      class or a baseclass different from <code>java.lang.Object</code>
 *    </li>
 *    <li>
 *      <code>toString</code> method is added to case objects, unless they
 *      are defined in the class or a baseclass different from
 *      <code>java.lang.Object</code>
 *    </li>
 *  </ul>
 */
trait SyntheticMethods { self: Analyzer =>
  import global._                  // the global environment
  import definitions._             // standard classes and methods
  //import global.typer.{typed}      // methods to type trees
  // @S: type hack: by default, we are used from global.analyzer context
  // so this cast won't fail. If we aren't in global.analyzer, we have
  // to override this method anyways.
  protected def typer : Typer = global.typer.asInstanceOf[Typer]

  /**
   *  @param templ ...
   *  @param clazz ...
   *  @param unit  ...
   *  @return      ...
   */
  def addSyntheticMethods(templ: Template, clazz: Symbol, context: Context): Template = try {

    val localContext = if (reporter.hasErrors) context.makeSilent(false) else context
    val localTyper = newTyper(localContext)

    def hasImplementation(name: Name): Boolean = if (inIDE) true else {
      val sym = clazz.info.nonPrivateMember(name) 
      sym.isTerm && !(sym hasFlag DEFERRED)
    }

    def hasOverridingImplementation(meth: Symbol): Boolean = if (inIDE) true else {
      val sym = clazz.info.nonPrivateMember(meth.name)
      sym.alternatives exists { sym =>
        sym != meth && !(sym hasFlag DEFERRED) && !(sym hasFlag (SYNTHETIC | SYNTHETICMETH)) && 
        (clazz.thisType.memberType(sym) matches clazz.thisType.memberType(meth))
      }
    }

    def syntheticMethod(name: Name, flags: Int, tpe: Type) =
      newSyntheticMethod(name, flags | OVERRIDE, tpe)

    def newSyntheticMethod(name: Name, flags: Int, tpe: Type) = {
      var method = clazz.newMethod(clazz.pos, name) 
        .setFlag(flags | (if (inIDE) SYNTHETIC else SYNTHETICMETH))
        .setInfo(tpe)
      method = clazz.info.decls.enter(method).asInstanceOf[TermSymbol]
      method
    }

    /*
    def productSelectorMethod(n: int, accessor: Symbol): Tree = {
      val method = syntheticMethod(newTermName("_"+n), FINAL, accessor.tpe) 
      typed(DefDef(method, vparamss => gen.mkAttributedRef(accessor)))
    }
    */
    def productPrefixMethod: Tree = {
      val method = syntheticMethod(nme.productPrefix, 0, PolyType(List(), StringClass.tpe))
      typer.typed(DefDef(method, vparamss => Literal(Constant(clazz.name.decode))))
    }

    def productArityMethod(nargs:Int ): Tree = {
      val method = syntheticMethod(nme.productArity, 0, PolyType(List(), IntClass.tpe))
      typer.typed(DefDef(method, vparamss => Literal(Constant(nargs))))
    }

    def productElementMethod(accs: List[Symbol]): Tree = {
      //val retTpe = lub(accs map (_.tpe.resultType))
      val method = syntheticMethod(nme.productElement, 0, MethodType(List(IntClass.tpe), AnyClass.tpe/*retTpe*/))
      typer.typed(DefDef(method, vparamss => Match(Ident(vparamss.head.head), {
	(for ((sym,i) <- accs.zipWithIndex) yield {
	  CaseDef(Literal(Constant(i)),EmptyTree, Ident(sym))
	}):::List(CaseDef(Ident(nme.WILDCARD), EmptyTree, 
		    Throw(New(TypeTree(IndexOutOfBoundsExceptionClass.tpe), List(List(
		      Select(Ident(vparamss.head.head), nme.toString_)
		    ))))))
      })))
    }

    def moduleToStringMethod: Tree = {
      val method = syntheticMethod(nme.toString_, FINAL, MethodType(List(), StringClass.tpe))
      typer.typed(DefDef(method, vparamss => Literal(Constant(clazz.name.decode))))
    }

    def tagMethod: Tree = {
      val method = syntheticMethod(nme.tag, 0, MethodType(List(), IntClass.tpe))
      typer.typed(DefDef(method, vparamss => Literal(Constant(clazz.tag))))
    }

    def forwardingMethod(name: Name): Tree = {
      val target = getMember(ScalaRunTimeModule, "_" + name)
      val paramtypes =
        if (target.tpe.paramTypes.isEmpty) List()
        else target.tpe.paramTypes.tail
      val method = syntheticMethod(
        name, 0, MethodType(paramtypes, target.tpe.resultType))
      typer.typed(DefDef(method, vparamss =>
        Apply(gen.mkAttributedRef(target), This(clazz) :: (vparamss.head map Ident))))
    }

    def equalsSym = 
      syntheticMethod(nme.equals_, 0, MethodType(List(AnyClass.tpe), BooleanClass.tpe))

    /** The equality method for case modules:
     *   def equals(that: Any) = this eq that
     */
    def equalsModuleMethod: Tree = {
      val method = equalsSym
      val methodDef = 
        DefDef(method, vparamss =>
          Apply(
            Select(This(clazz), Object_eq), 
            List(
              TypeApply(
                Select(
                  Ident(vparamss.head.head),
                  Any_asInstanceOf),
                List(TypeTree(AnyRefClass.tpe))))))
      localTyper.typed(methodDef)
    }

    /** The equality method for case classes:
     *   def equals(that: Any) = 
     *     that.isInstanceOf[AnyRef] &&
     *     ((this eq that.asInstanceOf[AnyRef]) || 
     *     (that match {
     *       case this.C(this.arg_1, ..., this.arg_n) => true
     *       case _ => false
     *     }))
     */
    def equalsClassMethod: Tree = {
      val method = equalsSym
      val methodDef = 
        DefDef(
          method, 
          { vparamss =>
            val that = Ident(vparamss.head.head)
            val constrParamTypes = clazz.primaryConstructor.tpe.paramTypes
            val hasVarArgs = !constrParamTypes.isEmpty && constrParamTypes.last.typeSymbol == RepeatedParamClass
            if (false && clazz.isStatic) {
              // todo: elim
              val target = getMember(ScalaRunTimeModule, if (hasVarArgs) nme._equalsWithVarArgs else nme._equals)
              Apply(
                Select(
                  TypeApply(
                    Select(that, Any_isInstanceOf),
                    List(TypeTree(clazz.tpe))),
                  Boolean_and),
                List(
                  Apply(gen.mkAttributedRef(target),
                        This(clazz) :: (vparamss.head map Ident))))
            } else {
              val (pat, guard) = {
                val guards = new ListBuffer[Tree]
                val params = for ((acc, cpt) <- clazz.caseFieldAccessors zip constrParamTypes) yield { 
                  val name = context.unit.fresh.newName(clazz.pos, acc.name+"$")
                  val isVarArg = cpt.typeSymbol == RepeatedParamClass 
                  guards += Apply(
                    Select(
                      Ident(name),
                      if (isVarArg) nme.sameElements else nme.EQ),
                    List(Ident(acc)))
                  Bind(name, 
                       if (isVarArg) Star(Ident(nme.WILDCARD))
                       else Ident(nme.WILDCARD))
                }
                ( Apply(Ident(clazz.name.toTermName), params),
                  if (guards.isEmpty) EmptyTree
                  else guards reduceLeft { (g1: Tree, g2: Tree) =>
                    Apply(Select(g1, nme.AMPAMP), List(g2))
                  }
                )
              }
              val isAnyRef = TypeApply(
                    Select(that, Any_isInstanceOf),
                    List(TypeTree(AnyRefClass.tpe)))
              val cast = TypeApply(
                    Select(that, Any_asInstanceOf),
                    List(TypeTree(AnyRefClass.tpe)))
              val eq_ = Apply(Select( This(clazz) , nme.eq), List(that setType AnyRefClass.tpe)) 
              val match_ = Match(that, List(
                    CaseDef(pat, guard, Literal(Constant(true))),
                    CaseDef(Ident(nme.WILDCARD), EmptyTree, Literal(Constant(false)))))
              Apply(
                    Select(isAnyRef, Boolean_and),
                    List(Apply(Select(eq_, Boolean_or),
                    List(match_))))
            }
          }
        )
      localTyper.typed(methodDef)
    }

    def isSerializable(clazz: Symbol): Boolean =
      !clazz.getAttributes(definitions.SerializableAttr).isEmpty

    def readResolveMethod: Tree = {
      // !!! the synthetic method "readResolve" should be private,
      // but then it is renamed !!!
      val method = newSyntheticMethod(nme.readResolve, PROTECTED,
                                      MethodType(List(), ObjectClass.tpe))
      typer.typed(DefDef(method, vparamss => gen.mkAttributedRef(clazz.sourceModule)))
    }

    def newAccessorMethod(tree: Tree): Tree = tree match {
      case DefDef(_, _, _, _, _, rhs) =>
        var newAcc = tree.symbol.cloneSymbol
        newAcc.name = context.unit.fresh.newName(tree.symbol.pos, tree.symbol.name + "$")
        newAcc.setFlag(SYNTHETIC).resetFlag(ACCESSOR | PARAMACCESSOR | PRIVATE)
        newAcc = newAcc.owner.info.decls enter newAcc
        val result = typer.typed(DefDef(newAcc, vparamss => rhs.duplicate))
        log("new accessor method " + result)
        result
    }

    def beanSetterOrGetter(sym: Symbol): Symbol =
      if (!sym.name(0).isLetter) {
        context.unit.error(sym.pos, "attribute `BeanProperty' can be applied only to fields that start with a letter")
        NoSymbol
      } else {
        var name0 = sym.name
        if (sym.isSetter) name0 = nme.setterToGetter(name0)
        val prefix = if (sym.isSetter) "set" else
          if (sym.tpe.resultType == BooleanClass.tpe) "is" else "get"
        val arity = if (sym.isSetter) 1 else 0
        val name1 = prefix + name0(0).toUpperCase + name0.subName(1, name0.length)
        val sym1 = clazz.info.decl(name1)
        if (sym1 != NoSymbol && sym1.tpe.paramTypes.length == arity) {
          context.unit.error(sym.pos, "a definition of `"+name1+"' already exists in " + clazz)
          NoSymbol
        } else {
          clazz.newMethod(sym.pos, name1)
            .setInfo(sym.info)
            .setFlag(sym.getFlag(DEFERRED | OVERRIDE | STATIC))
        }
      }

    val ts = new ListBuffer[Tree]

    def addBeanGetterMethod(sym: Symbol) = {
      val getter = beanSetterOrGetter(sym)
      if (getter != NoSymbol)
        ts += typer.typed(DefDef(
          getter,
          vparamss => if (sym hasFlag DEFERRED) EmptyTree else gen.mkAttributedRef(sym)))
    }

    def addBeanSetterMethod(sym: Symbol) = {
      val setter = beanSetterOrGetter(sym)
      if (setter != NoSymbol)
        ts += typer.typed(DefDef(
          setter,
          vparamss =>
            if (sym hasFlag DEFERRED) EmptyTree 
            else Apply(gen.mkAttributedRef(sym), List(Ident(vparamss.head.head)))))
    }

    def isPublic(sym: Symbol) = 
      !sym.hasFlag(PRIVATE | PROTECTED) && sym.privateWithin == NoSymbol

    if (!phase.erasedTypes) {
      try {
        if (clazz hasFlag CASE) {
          val isTop = !(clazz.info.baseClasses.tail exists (_ hasFlag CASE))
          // case classes are implicitly declared serializable
          clazz.attributes = AnnotationInfo(SerializableAttr.tpe, List(), List()) :: clazz.attributes

          if (isTop) {
            for (stat <- templ.body) {
              if (stat.isDef && stat.symbol.isMethod && stat.symbol.hasFlag(CASEACCESSOR) && !isPublic(stat.symbol)) {
                ts += newAccessorMethod(stat)
                stat.symbol.resetFlag(CASEACCESSOR)
              }
            }
            if (!inIDE && !clazz.hasFlag(INTERFACE) && clazz.info.nonPrivateDecl(nme.tag) == NoSymbol) ts += tagMethod
          }
          if (clazz.isModuleClass) {
            if (!hasOverridingImplementation(Object_toString)) ts += moduleToStringMethod
            // if there's a synthetic method in a parent case class, override its equality
            // with eq (see #883)
            val otherEquals = clazz.info.nonPrivateMember(Object_equals.name) 
            if (otherEquals.owner != clazz && (otherEquals hasFlag SYNTHETICMETH)) ts += equalsModuleMethod
          } else {
            if (!hasOverridingImplementation(Object_hashCode)) ts += forwardingMethod(nme.hashCode_)
            if (!hasOverridingImplementation(Object_toString)) ts += forwardingMethod(nme.toString_)
            if (!hasOverridingImplementation(Object_equals)) ts += equalsClassMethod
          }

          if (!hasOverridingImplementation(Product_productPrefix)) ts += productPrefixMethod
          val accessors = clazz.caseFieldAccessors
          if (!hasOverridingImplementation(Product_productArity))
            ts += productArityMethod(accessors.length)
          if (!hasOverridingImplementation(Product_productElement))
            ts += productElementMethod(accessors)
        }

        if (clazz.isModuleClass && isSerializable(clazz)) {
          // If you serialize a singleton and then deserialize it twice,
          // you will have two instances of your singleton, unless you implement
          // the readResolve() method (see http://www.javaworld.com/javaworld/
          // jw-04-2003/jw-0425-designpatterns_p.html)
          if (!hasImplementation(nme.readResolve)) ts += readResolveMethod
        }
        if (!forCLDC && !forMSIL)
          for (sym <- clazz.info.decls.toList)
            if (!sym.getAttributes(BeanPropertyAttr).isEmpty)
              if (sym.isGetter)
                addBeanGetterMethod(sym)
              else if (sym.isSetter)
                addBeanSetterMethod(sym)
              else if (sym.isMethod || sym.isType)
                context.unit.error(sym.pos, "attribute `BeanProperty' is not applicable to " + sym)
      } catch {
        case ex: TypeError =>
          if (!reporter.hasErrors) throw ex
      }
    }
    val synthetics = ts.toList
    copy.Template(
      templ, templ.parents, templ.self, if (synthetics.isEmpty) templ.body else templ.body ::: synthetics)
  }
}
