/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: Unapplies.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc.typechecker

import symtab.Flags._

/*
 *  @author  Martin Odersky
 *  @version 1.0
 */
trait Unapplies { self: Analyzer =>

  import global._
  import definitions._
  import posAssigner.atPos

  /** returns type list for return type of the extraction */
  def unapplyTypeList(ufn: Symbol, ufntpe: Type) = {
    assert(ufn.isMethod)
    //Console.println("utl "+ufntpe+" "+ufntpe.typeSymbol)
    ufn.name match {
      case nme.unapply    => unapplyTypeListFromReturnType(ufntpe)
      case nme.unapplySeq => unapplyTypeListFromReturnTypeSeq(ufntpe)
      case _ => throw new TypeError(ufn+" is not an unapply or unapplySeq")
    }
  }
  /** (the inverse of unapplyReturnTypeSeq)
   *  for type Boolean, returns Nil
   *  for type Option[T] or Some[T]:
   *   - returns T0...Tn if n>0 and T <: Product[T0...Tn]]
   *   - returns T otherwise
   */
  def unapplyTypeListFromReturnType(tp1: Type): List[Type] =  { // rename: unapplyTypeListFromReturnType
    val tp = unapplyUnwrap(tp1)
    val B = BooleanClass
    val O = OptionClass 
    val S = SomeClass 
    tp.typeSymbol match { // unapplySeqResultToMethodSig
      case  B => Nil
      case  O | S =>
        val prod = tp.typeArgs.head
        getProductArgs(prod)  match {
          case Some(all @ (x1::x2::xs)) => all       // n >= 2
          case _                        => prod::Nil // special n == 0 ||  n == 1 
        }
      case _ => throw new TypeError("result type "+tp+" of unapply not in {boolean, Option[_], Some[_]}")
    }
  }

  /** let type be the result type of the (possibly polymorphic) unapply method
   *  for type Option[T] or Some[T] 
   *  -returns T0...Tn-1,Tn* if n>0 and T <: Product[T0...Tn-1,Seq[Tn]]], 
   *  -returns R* if T = Seq[R]
   */
  def unapplyTypeListFromReturnTypeSeq(tp1: Type): List[Type] = {
    val tp = unapplyUnwrap(tp1)
    val   O = OptionClass; val S = SomeClass; tp.typeSymbol match { 
    case  O                  | S =>
      val ts = unapplyTypeListFromReturnType(tp1)
      val last1 = ts.last.baseType(SeqClass) match {
        case TypeRef(pre, seqClass, args) => typeRef(pre, RepeatedParamClass, args)
        case _ => throw new TypeError("last not seq")
      }
      ts.init ::: List(last1)
      case _ => throw new TypeError("result type "+tp+" of unapply not in {Option[_], Some[_]}")
    }
  }

  /** returns type of the unapply method returning T_0...T_n
   *  for n == 0, boolean
   *  for n == 1, Some[T0]
   *  else Some[Product[Ti]]
  def unapplyReturnType(elems: List[Type], useWildCards: Boolean) = 
    if (elems.isEmpty)
      BooleanClass.tpe
    else if (elems.length == 1)
      optionType(if(useWildCards) WildcardType else elems(0))
    else 
      productType({val es = elems; if(useWildCards) elems map { x => WildcardType} else elems})
   */
  def unapplyReturnTypeExpected(argsLength: Int) = argsLength match {
    case 0 => BooleanClass.tpe
    case 1 => optionType(WildcardType)
    case n => optionType(productType(List.range(0,n).map (arg => WildcardType)))
  }

  /** returns unapply or unapplySeq if available */
  def unapplyMember(tp: Type): Symbol = {
    var unapp = tp.member(nme.unapply)
    if (unapp == NoSymbol) unapp = tp.member(nme.unapplySeq)
    unapp
  }

  private def copyUntyped[T <: Tree](tree: T): T = {
    val tree1 = tree.duplicate
    UnTyper.traverse(tree1)
    tree1
  }

  private def classType(cdef: ClassDef, tparams: List[TypeDef]): Tree = {
    val tycon = gen.mkAttributedRef(cdef.symbol)
    if (tparams.isEmpty) tycon else AppliedTypeTree(tycon, tparams map (x => Ident(x.name)))
  }

  private def constrParamss(cdef: ClassDef): List[List[ValDef]] = {
    val constr = treeInfo.firstConstructor(cdef.impl.body)
    (constr: @unchecked) match {
      case DefDef(_, _, _, vparamss, _, _) => vparamss map (_ map copyUntyped[ValDef])
    }
  }

  /** The return value of an unapply method of a case class C[Ts]
   *  @param param  The name of the parameter of the unapply method, assumed to be of type C[Ts]
   *  @param caseclazz  The case class C[Ts]
   */
  private def caseClassUnapplyReturnValue(param: Name, caseclazz: Symbol) = {
    def caseFieldAccessorValue(selector: Symbol) = Select(Ident(param), selector)
    val accessors = caseclazz.caseFieldAccessors
    if (accessors.isEmpty) Literal(true)
    else 
      Apply(
        gen.scalaDot(nme.Some), 
        List(
          if (accessors.tail.isEmpty) caseFieldAccessorValue(accessors.head)
          else Apply(
            gen.scalaDot(newTermName("Tuple" + accessors.length)), 
            accessors map caseFieldAccessorValue)))
  }

  /** The module corresponding to a case class; without any member definitions
   */
  def caseModuleDef(cdef: ClassDef): ModuleDef = atPos(cdef.pos) {
    var parents = List(gen.scalaScalaObjectConstr)
    if (!(cdef.mods hasFlag ABSTRACT) && cdef.tparams.isEmpty && constrParamss(cdef).length == 1)
      parents = gen.scalaFunctionConstr(constrParamss(cdef).head map (_.tpt), 
                                        Ident(cdef.name)) :: parents
    ModuleDef(
      Modifiers(cdef.mods.flags & AccessFlags | SYNTHETIC, cdef.mods.privateWithin),
      cdef.name.toTermName,
      Template(parents, emptyValDef, Modifiers(0), List(), List(List()), List()))
  }

  /** The apply method corresponding to a case class
   */
  def caseModuleApplyMeth(cdef: ClassDef): DefDef = {
    val tparams = cdef.tparams map copyUntyped[TypeDef]
    val cparamss = constrParamss(cdef)
    atPos(cdef.pos) {
      DefDef(
        Modifiers(SYNTHETIC | CASE), 
        nme.apply, 
        tparams, 
        cparamss,
        classType(cdef, tparams),
        New(classType(cdef, tparams), cparamss map (_ map gen.paramToArg)))
    }
  }

  /** The unapply method corresponding to a case class
   */
  def caseModuleUnapplyMeth(cdef: ClassDef): DefDef = {
    val tparams = cdef.tparams map copyUntyped[TypeDef]
    val unapplyParamName = newTermName("x$0")
    val hasVarArg = constrParamss(cdef) match {
      case (cps @ (_ :: _)) :: _ => treeInfo.isRepeatedParamType(cps.last.tpt)
      case _ => false
    }
    atPos(cdef.pos) {
      DefDef(
        Modifiers(SYNTHETIC | CASE),
        if (hasVarArg) nme.unapplySeq else nme.unapply,
        tparams,
        List(List(ValDef(Modifiers(PARAM | SYNTHETIC), unapplyParamName, 
                         classType(cdef, tparams), EmptyTree))),
        TypeTree(),
        caseClassUnapplyReturnValue(unapplyParamName, cdef.symbol))
    }
  }
}
