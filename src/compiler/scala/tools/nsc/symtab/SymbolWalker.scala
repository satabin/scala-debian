package scala.tools.nsc.symtab

trait SymbolWalker {
  val global : Global 
  import scala.tools.nsc.util._
  import global._
  import scala.collection.jcl._
  trait Visitor {
    def update(pos : Position, sym : Symbol) : Unit
    def contains(pos : Position) : Boolean
    def apply(pos : Position) : Symbol
    def putDef(sym : Symbol, pos : Position) : Unit = ()
  }
  import scala.collection.mutable.Map
  /*
  implicit def map2use(map : Map[Position,Symbol]) = new Visitor {
    def update(pos : Position, sym : Symbol) : Unit = map.update(pos, sym)
    def contains(pos : Position) : Boolean = map.contains(pos)
    def apply(pos : Position) : Symbol = map.apply(pos)
  }
  */
  def walk(tree: Tree, visitor : Visitor)(fid : (util.Position) => Option[String]) : Unit = {
    val visited = new LinkedHashSet[Tree]
    def f(t : Tree) : Unit = {
      if (!visited.add(t)) return
      def fs(l : List[Tree]) : Unit = {
        val i = l.elements
        while (i.hasNext) f(i.next)
      }
      def fss(l : List[List[Tree]]) : Unit = {
        val i = l.elements
        while (i.hasNext) fs(i.next)
      }
      if (t.isInstanceOf[StubTree]) return
      def asTypeRef = t.tpe.asInstanceOf[TypeRef]
      val sym = (t,t.tpe) match {
        case (Super(_,_),SuperType(_,supertp)) if supertp.typeSymbol != NoSymbol && supertp.typeSymbol != null => supertp.typeSymbol
        case _ if t.symbol != NoSymbol && t.symbol != null => t.symbol
        case (t : TypeTree, tp) if tp != null && tp.typeSymbol != null && tp.typeSymbol != NoSymbol => tp.typeSymbol 
        case (t : TypeTree, tp) if tp != null && tp.resultType != null && tp.resultType.typeSymbol != null => tp.resultType.typeSymbol 
        case (t, tpe : Type) if tpe != null && (t.symbol eq NoSymbol) && t.isTerm && tpe.termSymbol != null => 
          tpe.termSymbol
        case (t, tpe : Type) if tpe != null && (t.symbol eq NoSymbol) && tpe.typeSymbol != null => 
              if (t.tpe.isInstanceOf[TypeRef]) asTypeRef.sym // XXX: looks like a bug
             else tpe.typeSymbol 
        case _ => NoSymbol
      }
      if (sym != null && sym != NoSymbol /* && !sym.hasFlag(SYNTHETIC) */) {
        var id = fid(t.pos) 
        val doAdd = if (id.isDefined) {
          if (id.get.charAt(0) == '`') id = Some(id.get.substring(1, id.get.length - 1))
          val name = sym.name.decode.trim
          if ((name startsWith id.get) || (id.get startsWith name)) true 
          else {
            false
          }
        } else false
        if (doAdd) {
          
        if (!visitor.contains(t.pos)) {
          visitor(t.pos) = sym
        } else {
          val existing = visitor(t.pos)
          if (sym.sourceFile != existing.sourceFile || sym.pos != existing.pos) {
            (sym,existing) match {
            case (sym,existing) if sym.pos == existing.pos => 
            case (sym : TypeSymbol ,_ : ClassSymbol) => visitor(t.pos) = sym
            case (_ : ClassSymbol,_ : TypeSymbol) => // nothing
            case _ if sym.isModule && existing.isValue => // nothing
            case _ if sym.isClass && existing.isMethod => // nothing
            case _ =>
              assert(true)
            }
          }
        }}
      }
      t match {
      case t : DefTree if t.symbol != NoSymbol =>
        if (t.pos != NoPosition)
          visitor.putDef(t.symbol, t.pos)
          if (t.symbol.isClass) {
            val factory = NoSymbol // XXX: t.symbol.caseFactory
            if (factory != NoSymbol) {
              visitor.putDef(factory, t.pos)
            }
        }
      case t : TypeBoundsTree => f(t.lo); f(t.hi)
      case t : TypeTree if t.original != null => 
        def h(original : Tree, tpe : Type): Unit = try {
          if (original.tpe == null)
            original.tpe = tpe
          (original) match {
          case (AppliedTypeTree(_,trees)) if tpe.isInstanceOf[TypeRef] => 
            val types = tpe.asInstanceOf[TypeRef].args
            trees.zip(types).foreach{
            case (tree,tpe) => assert(tree != null && tpe != null); h(tree, tpe)
            }
          case _ => 
          }
        }
        if (t.original.tpe == null) {
          val dup = t.original.duplicate
          h(dup,t.tpe)
          f(dup)
        } else f(t.original)
        ()
      case _ =>
      }
      (t) match {
      case (t : MemberDef) if t.symbol != null && t.symbol != NoSymbol => 
        val annotated = if (sym.isModule) sym.moduleClass else sym
        val i = t.mods.annotations.elements
        val j = annotated.attributes.elements
        while (i.hasNext && j.hasNext) {
          val tree = i.next.constr
          val ainfo = j.next
          val sym = ainfo.atp.typeSymbol
          tree.setType(ainfo.atp)
          tree.setSymbol(sym)
          f(tree)
        }

      case _ =>  
      }
      t match {
      case tree: ImplDef => 
        fs(tree.impl.parents); f(tree.impl.self); fs(tree.impl.body) 
        tree match {
        case tree : ClassDef => fs(tree.tparams)
        case _ =>
        }
      case tree: PackageDef => fs(tree.stats)
      case tree: ValOrDefDef => 
        f(tree.rhs); 
        if (tree.tpt != null) {
          f(tree.tpt)
        }
        tree match {
        case tree : DefDef => fs(tree.tparams); fss(tree.vparamss)
        case _ => 
        }
      case tree: Function => fs(tree.vparams); f(tree.body)
      case tree : Bind => f(tree.body)
      case tree : Select => 
        val qualifier = if (tree.tpe != null && tree.qualifier.tpe == null) {
          val pre = tree.tpe.prefix
          val qualifier = tree.qualifier.duplicate
          qualifier.tpe = pre
          qualifier
        } else tree.qualifier
      
        f(qualifier)
      case tree : Annotation => f(tree.constr)
      case tree : Annotated => f(tree.annot); f(tree.arg)
      case tree : GenericApply => f(tree.fun); fs(tree.args)
      case tree : UnApply => f(tree.fun); fs(tree.args)
      case tree : AppliedTypeTree =>
        if (tree.tpe != null) {
          val i = tree.tpe.typeArgs.elements
          val j = tree.args.elements
          while (i.hasNext && j.hasNext) {
            val tpe = i.next
            val arg = j.next
            if (arg.tpe == null) {
              arg.tpe = tpe  
            }
          }
          if (tree.tpt.tpe == null) {
            tree.tpt.tpe = tree.tpe
          }
          
        }
        f(tree.tpt); fs(tree.args) 

      case tree : ExistentialTypeTree=>
        if (tree.tpt.tpe == null) {
          tree.tpt.tpe = tree.tpe
        }
        
        f(tree.tpt)
        fs(tree.whereClauses)
      case tree : SingletonTypeTree => 
        if (tree.ref.tpe == null) {
          val dup = tree.ref.duplicate
          dup.tpe = tree.tpe
          f(dup)
        } else f(tree.ref)
      case tree : CompoundTypeTree => 
        if (tree.tpe != null && tree.tpe.typeSymbol != null && tree.tpe.typeSymbol.isRefinementClass) tree.tpe.typeSymbol.info match {
        case tpe : RefinedType => 
          tpe.parents.zip(tree.templ.parents).foreach{
          case (tpe,tree) => 
            if (tree.hasSymbol && (tree.symbol == NoSymbol || tree.symbol == null)) {
              tree.symbol = tpe.typeSymbol
            }
          }
        
        case _ =>
        }
        
        f(tree.templ)
      case tree : Template => fs(tree.parents); f(tree.self); fs(tree.body)
      case tree : SelectFromTypeTree => {
        if (tree.qualifier.tpe == null) tree.tpe match {
        case tpe : TypeRef =>
          // give it a type!
          tree.qualifier.tpe = tpe.prefix
        case _ => 
          // tree.tpe.pre
        }
        f(tree.qualifier)
      }
      case tree : Literal => 
      /*
        if (tree.tpe != null && tree.tpe.typeSymbol == definitions.ClassClass) {
          // nothing we can do without original tree.
        }
      */
      
      case tree : Typed => f(tree.expr); f(tree.tpt)
      case tree : Block => fs(tree.stats); f(tree.expr)
      case tree: CaseDef => f(tree.pat);f(tree.guard);f(tree.body)
      case tree : Sequence   => fs(tree.trees);
      case tree : Assign     => f(tree.lhs); f(tree.rhs);
      case tree : If         => f(tree.cond); f(tree.thenp); f(tree.elsep);
      case tree : New        => f(tree.tpt);
      case tree : Match      => f(tree.selector); fs(tree.cases);
      case tree : Return     => f(tree.expr);
      case tree : LabelDef   => f(tree.rhs);
      case tree : Throw      => f(tree.expr);
      case tree : Try        => f(tree.block); fs(tree.catches); f(tree.finalizer);
      case tree : Alternative => fs(tree.trees);
      case tree : TypeDef => 
        (tree.tpe,sym) match {
          case (null,sym : TypeSymbol) if (sym.rawInfo.isComplete) =>
            if (tree.tparams.isEmpty) {
              if (tree.rhs.tpe == null) tree.rhs.tpe = sym.info
              f(tree.rhs)
            } else {
              val tree0 = AppliedTypeTree(tree.rhs, tree.tparams)
              tree0.tpe = sym.info
              f(tree0)
            }
          case _ => f(tree.rhs); fs(tree.tparams)
        }
      case tree : DocDef     => f(tree.definition);
      case tree: Import => f(tree.expr)
      case _ => 
      }
    }
    f(tree)
  }
  
}
