/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: Pickler.scala 15841 2008-08-19 14:29:45Z rytz $

package scala.tools.nsc.symtab.classfile

import java.lang.{Float, Double}
import scala.tools.nsc.util.{Position, NoPosition, ShowPickled}
import Flags._
import PickleFormat._

/**
 * Serialize a top-level module and/or class.
 *
 * @see <code>EntryTags.scala</code> for symbol table attribute format.
 *
 * @author Martin Odersky
 * @version 1.0
 */
abstract class Pickler extends SubComponent {
  import global._

  private final val showSig = false

  val phaseName = "pickler"

  def newPhase(prev: Phase): StdPhase = new PicklePhase(prev)

  class PicklePhase(prev: Phase) extends StdPhase(prev) {
    def apply(unit: CompilationUnit) {
      def pickle(tree: Tree) {

        def add(sym: Symbol, pickle: Pickle) = {
          if (currentRun.compiles(sym) && !currentRun.symData.contains(sym)) {
            if (settings.debug.value) log("pickling " + sym)
            pickle.putSymbol(sym)
            currentRun.symData(sym) = pickle
          }
        }

        tree match {
          case PackageDef(_, stats) =>
            stats foreach pickle
          case ClassDef(_, _, _, _) | ModuleDef(_, _, _) =>
            val sym = tree.symbol
            val pickle = new Pickle(sym, sym.name.toTermName, sym.owner)
            add(sym, pickle)
            add(sym.linkedSym, pickle)
            pickle.finish
            val doPickleHash = global.doPickleHash
            if (doPickleHash) {
              var i = 0
              while (i < pickle.writeIndex) {
                unit.pickleHash += pickle.bytes(i).toLong // toLong needed to work around bug
                i += 1
              }
            }
          case _ =>
        }
      }
      pickle(unit.body)
    }
  }

  private class Pickle(root: Symbol, rootName: Name, rootOwner: Symbol)
        extends PickleBuffer(new Array[Byte](4096), -1, 0) {
    import scala.collection.jcl.LinkedHashMap
    private var entries = new Array[AnyRef](256)
    private var ep = 0
    private val index = new LinkedHashMap[AnyRef, Int]

    /** Is symbol an existentially bound variable with a package as owner? 
     *  Such symbols should be treated as if they were local.
     */
    private def isUnrootedExistential(sym: Symbol) = 
      sym.isAbstractType && sym.hasFlag(EXISTENTIAL) && sym.owner.isPackageClass 

    private def normalizedOwner(sym: Symbol) = 
      if (isUnrootedExistential(sym)) root else sym.owner

    /** Is root in symbol.owner*?
     *
     *  @param sym ...
     *  @return    ...
     */
    private def isLocal(sym: Symbol): Boolean =
      sym.isRefinementClass ||
      sym.name.toTermName == rootName && sym.owner == rootOwner ||
      sym != NoSymbol && isLocal(sym.owner) ||
      isUnrootedExistential(sym)

    // Phase 1 methods: Populate entries/index ------------------------------------

    /** Store entry <code>e</code> in index at next available position unless
     *  it is already there.
     *
     *  @param entry ...
     *  @return      <code>true</code> iff entry is new.
     */
    private def putEntry(entry: AnyRef): Boolean = index.get(entry) match {
      case Some(_) => false
      case None =>
        if (ep == entries.length) {
          val entries1 = new Array[AnyRef](ep * 2)
          Array.copy(entries, 0, entries1, 0, ep)
          entries = entries1
        }
        entries(ep) = entry
        index(entry) = ep
        ep = ep + 1
        true
    }

    /** Store symbol in <code>index</code>. If symbol is local, also store
     * everything it refers to.
     *
     *  @param sym ...
     */
    def putSymbol(sym: Symbol) {
      if (putEntry(sym)) {
        if (isLocal(sym)) {
          putEntry(sym.name)
          putSymbol(sym.owner)
          putSymbol(sym.privateWithin)
          putType(sym.info)
          if (sym.thisSym.tpeHK != sym.tpeHK)
            putType(sym.typeOfThis);
          putSymbol(sym.alias)
          if (!sym.children.isEmpty) {
            val (locals, globals) = sym.children.toList.partition(_.isLocalClass)
            val children = 
              if (locals.isEmpty) globals
              else {
                val localChildDummy = sym.newClass(sym.pos, nme.LOCALCHILD)
                localChildDummy.setInfo(ClassInfoType(List(sym.tpe), EmptyScope, localChildDummy))
                localChildDummy :: globals
              }
            putChildren(sym, children.sort((x, y) => x isLess y))
          }
          for (attr <- sym.attributes.reverse) {
            if (attr.atp.typeSymbol isNonBottomSubClass definitions.StaticAnnotationClass)
              putAnnotation(sym, attr)
          }
        } else if (sym != NoSymbol) {
          putEntry(if (sym.isModuleClass) sym.name.toTermName else sym.name)
          if (!sym.owner.isRoot) putSymbol(sym.owner)
        }
      }
    }

    private def putSymbols(syms: List[Symbol]) =
      syms foreach putSymbol

    /** Store type and everythig it refers to in map <code>index</code>.
     *
     *  @param tp ...
     */
    private def putType(tp: Type): Unit = if (putEntry(tp)) {
      tp match {
        case NoType | NoPrefix | DeBruijnIndex(_, _) =>
          ;
        case ThisType(sym) =>
          putSymbol(sym)
        case SingleType(pre, sym) =>
          putType(pre); putSymbol(sym)
        case ConstantType(value) =>
	  putConstant(value)
        case TypeRef(pre, sym, args) =>
          putType(pre); putSymbol(sym); putTypes(args)
        case TypeBounds(lo, hi) =>
          putType(lo); putType(hi)
        case RefinedType(parents, decls) =>
          val rclazz = tp.typeSymbol
          for (m <- decls.elements)
            if (m.owner != rclazz) assert(false, "bad refinement member "+m+" of "+tp+", owner = "+m.owner)
          putSymbol(rclazz); putTypes(parents); putSymbols(decls.toList)
        case ClassInfoType(parents, decls, clazz) =>
          putSymbol(clazz); putTypes(parents); putSymbols(decls.toList)
        case MethodType(formals, restpe) =>
          putType(restpe); putTypes(formals)
        case PolyType(tparams, restpe) =>
          putType(restpe); putSymbols(tparams)
        case ExistentialType(tparams, restpe) =>
          putType(restpe); putSymbols(tparams)
        case AnnotatedType(attribs, tp, selfsym) =>
          putType(tp); putAnnotations(attribs)
	  if (settings.selfInAnnots.value) putSymbol(selfsym)
        case _ =>
          throw new FatalError("bad type: " + tp + "(" + tp.getClass + ")")
      }
    }
    private def putTypes(tps: List[Type]) { tps foreach putType }

    private def putTree(tree: Tree): Unit = if (putEntry(tree)) {
      if (tree != EmptyTree)
	putType(tree.tpe)
      if (tree.hasSymbol)
	putSymbol(tree.symbol)

      tree match {
	case EmptyTree =>

	case tree@PackageDef(name, stats) =>
	  putEntry(name)
          putTrees(stats)

	case ClassDef(mods, name, tparams, impl) =>
	  putMods(mods)
	  putEntry(name)
          putTree(impl)
	  putTrees(tparams)
	  
	case ModuleDef(mods, name, impl) =>
	  putMods(mods)
	  putEntry(name)
	  putTree(impl)
  
	case ValDef(mods, name, tpt, rhs) =>
	  putMods(mods)
	  putEntry(name)
	  putTree(tpt)
	  putTree(rhs)
	
	case DefDef(mods, name, tparams, vparamss, tpt, rhs) =>
	  putMods(mods)
	  putEntry(name)
	  putTrees(tparams)
	  putTreess(vparamss)
	  putTree(tpt)
	  putTree(rhs)
  

	case TypeDef(mods, name, tparams, rhs) =>
	  putMods(mods)
	  putEntry(name)
	  putTree(rhs)
	  putTrees(tparams)


	case LabelDef(name, params, rhs) =>
	  putTree(rhs)
	  putTrees(params)


	case Import(expr, selectors) =>
	  putTree(expr)
	  for ((from,to) <- selectors) {
	    putEntry(from)
	    putEntry(to)
	  }

	case Annotation(constr, elements) =>                            
	  putTree(constr)
	  putTrees(elements)

	case DocDef(comment, definition) =>
	  putConstant(Constant(comment))
	  putTree(definition)

	case Template(parents, self, body) =>
          writeNat(parents.length)
	  putTrees(parents)
	  putTree(self)
	  putTrees(body)

	case Block(stats, expr) =>
	  putTree(expr)
	  putTrees(stats)

	case CaseDef(pat, guard, body) =>
	  putTree(pat)
	  putTree(guard)
	  putTree(body)

	case Sequence(trees) =>
	  putTrees(trees)

	case Alternative(trees) =>
	  putTrees(trees)

	case Star(elem) =>
	  putTree(elem)

	case Bind(name, body) =>
	  putEntry(name)
	  putTree(body)

	case UnApply(fun: Tree, args) =>
	  putTree(fun)
	  putTrees(args)

	case ArrayValue(elemtpt, trees) =>
	  putTree(elemtpt)
	  putTrees(trees)


	case Function(vparams, body) =>
	  putTree(body)
	  putTrees(vparams)

	case Assign(lhs, rhs) =>
	  putTree(lhs)
	  putTree(rhs)

	case If(cond, thenp, elsep) =>
	  putTree(cond)
	  putTree(thenp)
	  putTree(elsep)

	case Match(selector, cases) =>
	  putTree(selector)
	  putTrees(cases)

	case Return(expr) =>
	  putTree(expr)

	case Try(block, catches, finalizer) =>
	  putTree(block)
	  putTree(finalizer)
	  putTrees(catches)

	case Throw(expr) =>
	  putTree(expr)

	case New(tpt) =>
	  putTree(tpt)
  
	case Typed(expr, tpt) =>
	  putTree(expr)
	  putTree(tpt)

	case TypeApply(fun, args) =>
	  putTree(fun)
	  putTrees(args)
	  
	case Apply(fun, args) =>
	  putTree(fun)
	  putTrees(args)

	case ApplyDynamic(qual, args) =>
	  writeEntry(qual)
	  putTrees(args)

	case Super(qual, mix) =>
	  putEntry(qual:Name)
	  putEntry(mix:Name)

        case This(qual) =>
	  putEntry(qual)

        case Select(qualifier, selector) =>
	  putTree(qualifier)
	  putEntry(selector)

	case Ident(name) =>
	  putEntry(name)

	case Literal(value) =>
	  putEntry(value)

	case TypeTree() =>

	case Annotated(annot, arg) =>
	  putTree(annot)
	  putTree(arg)

	case SingletonTypeTree(ref) =>
	  putTree(ref)

	case SelectFromTypeTree(qualifier, selector) =>
	  putTree(qualifier)
	  putEntry(selector)

	case CompoundTypeTree(templ: Template) =>
	  putTree(templ)

	case AppliedTypeTree(tpt, args) =>
	  putTree(tpt)
	  putTrees(args)

	case TypeBoundsTree(lo, hi) =>
	  putTree(lo)
	  putTree(hi)

	case ExistentialTypeTree(tpt, whereClauses) =>
	  putTree(tpt)
	  putTrees(whereClauses)
      }
    }

    private def putTrees(trees: List[Tree]) =
      trees.foreach(putTree _)

    private def putTreess(treess: List[List[Tree]]) =
      treess.foreach(putTrees _)

    private def putMods(mods: Modifiers) = if (putEntry(mods)) {
      val Modifiers(flags, privateWithin, annotations) = mods
      putEntry(privateWithin)
      putTrees(annotations)
    }

    /** Store a constant in map <code>index</code> along with
     *  anything it references.
     */
    private def putConstant(c: Constant) =
      if (putEntry(c)) {
        if (c.tag == StringTag) putEntry(newTermName(c.stringValue))
        else if (c.tag == ClassTag) putType(c.typeValue)
      }

    private def putChildren(sym: Symbol, children: List[Symbol]) {
      assert(putEntry((sym, children)))
      children foreach putSymbol
    }

    private def putAnnotation(sym: Symbol, annot: AnnotationInfo) {
      // if an annotation with the same arguments is applied to the
      // same symbol multiple times, it's only pickled once.
      if (putEntry((sym, annot))) {
        val AnnotationInfo(atp, args, assocs) = annot
        putType(atp)
        args foreach putAnnotationArg
        for ((name, c) <- assocs) { putEntry(name); putAnnotationArg(c) }
      }
    }

    private def putAnnotation(annot: AnnotationInfo) {
      if (putEntry(annot)) {
        val AnnotationInfo(tpe, args, assocs) = annot
        putType(tpe)
        args foreach putAnnotationArg
        for ((name, rhs) <- assocs) { putEntry(name); putAnnotationArg(rhs) }
      }
    }
    
    private def putAnnotationArg(arg: AnnotationArgument) {
      if (putEntry(arg)) {
        arg.constant match {
	  case Some(c) => putConstant(c)
	  case _ => putTree(arg.intTree)
	}
      }
    }

    private def putAnnotations(annots: List[AnnotationInfo]) {
      annots foreach putAnnotation
    }

    // Phase 2 methods: Write all entries to byte array ------------------------------

    private val buf = new PickleBuffer(new Array[Byte](4096), -1, 0)

    /** Write a reference to object, i.e., the object's number in the map
     *  <code>index</code>.
     *
     *  @param ref ...
     */
    private def writeRef(ref: AnyRef) { writeNat(index(ref)) }
    private def writeRefs(refs: List[AnyRef]) { refs foreach writeRef }

    /** Write name, owner, flags, and info of a symbol.
     *
     *  @param sym ...
     *  @return    the position offset
     */
    private def writeSymInfo(sym: Symbol): Int = {
      var posOffset = 0
      writeRef(sym.name)
      writeRef(normalizedOwner(sym))
      writeNat((sym.flags & PickledFlags).asInstanceOf[Int])
      if (sym.privateWithin != NoSymbol) writeRef(sym.privateWithin)
      writeRef(sym.info)
      posOffset
    }

    /** Write a name in UTF8 format. */
    def writeName(name: Name) {
      ensureCapacity(name.length * 3)
      writeIndex = name.copyUTF8(bytes, writeIndex)
    }

    /** Write an entry */
    private def writeEntry(entry: AnyRef) {
      def writeBody(entry: AnyRef): Int = entry match {
        case name: Name =>
          writeName(name)
          if (name.isTermName) TERMname else TYPEname
        case NoSymbol =>
          NONEsym
        case sym: Symbol if !isLocal(sym) =>
          val tag =
            if (sym.isModuleClass) {
              writeRef(sym.name.toTermName); EXTMODCLASSref
            } else {
              writeRef(sym.name); EXTref
            }
          if (!sym.owner.isRoot) writeRef(sym.owner)
          tag
        case sym: ClassSymbol =>
          val posOffset = writeSymInfo(sym)
          if (sym.thisSym.tpe != sym.tpe) writeRef(sym.typeOfThis)
          CLASSsym + posOffset
        case sym: TypeSymbol =>
          val posOffset = writeSymInfo(sym)
          (if (sym.isAbstractType) TYPEsym else ALIASsym) + posOffset
        case sym: TermSymbol =>
          val posOffset = writeSymInfo(sym)
          if (sym.alias != NoSymbol) writeRef(sym.alias)
          (if (sym.isModule) MODULEsym else VALsym) + posOffset
        case NoType =>
          NOtpe
        case NoPrefix =>
          NOPREFIXtpe
        case ThisType(sym) =>
          writeRef(sym); THIStpe
        case SingleType(pre, sym) =>
          writeRef(pre); writeRef(sym); SINGLEtpe
        case ConstantType(value) =>
          writeRef(value); CONSTANTtpe
        case TypeRef(pre, sym, args) =>
          writeRef(pre); writeRef(sym); writeRefs(args); TYPEREFtpe
        case TypeBounds(lo, hi) =>
          writeRef(lo); writeRef(hi); TYPEBOUNDStpe
        case tp @ RefinedType(parents, decls) =>
          writeRef(tp.typeSymbol); writeRefs(parents); REFINEDtpe
        case ClassInfoType(parents, decls, clazz) =>
          writeRef(clazz); writeRefs(parents); CLASSINFOtpe
        case MethodType(formals, restpe) =>
          writeRef(restpe); writeRefs(formals)
          if (entry.isInstanceOf[ImplicitMethodType]) IMPLICITMETHODtpe
          else METHODtpe
        case PolyType(tparams, restpe) =>
          writeRef(restpe); writeRefs(tparams); POLYtpe
        case ExistentialType(tparams, restpe) =>
          writeRef(restpe); writeRefs(tparams); EXISTENTIALtpe
        case DeBruijnIndex(l, i) =>
          writeNat(l); writeNat(i); DEBRUIJNINDEXtpe
        case c @ Constant(_) =>
          if (c.tag == BooleanTag) writeLong(if (c.booleanValue) 1 else 0)
          else if (ByteTag <= c.tag && c.tag <= LongTag) writeLong(c.longValue)
          else if (c.tag == FloatTag) writeLong(Float.floatToIntBits(c.floatValue))
          else if (c.tag == DoubleTag) writeLong(Double.doubleToLongBits(c.doubleValue))
          else if (c.tag == StringTag) writeRef(newTermName(c.stringValue))
          else if (c.tag == ClassTag) writeRef(c.typeValue)
          LITERAL + c.tag
        case AnnotatedType(attribs, tp, selfsym) => 
	  if (settings.selfInAnnots.value) {
	    writeRef(tp)
	    writeRef(selfsym)
	    writeRefs(attribs)
	    ANNOTATEDWSELFtpe
	  } else {
            writeRef(tp)
            writeRefs(attribs)
            ANNOTATEDtpe
	  }
        case (target: Symbol, attr @ AnnotationInfo(atp, args, assocs)) =>
          writeRef(target)
          writeRef(atp)
          for (c <- args) writeRef(c)
          for ((name, c) <- assocs) { writeRef(name); writeRef(c) }
          ATTRIBUTE
        case (target: Symbol, children: List[_]) =>
          writeRef(target)
          for (c <- children) writeRef(c.asInstanceOf[Symbol])
          CHILDREN

	case EmptyTree =>
	  writeNat(EMPTYtree)
	  TREE

	case tree@PackageDef(name, stats) =>
	  writeNat(PACKAGEtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(tree.mods)
	  writeRef(name)
          writeRefs(stats)
	  TREE

	case tree@ClassDef(mods, name, tparams, impl) =>
	  writeNat(CLASStree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(mods)
	  writeRef(name)
          writeRef(impl)
	  writeRefs(tparams)
	  TREE
	  
	case tree@ModuleDef(mods, name, impl) =>
	  writeNat(MODULEtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(mods)
	  writeRef(name)
	  writeRef(impl)
	  TREE
  
	case tree@ValDef(mods, name, tpt, rhs) =>
          writeNat(VALDEFtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(mods)
	  writeRef(name)
	  writeRef(tpt)
	  writeRef(rhs)
	  TREE

	
	case tree@DefDef(mods, name, tparams, vparamss, tpt, rhs) =>
          writeNat(DEFDEFtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(mods)
	  writeRef(name)
	  writeNat(tparams.length)
	  writeRefs(tparams)
	  writeNat(vparamss.length)
	  for(vparams <- vparamss) {
	    writeNat(vparams.length)
	    writeRefs(vparams)
	  }
	  writeRef(tpt)
	  writeRef(rhs)
	  TREE
  

	case tree@TypeDef(mods, name, tparams, rhs) =>
	  writeNat(TYPEDEFtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(mods)
	  writeRef(name)
	  writeRef(rhs)
	  writeRefs(tparams)
	  TREE


	case tree@LabelDef(name, params, rhs) =>
	  writeNat(LABELtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(rhs)
	  writeRefs(params)
	  TREE


	case tree@Import(expr, selectors) =>
	  writeNat(IMPORTtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(expr)
	  for ((from, to) <- selectors) {
	    writeRef(from)
	    writeRef(to)
	  }
	  TREE


	case tree@Annotation(constr, elements) =>                            
	  writeNat(ANNOTATIONtree)
	  writeRef(tree.tpe)
	  writeRef(constr)
	  writeRefs(elements)
	  TREE

	case tree@DocDef(comment, definition) =>
          writeNat(DOCDEFtree)
	  writeRef(tree.tpe)
	  writeRef(Constant(comment))
	  writeRef(definition)
	  TREE

	case tree@Template(parents, self, body) =>
          writeNat(TEMPLATEtree)
	  writeRef(tree.tpe)
          writeRef(tree.symbol)
          writeNat(parents.length)
	  writeRefs(parents)
	  writeRef(self)
	  writeRefs(body)
	  TREE

	case tree@Block(stats, expr) =>
          writeNat(BLOCKtree)
	  writeRef(tree.tpe)
	  writeRef(expr)
	  writeRefs(stats)
	  TREE

	case tree@CaseDef(pat, guard, body) =>
	  writeNat(CASEtree)
	  writeRef(tree.tpe)
	  writeRef(pat)
	  writeRef(guard)
	  writeRef(body)
	  TREE

	case tree@Sequence(trees) =>
          writeNat(SEQUENCEtree)
	  writeRef(tree.tpe)
	  writeRefs(trees)
	  TREE

	case tree@Alternative(trees) =>
	  writeNat(ALTERNATIVEtree)
	  writeRef(tree.tpe)
	  writeRefs(trees)
	  TREE

	case tree@Star(elem) =>
          writeNat(STARtree)
	  writeRef(tree.tpe)
	  writeRef(elem)
	  TREE

	case tree@Bind(name, body) =>
	  writeNat(BINDtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(name)
	  writeRef(body)
	  TREE

	case tree@UnApply(fun: Tree, args) =>
	  writeNat(UNAPPLYtree)
	  writeRef(tree.tpe)
	  writeRef(fun)
	  writeRefs(args)
	  TREE

	case tree@ArrayValue(elemtpt, trees) =>
	  writeNat(ARRAYVALUEtree)
	  writeRef(tree.tpe)
	  writeRef(elemtpt)
	  writeRefs(trees)
	  TREE


	case tree@Function(vparams, body) =>
          writeNat(FUNCTIONtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(body)
	  writeRefs(vparams)
	  TREE

	case tree@Assign(lhs, rhs) =>
          writeNat(ASSIGNtree)
	  writeRef(tree.tpe)
	  writeRef(lhs)
	  writeRef(rhs)
	  TREE

	case tree@If(cond, thenp, elsep) =>
          writeNat(IFtree)
          writeRef(tree.tpe)
	  writeRef(cond)
	  writeRef(thenp)
	  writeRef(elsep)
	  TREE

	case tree@Match(selector, cases) =>
          writeNat(MATCHtree)
	  writeRef(tree.tpe)
	  writeRef(selector)
	  writeRefs(cases)
	  TREE

	case tree@Return(expr) =>
	  writeNat(RETURNtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(expr)
	  TREE

	case tree@Try(block, catches, finalizer) =>
          writeNat(TREtree)
	  writeRef(tree.tpe)
	  writeRef(block)
	  writeRef(finalizer)
	  writeRefs(catches)
	  TREE

	case tree@Throw(expr) =>
	  writeNat(THROWtree)
	  writeRef(tree.tpe)
	  writeRef(expr)
	  TREE

	case tree@New(tpt) =>
	  writeNat(NEWtree)
	  writeRef(tree.tpe)
	  writeRef(tpt)
	  TREE
  
	case tree@Typed(expr, tpt) =>
	  writeNat(TYPEDtree)
	  writeRef(tree.tpe)
	  writeRef(expr)
	  writeRef(tpt)
	  TREE

	case tree@TypeApply(fun, args) =>
	  writeNat(TYPEAPPLYtree)
	  writeRef(tree.tpe)
	  writeRef(fun)
	  writeRefs(args)
	  TREE
	  
	case tree@Apply(fun, args) =>
	  writeNat(APPLYtree)
	  writeRef(tree.tpe)
	  writeRef(fun)
	  writeRefs(args)
	  TREE

	case tree@ApplyDynamic(qual, args) =>
	  writeNat(APPLYDYNAMICtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(qual)
	  writeRefs(args)
	  TREE

	case tree@Super(qual, mix) =>
	  writeNat(SUPERtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(qual)
	  writeRef(mix)
	  TREE

        case tree@This(qual) =>
	  writeNat(THIStree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(qual)
	  TREE

        case tree@Select(qualifier, selector) =>
	  writeNat(SELECTtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(qualifier)
	  writeRef(selector)
	  TREE

	case tree@Ident(name) =>
	  writeNat(IDENTtree)
	  writeRef(tree.tpe)
	  writeRef(tree.symbol)
	  writeRef(name)
	  TREE

	case tree@Literal(value) =>
	  writeNat(LITERALtree)
	  writeRef(tree.tpe)
	  writeRef(value)
	  TREE

	case tree@TypeTree() =>
	  writeNat(TYPEtree)
	  writeRef(tree.tpe)
	  TREE

	case tree@Annotated(annot, arg) =>
	  writeNat(ANNOTATEDtree)
	  writeRef(tree.tpe)
	  writeRef(annot)
	  writeRef(arg)
	  TREE

	case tree@SingletonTypeTree(ref) =>
	  writeNat(SINGLETONTYPEtree)
	  writeRef(tree.tpe)
	  writeRef(ref)
	  TREE

	case tree@SelectFromTypeTree(qualifier, selector) =>
	  writeNat(SELECTFROMTYPEtree)
	  writeRef(tree.tpe)
	  writeRef(qualifier)
	  writeRef(selector)
	  TREE

	case tree@CompoundTypeTree(templ: Template) =>
	  writeNat(COMPOUNDTYPEtree)
	  writeRef(tree.tpe)
	  writeRef(templ)
	  TREE

	case tree@AppliedTypeTree(tpt, args) =>
	  writeNat(APPLIEDTYPEtree)
	  writeRef(tree.tpe)
	  writeRef(tpt)
	  writeRefs(args)
	  TREE

	case tree@TypeBoundsTree(lo, hi) =>
	  writeNat(TYPEBOUNDStree)
	  writeRef(tree.tpe)
	  writeRef(lo)
	  writeRef(hi)
	  TREE

	case tree@ExistentialTypeTree(tpt, whereClauses) =>
	  writeNat(EXISTENTIALTYPEtree)
	  writeRef(tree.tpe)
	  writeRef(tpt)
	  writeRefs(whereClauses)
	  TREE


	case Modifiers(flags, privateWithin, annotations) =>
	  writeNat((flags >> 32).toInt)
	  writeNat((flags & 0xFFFFFFFF).toInt)
	  writeRef(privateWithin)
          writeRefs(annotations)
	  MODIFIERS

        case AnnotationInfo(atp, args, assocs) =>
          writeRef(atp)
          writeNat(args.length)
          for (arg <- args) writeRef(arg)
          for ((name, arg) <- assocs) {
            writeRef(name);
            writeRef(arg)
          }
          ANNOTINFO

	case arg:AnnotationArgument =>
	  arg.constant match {
	    case Some(c) => writeBody(c)
	    case None => writeBody(arg.intTree)
	  }

        case _ =>
          throw new FatalError("bad entry: " + entry + " " + entry.getClass)
      }

      // begin writeEntry
      val startpos = writeIndex
      writeByte(0); writeByte(0)
      patchNat(startpos, writeBody(entry))
      patchNat(startpos + 1, writeIndex - (startpos + 2))
    }

    /** Print entry for diagnostics */
    private def printEntry(entry: AnyRef) {
      def printRef(ref: AnyRef) { 
        print(index(ref)+
              (if (ref.isInstanceOf[Name]) "("+ref+") " else " "))
      }
      def printRefs(refs: List[AnyRef]) { refs foreach printRef }
      def printSymInfo(sym: Symbol) {
        var posOffset = 0
        printRef(sym.name)
        printRef(normalizedOwner(sym))
        print(flagsToString(sym.flags & PickledFlags)+" ")
        if (sym.privateWithin != NoSymbol) printRef(sym.privateWithin)
        printRef(sym.info)
      }
      def printBody(entry: AnyRef) = entry match {
        case name: Name =>
          print((if (name.isTermName) "TERMname " else "TYPEname ")+name)
        case NoSymbol =>
          print("NONEsym")
        case sym: Symbol if !isLocal(sym) =>
          if (sym.isModuleClass) {
            print("EXTMODCLASSref "); printRef(sym.name.toTermName)
          } else {
            print("EXTref "); printRef(sym.name)
          }
          if (!sym.owner.isRoot) printRef(sym.owner)
        case sym: ClassSymbol =>
          print("CLASSsym ")
          printSymInfo(sym)
          if (sym.thisSym.tpe != sym.tpe) printRef(sym.typeOfThis)
        case sym: TypeSymbol =>
          print(if (sym.isAbstractType) "TYPEsym " else "ALIASsym ")
          printSymInfo(sym)
        case sym: TermSymbol =>
          print(if (sym.isModule) "MODULEsym " else "VALsym ")
          printSymInfo(sym)
          if (sym.alias != NoSymbol) printRef(sym.alias)
        case NoType =>
          print("NOtpe")
        case NoPrefix =>
          print("NOPREFIXtpe")
        case ThisType(sym) =>
          print("THIStpe "); printRef(sym)
        case SingleType(pre, sym) =>
          print("SINGLEtpe "); printRef(pre); printRef(sym); 
        case ConstantType(value) =>
          print("CONSTANTtpe "); printRef(value); 
        case TypeRef(pre, sym, args) =>
          print("TYPEREFtpe "); printRef(pre); printRef(sym); printRefs(args); 
        case TypeBounds(lo, hi) =>
          print("TYPEBOUNDStpe "); printRef(lo); printRef(hi); 
        case tp @ RefinedType(parents, decls) =>
          print("REFINEDtpe "); printRef(tp.typeSymbol); printRefs(parents); 
        case ClassInfoType(parents, decls, clazz) =>
          print("CLASSINFOtpe "); printRef(clazz); printRefs(parents); 
        case MethodType(formals, restpe) =>
          print(if (entry.isInstanceOf[ImplicitMethodType]) "IMPLICITMETHODtpe " else "METHODtpe ");
          printRef(restpe); printRefs(formals)
        case PolyType(tparams, restpe) =>
          print("POLYtpe "); printRef(restpe); printRefs(tparams); 
        case ExistentialType(tparams, restpe) =>
          print("EXISTENTIALtpe "); printRef(restpe); printRefs(tparams); 
        case DeBruijnIndex(l, i) =>
          print("DEBRUIJNINDEXtpe "); print(l+" "+i)
        case c @ Constant(_) =>
          print("LITERAL ")
          if (c.tag == BooleanTag) print("Boolean "+(if (c.booleanValue) 1 else 0))
          else if (c.tag == ByteTag) print("Byte "+c.longValue)
          else if (c.tag == ShortTag) print("Short "+c.longValue)
          else if (c.tag == CharTag) print("Char "+c.longValue)
          else if (c.tag == IntTag) print("Int "+c.longValue)
          else if (c.tag == LongTag) print("Long "+c.longValue)
          else if (c.tag == FloatTag) print("Float "+c.floatValue)
          else if (c.tag == DoubleTag) print("Double "+c.doubleValue)
          else if (c.tag == StringTag) { print("String "); printRef(newTermName(c.stringValue)) }
          else if (c.tag == ClassTag) { print("Class "); printRef(c.typeValue) }
        case AnnotatedType(attribs, tp, selfsym) => 
	  if (settings.selfInAnnots.value) {
            print("ANNOTATEDWSELFtpe ")
	    printRef(tp)
	    printRef(selfsym)
	    printRefs(attribs)
	  } else {
            print("ANNOTATEDtpe ")
            printRef(tp)
            printRefs(attribs)
	  }
        case (target: Symbol, attr @ AnnotationInfo(atp, args, assocs)) =>
          print("ATTRIBUTE ")
          printRef(target)
          printRef(atp)
          for (c <- args) printRef(c)
          for ((name, c) <- assocs) { printRef(name); printRef(c) }
        case (target: Symbol, children: List[_]) =>
          print("CHILDREN ")
          printRef(target)
          for (c <- children) printRef(c.asInstanceOf[Symbol])
        case _ =>
          throw new FatalError("bad entry: " + entry + " " + entry.getClass)
      }
      printBody(entry); println()
    }

    /** Write byte array */
    def finish {
      assert(writeIndex == 0)
      writeNat(MajorVersion)
      writeNat(MinorVersion)
      writeNat(ep)
      if (showSig) {
        println("Pickled info for "+rootName+" V"+MajorVersion+"."+MinorVersion)
      }
      for (i <- 0 until ep) {
        if (showSig) {
          print((i formatted "%3d: ")+(writeIndex formatted "%5d: "))
          printEntry(entries(i))
        }
        writeEntry(entries(i))
      }
      if (settings.Xshowcls.value == rootName.toString) {
        readIndex = 0
        ShowPickled.printFile(this, Console.out)
      }
    }

    override def toString() = "" + rootName + " in " + rootOwner
  }
}
