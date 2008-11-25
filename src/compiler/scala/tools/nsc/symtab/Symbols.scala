/* NSC -- new Scala compiler
 * Copyright 2005-2008 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: Symbols.scala 15700 2008-08-05 12:08:02Z odersky $

package scala.tools.nsc.symtab

import scala.collection.mutable.ListBuffer
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.{Position, NoPosition, BatchSourceFile}
import Flags._

//todo: get rid of MONOMORPHIC flag

trait Symbols {
  self: SymbolTable =>
  import definitions._

  private var ids = 0

  //for statistics:
  def symbolCount = ids
  var typeSymbolCount = 0
  var classSymbolCount = 0

  val emptySymbolArray = new Array[Symbol](0)
  val emptySymbolSet = Set.empty[Symbol]
/*                                 
  type Position;
  def NoPos : Position;
  def FirstPos : Position;
  implicit def coercePosToInt(pos : Position) : Int;
  def coerceIntToPos(pos : Int) : Position;
  object RequiresIntsAsPositions {
    implicit def coerceIntToPos0(pos: Int) =
      coerceIntToPos(pos)
  }
  */
  /** The class for all symbols */
  abstract class Symbol(initOwner: Symbol, initPos: Position, initName: Name) {

    var rawowner = initOwner
    var rawname = initName
    var rawflags: Long = 0
    private var rawpos = initPos
    val id = { ids += 1; ids }
//    assert(id != 7498, initName+"/"+initOwner)

    var validTo: Period = NoPeriod

    def pos = rawpos
    def setPos(pos: Position): this.type = { this.rawpos = pos; this }
 
    def namePos(source: BatchSourceFile) = {
      val pos: Int = this.pos.offset.getOrElse(-1)
      val buf = source.content
      if (pos == -1) -1
      else if (isTypeParameter) pos - name.length
      else if (isVariable || isMethod || isClass || isModule) {
        var ret = pos
        if (buf(pos) == ',') ret += 1
        else if (isClass)  ret += "class".length()
        else if (isModule) ret += "object".length()
        else ret += "var".length()
        while (buf(ret).isWhitespace) ret += 1
        ret
      }
      else if (isValue) {
        if (pos < (buf.length + ("val ").length())) {
          if ((buf(pos + 0) == 'v') &&
              (buf(pos + 1) == 'a') &&
              (buf(pos + 2) == 'l') &&
              (buf(pos + 3) == ' ')) {
            var pos0 = pos + 4
            while (pos0 < buf.length && buf(pos0).isWhitespace)
              pos0 += 1
            pos0

          } else pos
        } else pos
      }
      else -1
    }

    var attributes: List[AnnotationInfo] = List()

    def setAttributes(attrs: List[AnnotationInfo]): this.type = { this.attributes = attrs; this }
    
    /** Does this symbol have an attribute of the given class? */
    def hasAttribute(cls: Symbol): Boolean = 
      attributes.exists { 
        case AnnotationInfo(tp, _, _) if tp.typeSymbol == cls => true
        case _ => false
    }

    var privateWithin: Symbol = _

// Creators -------------------------------------------------------------------

    final def newValue(pos: Position, name: Name) = 
      new TermSymbol(this, pos, name)
    final def newVariable(pos: Position, name: Name) =
      newValue(pos, name).setFlag(MUTABLE)
    final def newValueParameter(pos: Position, name: Name) =
      newValue(pos, name).setFlag(PARAM)
    final def newLocalDummy(pos: Position) =
      newValue(pos, nme.LOCAL(this)).setInfo(NoType)
    final def newMethod(pos: Position, name: Name) =
      newValue(pos, name).setFlag(METHOD)
    final def newLabel(pos: Position, name: Name) =
      newMethod(pos, name).setFlag(LABEL)
    final def newConstructor(pos: Position) =
      newMethod(pos, nme.CONSTRUCTOR)
    final def newModule(pos: Position, name: Name, clazz: ClassSymbol) =
      new ModuleSymbol(this, pos, name).setFlag(MODULE | FINAL)
        .setModuleClass(clazz)
    final def newModule(pos: Position, name: Name) = {
      val m = new ModuleSymbol(this, pos, name).setFlag(MODULE | FINAL)
      m.setModuleClass(new ModuleClassSymbol(m))
    } 
    final def newPackage(pos: Position, name: Name) = {
      assert(name == nme.ROOT || isPackageClass)
      val m = newModule(pos, name).setFlag(JAVA | PACKAGE)
      m.moduleClass.setFlag(JAVA | PACKAGE)
      m
    }
    final def newThisSym(pos: Position) =
      newValue(pos, nme.this_).setFlag(SYNTHETIC)
    final def newImport(pos: Position) =
      newValue(pos, nme.IMPORT)
    final def newOverloaded(pre: Type, alternatives: List[Symbol]): Symbol =
      newValue(alternatives.head.pos, alternatives.head.name)
      .setFlag(OVERLOADED)
      .setInfo(OverloadedType(pre, alternatives))

    final def newOuterAccessor(pos: Position) = {
      val sym = newMethod(pos, nme.OUTER) 
      sym setFlag (STABLE | SYNTHETIC)
      if (isTrait) sym setFlag DEFERRED
      sym.expandName(this)
      sym.referenced = this
      sym
    }

    final def newErrorValue(name: Name) =
      newValue(pos, name).setFlag(SYNTHETIC | IS_ERROR).setInfo(ErrorType)
    final def newAliasType(pos: Position, name: Name) =
      new TypeSymbol(this, pos, name)
    final def newAbstractType(pos: Position, name: Name) =
      new TypeSymbol(this, pos, name).setFlag(DEFERRED)
    final def newTypeParameter(pos: Position, name: Name) =
      newAbstractType(pos, name).setFlag(PARAM)
    final def newTypeSkolem: Symbol =
      new TypeSkolem(owner, pos, name, this)
        .setFlag(flags)
    final def newClass(pos: Position, name: Name) =
      new ClassSymbol(this, pos, name)
    final def newModuleClass(pos: Position, name: Name) =
      new ModuleClassSymbol(this, pos, name)
    final def newAnonymousClass(pos: Position) =
      newClass(pos, nme.ANON_CLASS_NAME.toTypeName)
    final def newAnonymousFunctionClass(pos: Position) = {
      val anonfun = newClass(pos, nme.ANON_FUN_NAME.toTypeName)
      def firstNonSynOwner(chain: List[Symbol]): Symbol = (chain: @unchecked) match {
        case o :: os => if (o != this && !(o hasFlag SYNTHETIC) && o.isClass) o else firstNonSynOwner(os)
      }
      val ownerSerial = firstNonSynOwner(ownerChain) hasAttribute SerializableAttr
      if (ownerSerial)
        anonfun.attributes =
          AnnotationInfo(definitions.SerializableAttr.tpe, List(), List()) :: anonfun.attributes
      anonfun
    }
    final def newRefinementClass(pos: Position) =
      newClass(pos, nme.REFINE_CLASS_NAME.toTypeName)
    final def newErrorClass(name: Name) = {
      val clazz = newClass(pos, name).setFlag(SYNTHETIC | IS_ERROR)
      clazz.setInfo(ClassInfoType(List(), new ErrorScope(this), clazz))
      clazz
    }
    final def newErrorSymbol(name: Name): Symbol =
      if (name.isTypeName) newErrorClass(name) else newErrorValue(name)

// Tests ----------------------------------------------------------------------

    def isTerm   = false         //to be overridden
    def isType   = false         //to be overridden
    def isClass  = false         //to be overridden
    def isTypeMember = false     //to be overridden
    def isAliasType = false      //to be overridden
    def isAbstractType = false   //to be overridden
    def isSkolem = false         //to be overridden

    final def isValue = isTerm && !(isModule && hasFlag(PACKAGE | JAVA))
    final def isVariable  = isTerm && hasFlag(MUTABLE) && !isMethod
    final def isCapturedVariable  = isVariable && hasFlag(CAPTURED)

    final def isGetter = isTerm && hasFlag(ACCESSOR) && !nme.isSetterName(name)
    final def isSetter = isTerm && hasFlag(ACCESSOR) && nme.isSetterName(name)
       //todo: make independent of name, as this can be forged.
    final def hasGetter = isTerm && nme.isLocalName(name)
    final def isValueParameter = isTerm && hasFlag(PARAM)
    final def isLocalDummy = isTerm && nme.isLocalDummyName(name)
    final def isMethod = isTerm && hasFlag(METHOD)
    final def isSourceMethod = isTerm && (flags & (METHOD | STABLE)) == METHOD
    final def isLabel = isMethod && !hasFlag(ACCESSOR) && hasFlag(LABEL)
    final def isInitializedToDefault = !isType && (getFlag(DEFAULTINIT | ACCESSOR) == (DEFAULTINIT | ACCESSOR))
    final def isClassConstructor = isTerm && (name == nme.CONSTRUCTOR)
    final def isMixinConstructor = isTerm && (name == nme.MIXIN_CONSTRUCTOR)
    final def isConstructor = isTerm && (name == nme.CONSTRUCTOR) || (name == nme.MIXIN_CONSTRUCTOR)
    final def isModule = isTerm && hasFlag(MODULE)
    final def isStaticModule = isModule && isStatic && !isMethod
    final def isPackage = isModule && hasFlag(PACKAGE)
    final def isThisSym = isTerm && owner.thisSym == this
    //final def isMonomorphicType = isType && hasFlag(MONOMORPHIC)
    final def isError = hasFlag(IS_ERROR)
    final def isErroneous = isError || isInitialized && tpe.isErroneous
    final def isTrait = isClass & hasFlag(TRAIT | notDEFERRED)     // A virtual class becomes a trait (part of DEVIRTUALIZE)
    final def isTypeParameterOrSkolem = isType && hasFlag(PARAM)
    final def isTypeSkolem            = isSkolem && hasFlag(PARAM)
    final def isTypeParameter         = isTypeParameterOrSkolem && !isSkolem
    final def isExistential           = isType && hasFlag(EXISTENTIAL)
    final def isExistentialSkolem     = isSkolem && hasFlag(EXISTENTIAL)
    final def isExistentialQuantified = isExistential && !isSkolem
    final def isClassLocalToConstructor = isClass && hasFlag(INCONSTRUCTOR)
    final def isAnonymousClass = isClass && (originalName startsWith nme.ANON_CLASS_NAME)
      // startsWith necessary because name may grow when lifted and also because of anonymous function classes
    def isAnonymousFunction = hasFlag(SYNTHETIC) && (originalName startsWith nme.ANON_FUN_NAME)
    final def isRefinementClass = isClass && name == nme.REFINE_CLASS_NAME.toTypeName; // no lifting for refinement classes
    final def isModuleClass = isClass && hasFlag(MODULE)
    final def isPackageClass = isClass && hasFlag(PACKAGE)
    final def isRoot = isPackageClass && name == nme.ROOT.toTypeName
    final def isRootPackage = isPackage && name == nme.ROOTPKG
    final def isEmptyPackage = isPackage && name == nme.EMPTY_PACKAGE_NAME
    final def isEmptyPackageClass = isPackageClass && name == nme.EMPTY_PACKAGE_NAME.toTypeName
    final def isPredefModule = isModule && name == nme.Predef // not printed as a prefix
    final def isScalaPackage = isPackage && name == nme.scala_ // not printed as a prefix
    final def isScalaPackageClass = isPackageClass && name == nme.scala_.toTypeName // not printed as a prefix
    
    /** Is symbol a monomophic type?
     *  assumption: if a type starts out as monomorphic, it will not acquire 
     *  type parameters in later phases.
     */
    final def isMonomorphicType = 
      isType && {
        var is = infos
        (is eq null) || {
          while (is.prev ne null) { is = is.prev }
          is.info.isComplete && is.info.typeParams.isEmpty
        }
      }

    def isDeprecated = 
      attributes exists (attr => attr.atp.typeSymbol == DeprecatedAttr)

    /** Does this symbol denote a wrapper object of the interpreter or its class? */
    final def isInterpreterWrapper = 
      (isModule || isModuleClass) && 
      owner.isEmptyPackageClass && 
      name.toString.startsWith(nme.INTERPRETER_LINE_PREFIX) &&
      name.toString.endsWith(nme.INTERPRETER_WRAPPER_SUFFIX)

    /** Is this symbol an accessor method for outer? */
    final def isOuterAccessor = {
      hasFlag(STABLE | SYNTHETIC) &&
      originalName == nme.OUTER 
    }

    /** Does this symbol denote a stable value? */
    final def isStable =
      isTerm && !hasFlag(MUTABLE) && (!hasFlag(METHOD | BYNAMEPARAM) || hasFlag(STABLE))

    def isDeferred = 
      hasFlag(DEFERRED) && !isClass

    def isVirtualClass = 
      hasFlag(DEFERRED) && isClass

    def isVirtualTrait = 
      hasFlag(DEFERRED) && isTrait

    /** Is this symbol a public */
    final def isPublic: Boolean =
      !hasFlag(PRIVATE | PROTECTED) && privateWithin == NoSymbol

    /** Is this symbol a private local */
    final def isPrivateLocal = 
      hasFlag(PRIVATE) && hasFlag(LOCAL)

    /** Is this symbol a protected local */
    final def isProtectedLocal = 
      hasFlag(PROTECTED) && hasFlag(LOCAL)

    /** Does this symbol denote the primary constructor of its enclosing class? */
    final def isPrimaryConstructor =
      isConstructor && owner.primaryConstructor == this

    /** Is this symbol a synthetic apply or unapply method in a companion object of a case class? */
    final def isCaseApplyOrUnapply = 
      isMethod && hasFlag(CASE) && hasFlag(SYNTHETIC)

    /** Is this symbol an implementation class for a mixin? */
    final def isImplClass: Boolean = isClass && hasFlag(IMPLCLASS)

    /** Is thhis symbol early initialized */
    final def isEarly: Boolean = isTerm && hasFlag(PRESUPER)

    /** Is this symbol a trait which needs an implementation class? */
    final def needsImplClass: Boolean =
      isTrait && (!hasFlag(INTERFACE) || hasFlag(lateINTERFACE)) && !isImplClass

    /** Is this a symbol which exists only in the implementation class, not in its trait? */
    final def isImplOnly: Boolean =
      hasFlag(PRIVATE) ||
      (owner.isImplClass || owner.isTrait) &&
      ((hasFlag(notPRIVATE | LIFTED) && !hasFlag(ACCESSOR | SUPERACCESSOR | MODULE) || isConstructor) ||
       (hasFlag(LIFTED) && isModule && isMethod))

    /** Is this symbol a module variable ? */
    final def isModuleVar: Boolean = isVariable && hasFlag(MODULEVAR)

    /** Is this symbol static (i.e. with no outer instance)? */
    final def isStatic: Boolean =
      hasFlag(STATIC) || isRoot || owner.isStaticOwner

    /** Is this symbol a static member of its class? (i.e. needs to be implemented as a Java static?) */
    final def isStaticMember: Boolean = 
      hasFlag(STATIC) || owner.isImplClass

    /** Does this symbol denote a class that defines static symbols? */
    final def isStaticOwner: Boolean =
      isPackageClass || isModuleClass && isStatic

    /** Is this symbol final?*/
    final def isFinal: Boolean = (
      hasFlag(FINAL) ||
      isTerm && (
        hasFlag(PRIVATE) || isLocal || owner.isClass && owner.hasFlag(FINAL | MODULE))
    )

    /** Is this symbol a sealed class?*/
    final def isSealed: Boolean =
      isClass && (hasFlag(SEALED) || isUnboxedClass(this))

    /** Is this symbol locally defined? I.e. not accessed from outside `this' instance */
    final def isLocal: Boolean = owner.isTerm

    /** Is this symbol a constant? */
    final def isConstant: Boolean =
      isStable && (tpe match {
        case ConstantType(_) => true
        case PolyType(_, ConstantType(_)) => true
        case MethodType(_, ConstantType(_)) => true
        case _ => false
      })

    /** Is this class nested in another class or module (not a package)? */
    final def isNestedClass: Boolean =
      isClass && !isRoot && !owner.isPackageClass

    /** Is this class locally defined?
     *  A class is local, if
     *   - it is anonymous, or
     *   - its owner is a value
     *   - it is defined within a local class
     */
    final def isLocalClass: Boolean =
      isClass && (isAnonymousClass || isRefinementClass || isLocal ||
                  !owner.isPackageClass && owner.isLocalClass)

    /** A a member of class `base' is incomplete if
     *  (1) it is declared deferred or
     *  (2) it is abstract override and its super symbol in `base' is
     *      nonexistent or inclomplete.
     *
     *  @param base ...
     *  @return     ...
     */
    final def isIncompleteIn(base: Symbol): Boolean =
      this.isDeferred ||
      (this hasFlag ABSOVERRIDE) && {
        val supersym = superSymbol(base)
        supersym == NoSymbol || supersym.isIncompleteIn(base)
      }

    final def exists: Boolean =
      this != NoSymbol && (!owner.isPackageClass || { rawInfo.load(this); rawInfo != NoType })

    final def isInitialized: Boolean =
      validTo != NoPeriod

    final def isStableClass: Boolean = {
      def hasNoAbstractTypeMember(clazz: Symbol): Boolean = 
        (clazz hasFlag STABLE) || {
          var e = clazz.info.decls.elems
          while ((e ne null) && !(e.sym.isAbstractType && info.member(e.sym.name) == e.sym))
            e = e.next
          e == null
        }
      def checkStable() =
        (info.baseClasses forall hasNoAbstractTypeMember) && { setFlag(STABLE); true }
      isClass && (hasFlag(STABLE) || checkStable())
    }

    final def isCovariant: Boolean = isType && hasFlag(COVARIANT)

    final def isContravariant: Boolean = isType && hasFlag(CONTRAVARIANT)

    /** The variance of this symbol as an integer */
    final def variance: Int =
      if (isCovariant) 1
      else if (isContravariant) -1
      else 0

// Flags, owner, and name attributes --------------------------------------------------------------

    def owner: Symbol = rawowner
    final def owner_=(owner: Symbol) { rawowner = owner }

    def ownerChain: List[Symbol] = this :: owner.ownerChain

    def hasTransOwner(sym: Symbol) = {
      var o = this
      while ((o ne sym) && (o ne NoSymbol)) o = o.owner
      o eq sym
    }

    def name: Name = rawname
      
    final def name_=(name: Name) { 
      if (name != rawname) {
        if (owner.isClass) {
          var ifs = owner.infos
          while (ifs != null) {
            ifs.info.decls.rehash(this, name)
            ifs = ifs.prev
          }
        }
        rawname = name 
      }
    }

    /** If this symbol has an expanded name, its original name, otherwise its name itself.
     *  @see expandName
     */
    def originalName = nme.originalName(name)

    final def flags: Long = {
      val fs = rawflags & phase.flagMask
      (fs | ((fs & LateFlags) >>> LateShift)) & ~(fs >>> AntiShift)
    }
    final def flags_=(fs: Long) = rawflags = fs
    final def setFlag(mask: Long): this.type = { rawflags = rawflags | mask; this }
    final def resetFlag(mask: Long): this.type = { rawflags = rawflags & ~mask; this }
    final def getFlag(mask: Long): Long = flags & mask
    final def hasFlag(mask: Long): Boolean = (flags & mask) != 0
    final def resetFlags { rawflags = rawflags & TopLevelCreationFlags }

    /** The class or term up to which this symbol is accessible,
     *  or RootClass if it is public
     */
    def accessBoundary(base: Symbol): Symbol = {
      if (hasFlag(PRIVATE) || owner.isTerm) owner
      else if (privateWithin != NoSymbol && !phase.erasedTypes) privateWithin
      else if (hasFlag(PROTECTED)) base
      else RootClass
    }

    def isLessAccessibleThan(other: Symbol): Boolean = {
      val tb = this.accessBoundary(owner)
      val ob1 = other.accessBoundary(owner)
      val ob2 = ob1.linkedClassOfClass
      var o = tb
      while (o != NoSymbol && o != ob1 && o != ob2) {
        o = o.owner
      }
      o != NoSymbol && o != tb
    }

// Info and Type -------------------------------------------------------------------

    private[Symbols] var infos: TypeHistory = null

    /** Get type. The type of a symbol is:
     *  for a type symbol, the type corresponding to the symbol itself
     *  for a term symbol, its usual type
     */
    def tpe: Type = info

    /** Get type info associated with symbol at current phase, after
     *  ensuring that symbol is initialized (i.e. type is completed).
     */
    def info: Type = try {
      var cnt = 0 
      while (validTo == NoPeriod) {
        //if (settings.debug.value) System.out.println("completing " + this);//DEBUG
        if (inIDE && (infos eq null)) return ErrorType
        assert(infos ne null, this.name)
        assert(infos.prev eq null, this.name)
        val tp = infos.info
        //if (settings.debug.value) System.out.println("completing " + this.rawname + tp.getClass());//debug
        if ((rawflags & LOCKED) != 0) {
          setInfo(ErrorType)
          throw CyclicReference(this, tp)
        }
        rawflags = rawflags | LOCKED
        val current = phase
        try {
          phase = phaseOf(infos.validFrom)
          tp.complete(this)
          // if (settings.debug.value && runId(validTo) == currentRunId) System.out.println("completed " + this/* + ":" + info*/);//DEBUG
          rawflags = rawflags & ~LOCKED
        } finally {
          phase = current
        }
        cnt += 1
        // allow for two completions:
        //   one: sourceCompleter to LazyType, two: LazyType to completed type
        if (cnt == 3) throw new Error("no progress in completing " + this + ":" + tp)
      }
      val result = rawInfo
      result
    } catch {
      case ex: CyclicReference =>
        if (settings.debug.value) println("... trying to complete "+this)
        throw ex
    }

    /** Set initial info. */
    def setInfo(info: Type): this.type = {
      assert(info ne null)
      infos = TypeHistory(currentPeriod, info, null)
      if (info.isComplete) {
        rawflags = rawflags & ~LOCKED
        validTo = currentPeriod
      } else {
        rawflags = rawflags & ~LOCKED
        validTo = NoPeriod
      }
      this
    }

    /** Set new info valid from start of this phase. */
    final def updateInfo(info: Type): Symbol = {
      assert(phaseId(infos.validFrom) <= phase.id)
      if (phaseId(infos.validFrom) == phase.id) infos = infos.prev
      infos = TypeHistory(currentPeriod, info, infos)
      this
    }

    def hasRawInfo: Boolean = infos ne null

    /** Return info without checking for initialization or completing */
    def rawInfo: Type = {
      var infos = this.infos
      assert(infos != null, name)
      val curPeriod = currentPeriod
      val curPid = phaseId(curPeriod)

      if (!inIDE && validTo != NoPeriod) { // IDE doesn't adapt.

        // skip any infos that concern later phases
        while (curPid < phaseId(infos.validFrom) && infos.prev != null) 
          infos = infos.prev

        if (validTo < curPeriod) {
          // adapt any infos that come from previous runs
          val current = phase
          try {
            infos = adaptInfos(infos)

            //assert(runId(validTo) == currentRunId, name)
            //assert(runId(infos.validFrom) == currentRunId, name)

            if (validTo < curPeriod) {
              var itr = infoTransformers.nextFrom(phaseId(validTo))
              infoTransformers = itr; // caching optimization
              while (itr.pid != NoPhase.id && itr.pid < current.id) {
                phase = phaseWithId(itr.pid)
                val info1 = itr.transform(this, infos.info)
                if (info1 ne infos.info) {
                  infos = TypeHistory(currentPeriod + 1, info1, infos)
                  this.infos = infos
                }
                validTo = currentPeriod + 1 // to enable reads from same symbol during info-transform
                itr = itr.next
              }
              validTo = if (itr.pid == NoPhase.id) curPeriod 
                        else period(currentRunId, itr.pid)
            }
          } finally {
            phase = current
          }
        }
      }            
      infos.info
    }

    private def adaptInfos(infos: TypeHistory): TypeHistory =
      if (infos == null || runId(infos.validFrom) == currentRunId) {
        infos
      } else {
        val prev1 = adaptInfos(infos.prev)
        if (prev1 ne infos.prev) prev1
        else {
          def adaptToNewRun(info: Type): Type = 
            if (isPackageClass) info else adaptToNewRunMap(info)
          val pid = phaseId(infos.validFrom)
          validTo = period(currentRunId, pid)
          phase = phaseWithId(pid)
          val info1 = adaptToNewRun(infos.info)
          if (info1 eq infos.info) {
            infos.validFrom = validTo
            infos
          } else {
            this.infos = TypeHistory(validTo, info1, prev1)
            this.infos
          }
        }
      }

    /** Initialize the symbol */
    final def initialize: this.type = {
      if (!isInitialized) info
      this
    }

    /** Was symbol's type updated during given phase? */
    final def isUpdatedAt(pid: Phase#Id): Boolean = {
      var infos = this.infos
      while ((infos ne null) && phaseId(infos.validFrom) != pid + 1) infos = infos.prev
      infos ne null
    }

    /** The type constructor of a symbol is:
     *  For a type symbol, the type corresponding to the symbol itself,
     *  excluding parameters.
     *  Not applicable for term symbols.
     */
    def typeConstructor: Type =
      throw new Error("typeConstructor inapplicable for " + this)

    def tpeHK = if (isType) typeConstructor else tpe // @M! used in memberType

    /** The type parameters of this symbol, without ensuring type completion.
     *  assumption: if a type starts out as monomorphic, it will not acquire 
     *  type parameters later.
     */
    def unsafeTypeParams: List[Symbol] = 
      if (isMonomorphicType) List() else rawInfo.typeParams 

    /** The type parameters of this symbol.
     *  assumption: if a type starts out as monomorphic, it will not acquire 
     *  type parameters later.
     */
    def typeParams: List[Symbol] =
      if (isMonomorphicType) List() else { rawInfo.load(this); rawInfo.typeParams }

    def getAttributes(clazz: Symbol): List[AnnotationInfo] =
      attributes.filter(_.atp.typeSymbol.isNonBottomSubClass(clazz))

    /** The least proper supertype of a class; includes all parent types
     *  and refinement where needed 
     */
    def classBound: Type = {
      val tp = refinedType(info.parents, owner)
      val thistp = tp.typeSymbol.thisType
      val oldsymbuf = new ListBuffer[Symbol]
      val newsymbuf = new ListBuffer[Symbol]
      for (sym <- info.decls.toList) {
        // todo: what about public references to private symbols?
        if (sym.isPublic && !sym.isConstructor) {
          oldsymbuf += sym
          newsymbuf += ( 
            if (sym.isClass)
              tp.typeSymbol.newAbstractType(sym.pos, sym.name).setInfo(sym.existentialBound)
            else 
              sym.cloneSymbol(tp.typeSymbol))
        }
      }
      val oldsyms = oldsymbuf.toList
      val newsyms = newsymbuf.toList
      for (sym <- newsyms) {
        addMember(thistp, tp, sym.setInfo(sym.info.substThis(this, thistp).substSym(oldsyms, newsyms)))
      }
      tp
    }

    /** If we quantify existentially over this symbol, 
     *  the bound of the type variable that stands for it 
     *  pre: symbol is a term, a class, or an abstract type (no alias type allowed)
     */
    def existentialBound: Type = 
      if (this.isClass) 
         polyType(this.typeParams, mkTypeBounds(NothingClass.tpe, this.classBound))
      else if (this.isAbstractType) 
         this.info
      else if (this.isTerm) 
         mkTypeBounds(NothingClass.tpe, intersectionType(List(this.tpe, SingletonClass.tpe)))
      else 
        throw new Error("unexpected alias type: "+this)

    /** Reset symbol to initial state
     */
    def reset(completer: Type) {
      resetFlags
      infos = null
      validTo = NoPeriod
      //limit = NoPhase.id
      setInfo(completer)
    }

// Comparisons ----------------------------------------------------------------

    /** A total ordering between symbols that refines the class
     *  inheritance graph (i.e. subclass.isLess(superclass) always holds).
     *  the ordering is given by: (_.isType, -_.baseTypeSeq.length) for type symbols, followed by `id'.
     */
    final def isLess(that: Symbol): Boolean = {
      def baseTypeSeqLength(sym: Symbol) =
        if (sym.isAbstractType) 1 + sym.info.bounds.hi.baseTypeSeq.length
        else sym.info.baseTypeSeq.length
      if (this.isType)
        (that.isType &&
         { val diff = baseTypeSeqLength(this) - baseTypeSeqLength(that)
           diff > 0 || diff == 0 && this.id < that.id })
      else
        that.isType || this.id < that.id
    }

    /** A partial ordering between symbols.
     *  (this isNestedIn that) holds iff this symbol is defined within
     *  a class or method defining that symbol
     */
    final def isNestedIn(that: Symbol): Boolean =
      owner == that || owner != NoSymbol && (owner isNestedIn that)

    /** Is this class symbol a subclass of that symbol? */
    final def isNonBottomSubClass(that: Symbol): Boolean =
      this == that || this.isError || that.isError ||
      info.baseTypeIndex(that) >= 0

    final def isSubClass(that: Symbol): Boolean = {
      isNonBottomSubClass(that) ||
      this == NothingClass ||
      this == NullClass &&
      (that == AnyClass ||
       that != NothingClass && (that isSubClass AnyRefClass))
    }

// Overloaded Alternatives ---------------------------------------------------------

    def alternatives: List[Symbol] =
      if (hasFlag(OVERLOADED)) info.asInstanceOf[OverloadedType].alternatives
      else List(this)

    def filter(cond: Symbol => Boolean): Symbol =
      if (hasFlag(OVERLOADED)) {
        //assert(info.isInstanceOf[OverloadedType], "" + this + ":" + info);//DEBUG
        val alts = alternatives
        val alts1 = alts filter cond
        if (alts1 eq alts) this
        else if (alts1.isEmpty) NoSymbol
        else if (alts1.tail.isEmpty) alts1.head
        else owner.newOverloaded(info.prefix, alts1)
      } else if (cond(this)) this
      else NoSymbol

    def suchThat(cond: Symbol => Boolean): Symbol = {
      val result = filter(cond)
      // @S: seems like NoSymbol has the overloaded flag????
      if (inIDE && (this eq result) && result != NoSymbol && (result hasFlag OVERLOADED)) {
        return result         
      }
      if (!inIDE) assert(!(result hasFlag OVERLOADED), result.alternatives)
      result
    }

// Cloneing -------------------------------------------------------------------

    /** A clone of this symbol */
    final def cloneSymbol: Symbol =
      cloneSymbol(owner)

    /** A clone of this symbol, but with given owner */
    final def cloneSymbol(owner: Symbol): Symbol =
      cloneSymbolImpl(owner).setInfo(info.cloneInfo(this))
        .setFlag(this.rawflags).setAttributes(this.attributes)

    /** Internal method to clone a symbol's implementation without flags or type
     */
    def cloneSymbolImpl(owner: Symbol): Symbol

// Access to related symbols --------------------------------------------------

    /** The next enclosing class */
    def enclClass: Symbol = if (isClass) this else owner.enclClass

    /** The next enclosing method */
    def enclMethod: Symbol = if (isSourceMethod) this else owner.enclMethod

    /** The primary constructor of a class */
    def primaryConstructor: Symbol = {
      var c = info.decl(
        if (isTrait || isImplClass) nme.MIXIN_CONSTRUCTOR
        else nme.CONSTRUCTOR)
      c = if (c hasFlag OVERLOADED) c.alternatives.head else c
      //assert(c != NoSymbol)
      c
    }

    /** The self symbol of a class with explicit self type, or else the
     *  symbol itself.
     */
    def thisSym: Symbol = this

    /** The type of `this' in a class, or else the type of the symbol itself. */
    def typeOfThis = thisSym.tpe

    /** Sets the type of `this' in a class */
    def typeOfThis_=(tp: Type): Unit =
      throw new Error("typeOfThis cannot be set for " + this)

    /** If symbol is a class, the type <code>this.type</code> in this class,
     * otherwise <code>NoPrefix</code>.
     */
    def thisType: Type = NoPrefix

    /** Return every accessor of a primary constructor parameter in this case class
      */
    final def caseFieldAccessors: List[Symbol] =
      info.decls.toList filter (sym => !(sym hasFlag PRIVATE) && sym.hasFlag(CASEACCESSOR))

    final def constrParamAccessors: List[Symbol] =
      info.decls.toList filter (sym => !sym.isMethod && sym.hasFlag(PARAMACCESSOR))

    /** The symbol accessed by this accessor function.
     */
    final def accessed: Symbol = {
      assert(hasFlag(ACCESSOR))
      owner.info.decl(nme.getterToLocal(if (isSetter) nme.setterToGetter(name) else name))
    }

    /** The implementation class of a trait */
    final def implClass: Symbol = owner.info.decl(nme.implClassName(name))

    /** The class that is logically an outer class of given `clazz'.
     *  This is the enclosing class, except for classes defined locally to constructors,
     *  where it is the outer class of the enclosing class
     */
    final def outerClass: Symbol = 
      if (owner.isClass) owner
      else if (isClassLocalToConstructor) owner.enclClass.outerClass
      else owner.outerClass

    /** For a paramaccessor: a superclass paramaccessor for which this symbol
     *  is an alias, NoSymbol for all others */
    def alias: Symbol = NoSymbol

    /** For a lazy value, it's lazy accessor. NoSymbol for all others */
    def lazyAccessor: Symbol = NoSymbol

    /** For an outer accessor: The class from which the outer originates.
     *  For all other symbols: NoSymbol
     */
    def outerSource: Symbol = NoSymbol

    /** The superclass of this class */
    def superClass: Symbol = if (info.parents.isEmpty) NoSymbol else info.parents.head.typeSymbol

    /** The directly or indirectly inherited mixins of this class
     *  except for mixin classes inherited by the superclass. Mixin classes appear
     *  in linearlization order.
     */
    def mixinClasses: List[Symbol] = {
      val sc = superClass
      info.baseClasses.tail.takeWhile(sc ne)
    }

    /** The package containing this symbol, or NoSymbol if there
     *  is not one. */
    def enclosingPackage: Symbol =
      if (this == NoSymbol) this else {
        var packSym = this.owner
        while ((packSym != NoSymbol)
               && !packSym.isPackageClass) 
          packSym = packSym.owner
        if (packSym != NoSymbol)
          packSym = packSym.linkedModuleOfClass
        packSym
      }

    /** The top-level class containing this symbol */
    def toplevelClass: Symbol =
      if (owner.isPackageClass) {
        if (isClass) this else moduleClass 
      } else owner.toplevelClass

    /** Is this symbol defined in the same scope and compilation unit as `that' symbol?
     */
    def isCoDefinedWith(that: Symbol) = 
      (this.rawInfo ne NoType) && {
        val res = 
          !this.owner.isPackageClass || 
          (this.sourceFile eq null) ||
          (that.sourceFile eq null) ||
          (this.sourceFile eq that.sourceFile) ||
          (this.sourceFile == that.sourceFile)
        res
      }

    /** The class with the same name in the same package as this module or
     *  case class factory
     */
    final def linkedClassOfModule: Symbol = {
      if (this != NoSymbol)
        owner.info.decl(name.toTypeName).suchThat(_ isCoDefinedWith this)
      else NoSymbol
    }

    /** The module or case class factory with the same name in the same
     *  package as this class.
     */
    final def linkedModuleOfClass: Symbol =
      if (this.isClass && !this.isAnonymousClass && !this.isRefinementClass) {
        owner.rawInfo.decl(name.toTermName).suchThat(
          sym => (sym hasFlag MODULE) && (sym isCoDefinedWith this))
      } else NoSymbol

    /** For a module its linked class, for a class its linked module or case
     *  factory otherwise.
     */
    final def linkedSym: Symbol =
      if (isTerm) linkedClassOfModule
      else if (isClass) owner.info.decl(name.toTermName).suchThat(_ isCoDefinedWith this)
      else NoSymbol

    /** For a module class its linked class, for a plain class
     *  the module class of its linked module.
     */
    final def linkedClassOfClass: Symbol =
      if (isModuleClass) linkedClassOfModule else linkedModuleOfClass.moduleClass

    /** If this symbol is an implementation class, its interface, otherwise the symbol itself
     *  The method follows two strategies to determine the interface.
     *   - during or after erasure, it takes the last parent of the implementatation class
     *     (which is always the interface, by convention)
     *   - before erasure, it looks up the interface name in the scope of the owner of the class.
     *     This only works for implementation classes owned by other classes or traits.
     */
    final def toInterface: Symbol =
      if (isImplClass) {
        val result =
          if (phase.next.erasedTypes) {
            assert(!tpe.parents.isEmpty, this)
            tpe.parents.last.typeSymbol
          } else {
            owner.info.decl(nme.interfaceName(name))
          }
        assert(result != NoSymbol, this)
        result
      } else this

    /** The module corresponding to this module class (note that this
     *  is not updated when a module is cloned).
     */
    def sourceModule: Symbol = NoSymbol

    /** The module class corresponding to this module.
     */
    def moduleClass: Symbol = NoSymbol

    /** The non-private symbol whose type matches the type of this symbol
     *  in in given class.
     *
     *  @param ofclazz   The class containing the symbol's definition
     *  @param site      The base type from which member types are computed
     */
    final def matchingSymbol(ofclazz: Symbol, site: Type): Symbol =
      ofclazz.info.nonPrivateDecl(name).filter(sym =>
        !sym.isTerm || (site.memberType(this) matches site.memberType(sym)))

    /** The non-private member of `site' whose type and name match the type of this symbol
     */
    final def matchingSymbol(site: Type): Symbol =
      site.nonPrivateMember(name).filter(sym =>
        !sym.isTerm || (site.memberType(this) matches site.memberType(sym)))

    /** The symbol overridden by this symbol in given class `ofclazz' */
    final def overriddenSymbol(ofclazz: Symbol): Symbol =
      if (isClassConstructor) NoSymbol else matchingSymbol(ofclazz, owner.thisType)

    /** The symbol overriding this symbol in given subclass `ofclazz' */
    final def overridingSymbol(ofclazz: Symbol): Symbol =
      if (isClassConstructor) NoSymbol else matchingSymbol(ofclazz, ofclazz.thisType)

    final def allOverriddenSymbols: List[Symbol] =
      if (owner.isClass && !owner.info.baseClasses.isEmpty)
        for { bc <- owner.info.baseClasses.tail
              val s = overriddenSymbol(bc)
              if s != NoSymbol } yield s
      else List()

    /** The virtual classes overridden by this virtual class (excluding `clazz' itself)
     *  Classes appear in linearization order (with superclasses before subclasses)
     */
    final def overriddenVirtuals: List[Symbol] = 
      if (isVirtualTrait && hasFlag(OVERRIDE))
        this.owner.info.baseClasses.tail 
          .map(_.info.decl(name)) 
          .filter(_.isVirtualTrait)
          .reverse
      else List()

    /** The symbol accessed by a super in the definition of this symbol when
     *  seen from class `base'. This symbol is always concrete.
     *  pre: `this.owner' is in the base class sequence of `base'.
     */
    final def superSymbol(base: Symbol): Symbol = {
      var bcs = base.info.baseClasses.dropWhile(owner !=).tail
      var sym: Symbol = NoSymbol
      while (!bcs.isEmpty && sym == NoSymbol) {
        if (!bcs.head.isImplClass)
          sym = matchingSymbol(bcs.head, base.thisType).suchThat(!_.isDeferred)
        bcs = bcs.tail
      }
      sym
    }

    /** The getter of this value or setter definition in class `base', or NoSymbol if
     *  none exists.
     */
    final def getter(base: Symbol): Symbol = {
      val getterName = if (isSetter) nme.setterToGetter(name) else nme.getterName(name)
      base.info.decl(getterName) filter (_.hasFlag(ACCESSOR))
    }

    /** The setter of this value or getter definition, or NoSymbol if none exists */
    final def setter(base: Symbol): Symbol = setter(base, false)

    final def setter(base: Symbol, hasExpandedName: Boolean): Symbol = {
      var sname = nme.getterToSetter(nme.getterName(name))
      if (hasExpandedName) sname = base.expandedSetterName(sname)
      base.info.decl(sname) filter (_.hasFlag(ACCESSOR))
    }

    /** The case module corresponding to this case class
     *  @pre case class is a member of some other class or package
     */
    final def caseModule: Symbol = {
      var modname = name.toTermName
      if (privateWithin.isClass && !privateWithin.isModuleClass && !hasFlag(EXPANDEDNAME))
        modname = privateWithin.expandedName(modname)
      initialize.owner.info.decl(modname).suchThat(_.isModule)
    }

    /** If this symbol is a type parameter skolem (not an existential skolem!)
     *  its corresponding type parameter, otherwise this */
    def deSkolemize: Symbol = this

    /** If this symbol is an existential skolem the location (a Tree or null) 
     *  where it was unpacked. Resulttype is AnyRef because trees are not visible here. */
    def unpackLocation: AnyRef = null

    /** Remove private modifier from symbol `sym's definition. If `sym' is a
     *  term symbol rename it by expanding its name to avoid name clashes
     */
    final def makeNotPrivate(base: Symbol) {
      if (this hasFlag PRIVATE) {
        setFlag(notPRIVATE)
        if (isTerm && !isDeferred) setFlag(lateFINAL)
        if (!isStaticModule && !isClassConstructor) {
          expandName(base)
          if (isModule) moduleClass.makeNotPrivate(base)
        }
      }
    }

    /** change name by appending $$<fully-qualified-name-of-class `base'>
     *  Do the same for any accessed symbols or setters/getters
     */
    def expandName(base: Symbol) {
      if (this.isTerm && this != NoSymbol && !hasFlag(EXPANDEDNAME)) {
        setFlag(EXPANDEDNAME)
        if (hasFlag(ACCESSOR) && !isDeferred) {
          accessed.expandName(base)
        } else if (hasGetter) {
          getter(owner).expandName(base)
          setter(owner).expandName(base)
        }
        name = base.expandedName(name)
        if (isType) name = name.toTypeName
      }
    }

    def expandedSetterName(simpleSetterName: Name): Name =
      newTermName(fullNameString('$') + nme.TRAIT_SETTER_SEPARATOR_STRING + simpleSetterName)

    /** The expanded name of `name' relative to this class as base
     */
    def expandedName(name: Name): Name = {
        newTermName(fullNameString('$') + nme.EXPAND_SEPARATOR_STRING + name)
    }

    def sourceFile: AbstractFile = {
      var ret = (if (isModule) moduleClass else toplevelClass).sourceFile
      if (ret == null && inIDE && !isModule) this match {
        case sym : ModuleSymbol if sym.referenced != null =>
          ret = sym.referenced.sourceFile
        case _ => 
      }
      ret
    }

    def sourceFile_=(f: AbstractFile) {
      throw new Error("sourceFile_= inapplicable for " + this)
    }

    def isFromClassFile: Boolean =
      (if (isModule) moduleClass else toplevelClass).isFromClassFile

    /** If this is a sealed class, its known direct subclasses. Otherwise Set.empty */
    def children: Set[Symbol] = emptySymbolSet

    /** Declare given subclass `sym' of this sealed class */
    def addChild(sym: Symbol) {
      throw new Error("addChild inapplicable for " + this)
    }
      

// ToString -------------------------------------------------------------------

    /** A tag which (in the ideal case) uniquely identifies class symbols */
    final def tag: Int = fullNameString.hashCode()

    /** The simple name of this Symbol */
    final def simpleName: Name = name

    /** String representation of symbol's definition key word */
    final def keyString: String =
      if (isTrait && hasFlag(JAVA)) "interface"
      else if (isTrait) "trait"
      else if (isClass) "class"
      else if (isType && !hasFlag(PARAM)) "type"
      else if (isVariable) "var"
      else if (isPackage) "package"
      else if (isModule) "object"
      else if (isMethod) "def"
      else if (isTerm && (!hasFlag(PARAM) || hasFlag(PARAMACCESSOR))) "val"
      else ""

    /** String representation of symbol's kind */
    final def kindString: String =
      if (isPackageClass)
        if (settings.debug.value) "package class" else "package"
      else if (isModuleClass) 
        if (settings.debug.value) "singleton class" else "object"
      else if (isAnonymousClass) "template"
      else if (isRefinementClass) ""
      else if (isTrait) "trait"
      else if (isClass) "class"
      else if (isType) "type"
      else if (isTerm && hasFlag(LAZY)) "lazy value"
      else if (isVariable) "variable"
      else if (isPackage) "package"
      else if (isModule) "object"
      else if (isClassConstructor) "constructor"
      else if (isSourceMethod) "method"
      else if (isTerm) "value"
      else ""

    /** String representation of symbol's simple name.
     *  If !settings.debug translates expansions of operators back to operator symbol.
     *  E.g. $eq => =.
     *  If settings.uniquId adds id.
     */
    def nameString: String = {
      var s = simpleName.decode
      if (s endsWith nme.LOCAL_SUFFIX) 
        s = s.substring(0, s.length - nme.LOCAL_SUFFIX.length)
      s + idString
    }

    /** String representation of symbol's full name with <code>separator</code>
     *  between class names.
     *  Never translates expansions of operators back to operator symbol.
     *  Never adds id.
     */
    final def fullNameString(separator: Char): String = {
      if (this == NoSymbol) return "<NoSymbol>"
      assert(owner != NoSymbol, this)
      var str =
        if (owner.isRoot || 
            owner.isEmptyPackageClass || 
            owner.isInterpreterWrapper) simpleName.toString
        else owner.enclClass.fullNameString(separator) + separator + simpleName
      if (str.charAt(str.length - 1) == ' ') str = str.substring(0, str.length - 1)
      str
    }

    final def fullNameString: String = fullNameString('.')

    /** If settings.uniqid is set, the symbol's id, else "" */
    final def idString: String =
      if (settings.uniqid.value) "#"+id else ""

    /** String representation, including symbol's kind
     *  e.g., "class Foo", "method Bar".
     */
    override def toString(): String =
      if (isValueParameter && owner.isSetter)
        "parameter of setter "+owner.nameString
      else 
        compose(List(kindString, 
                     if (isClassConstructor) owner.simpleName.decode+idString else nameString))

    /** String representation of location. */
    def locationString: String =
      if (owner.isClass &&
          ((!owner.isAnonymousClass && 
            !owner.isRefinementClass && 
            !owner.isInterpreterWrapper &&
            !owner.isRoot &&
            !owner.isEmptyPackageClass) || settings.debug.value))
        " in " + owner else ""

    /** String representation of symbol's definition following its name */
    final def infoString(tp: Type): String = {
      def typeParamsString: String = tp match {
        case PolyType(tparams, _) if (tparams.length != 0) =>
          (tparams map (_.defString)).mkString("[", ",", "]")
        case _ =>
          ""
      }
      if (isClass)
        typeParamsString + " extends " + tp.resultType
      else if (isAliasType)
        typeParamsString + " = " + tp.resultType
      else if (isAbstractType)
        typeParamsString + {
          tp.resultType match {
            case TypeBounds(lo, hi) =>
              (if (lo.typeSymbol == NothingClass) "" else " >: " + lo) +
              (if (hi.typeSymbol == AnyClass) "" else " <: " + hi)
            case rtp =>
              "<: " + rtp
          }
        }
      else if (isModule)
        moduleClass.infoString(tp)
      else
        tp match {
          case PolyType(tparams, res) =>
            typeParamsString + infoString(res)
          case MethodType(pts, res) =>
            pts.mkString("(", ",", ")") + infoString(res)
          case _ =>
            ": " + tp
        }
    }

    def infosString = infos.toString()

    /** String representation of symbol's variance */
    def varianceString: String =
      if (variance == 1) "+"
      else if (variance == -1) "-"
      else ""

    /** String representation of symbol's definition */
    def defString: String = {
      val f = if (settings.debug.value) flags 
              else if (owner.isRefinementClass) flags & ExplicitFlags & ~OVERRIDE
              else flags & ExplicitFlags
      compose(List(flagsToString(f), keyString, varianceString + nameString + 
                   (if (hasRawInfo) infoString(rawInfo) else "<_>")))
    }

    /** Concatenate strings separated by spaces */
    private def compose(ss: List[String]): String =
      ss.filter("" !=).mkString("", " ", "")

    def isSingletonExistential: Boolean =
      (name endsWith nme.dottype) && (info.bounds.hi.typeSymbol isSubClass SingletonClass) 

    /** String representation of existentially bound variable */
    def existentialToString = 
      if (isSingletonExistential && !settings.debug.value)
        "val "+name.subName(0, name.length - nme.dottype.length)+": "+
        dropSingletonType(info.bounds.hi)
      else defString
  }

  /** A class for term symbols */
  class TermSymbol(initOwner: Symbol, initPos: Position, initName: Name)
  extends Symbol(initOwner, initPos, initName) {
    override def isTerm = true

/*
    val marker = initName.toString match {
      case "forCLDC" => new MarkForCLDC
      case _ => null
    }
*/
    privateWithin = NoSymbol

    protected var referenced: Symbol = NoSymbol

    def cloneSymbolImpl(owner: Symbol): Symbol = {
      val clone = new TermSymbol(owner, pos, name)
      clone.referenced = referenced
      clone
    }

    override def alias: Symbol =
      if (hasFlag(SUPERACCESSOR | PARAMACCESSOR | MIXEDIN)) initialize.referenced
      else NoSymbol

    def setAlias(alias: Symbol): TermSymbol = {
      assert(alias != NoSymbol, this)
      assert(!(alias hasFlag OVERLOADED), alias)

      assert(hasFlag(SUPERACCESSOR | PARAMACCESSOR | MIXEDIN), this)
      referenced = alias
      this
    }

    override def outerSource: Symbol = 
      if (name endsWith nme.OUTER) initialize.referenced
      else NoSymbol

    override def moduleClass: Symbol =
      if (hasFlag(MODULE)) referenced else NoSymbol

    def setModuleClass(clazz: Symbol): TermSymbol = {
      assert(hasFlag(MODULE))
      referenced = clazz
      this
    }
    
    def setLazyAccessor(sym: Symbol): TermSymbol = {
      // @S: in IDE setLazyAccessor can be called multiple times on same sym
      if (inIDE && referenced != NoSymbol && referenced != sym) {
        // do nothing
        recycle(referenced)
      } else {
        assert(hasFlag(LAZY) && (referenced == NoSymbol || referenced == sym), this)
        referenced = sym
      }
      this
    }
    
    override def lazyAccessor: Symbol = {
      assert(hasFlag(LAZY), this)
      referenced
    }
  }

  /** A class for module symbols */
  class ModuleSymbol(initOwner: Symbol, initPos: Position, initName: Name)
  extends TermSymbol(initOwner, initPos, initName) {

    private var flatname = nme.EMPTY

    override def owner: Symbol =
      if (phase.flatClasses && !hasFlag(METHOD) &&
          rawowner != NoSymbol && !rawowner.isPackageClass) rawowner.owner
      else rawowner

    override def name: Name =
      if (phase.flatClasses && !hasFlag(METHOD) &&
          rawowner != NoSymbol && !rawowner.isPackageClass) {
        if (flatname == nme.EMPTY) {
          assert(rawowner.isClass)
          flatname = newTermName(compactify(rawowner.name.toString() + "$" + rawname))
        }
        flatname
      } else rawname

    override def cloneSymbolImpl(owner: Symbol): Symbol = {
      val clone = new ModuleSymbol(owner, pos, name)
      clone.referenced = referenced
      clone
    }
  }

  /** A class of type symbols. Alias and abstract types are direct instances
   *  of this class. Classes are instances of a subclass.
   */
  class TypeSymbol(initOwner: Symbol, initPos: Position, initName: Name)
  extends Symbol(initOwner, initPos, initName) {
    privateWithin = NoSymbol
    private var tyconCache: Type = null
    private var tyconRunId = NoRunId
    private var tpeCache: Type = _
    private var tpePeriod = NoPeriod

    override def isType = true
    override def isTypeMember = true
    override def isAbstractType = isDeferred
    override def isAliasType = !isDeferred

    override def tpe: Type = {
      if (tpeCache eq NoType) throw CyclicReference(this, typeConstructor)
      if (tpePeriod != currentPeriod) {
        if (isValid(tpePeriod)) {
          tpePeriod = currentPeriod
        } else {
          if (isInitialized) tpePeriod = currentPeriod
          tpeCache = NoType
          val targs = 
            if (phase.erasedTypes && this != ArrayClass) List()
            else unsafeTypeParams map (_.typeConstructor) //@M! use typeConstructor to generate dummy type arguments,
            // sym.tpe should not be called on a symbol that's supposed to be a higher-kinded type
            // memberType should be used instead, that's why it uses tpeHK and not tpe
          tpeCache = typeRef(if (hasFlag(PARAM | EXISTENTIAL)) NoPrefix else owner.thisType, 
                             this, targs)
        }
      }
      assert(tpeCache ne null/*, "" + this + " " + phase*/)//debug
      tpeCache
    }

    override def typeConstructor: Type = {
      if ((tyconCache eq null) || tyconRunId != currentRunId) {
        tyconCache = typeRef(if (hasFlag(PARAM | EXISTENTIAL)) NoPrefix else owner.thisType, 
                             this, List())
        tyconRunId = currentRunId
      }
      assert(tyconCache ne null)
      tyconCache
    }

    override def setInfo(tp: Type): this.type = {
      tpePeriod = NoPeriod
      tyconCache = null
      if (tp.isComplete)
        tp match {
          case PolyType(_, _) => resetFlag(MONOMORPHIC)
          case NoType | AnnotatedType(_, _, _) => ;
          case _ => setFlag(MONOMORPHIC)
        }
      super.setInfo(tp)
      this
    }

    override def reset(completer: Type) {
      super.reset(completer)
      tpePeriod = NoPeriod
      tyconRunId = NoRunId
    }

    def cloneSymbolImpl(owner: Symbol): Symbol =
      new TypeSymbol(owner, pos, name)

    if (util.Statistics.enabled) typeSymbolCount = typeSymbolCount + 1
  }

  /** A class for type parameters viewed from inside their scopes */
  class TypeSkolem(initOwner: Symbol, initPos: Position,
                   initName: Name, origin: AnyRef)
  extends TypeSymbol(initOwner, initPos, initName) {

    /** The skolemization level in place when the skolem was constructed */
    val level = skolemizationLevel

    override def isSkolem = true
    override def deSkolemize = origin match {
      case s: Symbol => s
      case _ => this
    }
    override def unpackLocation = origin
    override def typeParams = info.typeParams //@M! (not deSkolemize.typeParams!!), also can't leave superclass definition: use info, not rawInfo
    override def cloneSymbolImpl(owner: Symbol): Symbol = {
      throw new Error("should not clone a type skolem")
    }
    override def nameString: String = 
      if (settings.debug.value) (super.nameString + "&" + level)
      else super.nameString
  }


  /** A class for class symbols */
  class ClassSymbol(initOwner: Symbol, initPos: Position, initName: Name)
  extends TypeSymbol(initOwner, initPos, initName) {

    /** The classfile from which this class was loaded. Maybe null. */
    var classFile: AbstractFile = null;
    
    private var source: AbstractFile = null
    override def sourceFile =
      if (owner.isPackageClass) source else super.sourceFile
    override def sourceFile_=(f: AbstractFile) {
      //System.err.println("set source file of " + this + ": " + f);
      attachSource(this, f)
      source = f
    }
    override def isFromClassFile = {
      if (classFile ne null) true
      else if (owner.isPackageClass) false
      else super.isFromClassFile
    }
    private var thissym: Symbol = this

    override def isClass: Boolean = true
    override def isTypeMember = false
    override def isAbstractType = false
    override def isAliasType = false

    override def reset(completer: Type) {
      super.reset(completer)
      thissym = this
    }

    private var flatname = nme.EMPTY

    override def owner: Symbol =
      if (phase.flatClasses && rawowner != NoSymbol && !rawowner.isPackageClass) rawowner.owner
      else rawowner

    override def name: Name =
      if ((rawflags & notDEFERRED) != 0 && phase.devirtualized && !phase.erasedTypes) {
        newTypeName(rawname+"$trait") // (part of DEVIRTUALIZE)
      } else if (phase.flatClasses && rawowner != NoSymbol && !rawowner.isPackageClass) {
        if (flatname == nme.EMPTY) {
          assert(rawowner.isClass)
          flatname = newTypeName(compactify(rawowner.name.toString() + "$" + rawname))
        }
        flatname
      } else rawname

    private var thisTypeCache: Type = _
    private var thisTypePeriod = NoPeriod

    /** the type this.type in this class */
    override def thisType: Type = {
      val period = thisTypePeriod
      if (period != currentPeriod) {
        thisTypePeriod = currentPeriod
        if (!isValid(period)) thisTypeCache = mkThisType(this)
      }
      thisTypeCache
    }

    /** A symbol carrying the self type of the class as its type */
    override def thisSym: Symbol = thissym

    override def typeOfThis: Type =
      if (getFlag(MODULE | IMPLCLASS) == MODULE && owner != NoSymbol)
        singleType(owner.thisType, sourceModule)
      else thissym.tpe

    /** Sets the self type of the class */
    override def typeOfThis_=(tp: Type) {
      thissym = newThisSym(pos).setInfo(tp)
    }

    override def cloneSymbolImpl(owner: Symbol): Symbol = {
      assert(!isModuleClass)
      val clone = new ClassSymbol(owner, pos, name)
      if (thisSym != this) {
        clone.typeOfThis = typeOfThis
        clone.thisSym.name = thisSym.name
      }
      clone
    }

    override def sourceModule =
      if (isModuleClass) linkedModuleOfClass else NoSymbol

    private var childSet: Set[Symbol] = emptySymbolSet
    override def children: Set[Symbol] = childSet
    override def addChild(sym: Symbol) { childSet = childSet + sym }

    if (util.Statistics.enabled) classSymbolCount = classSymbolCount + 1
  }

  /** A class for module class symbols
   *  Note: Not all module classes are of this type; when unpickled, we get
   *  plain class symbols!
   */
  class ModuleClassSymbol(owner: Symbol, pos: Position, name: Name)
  extends ClassSymbol(owner, pos, name) {
    private var module: Symbol = null
    def this(module: TermSymbol) = {
      this(module.owner, module.pos, module.name.toTypeName)
      setFlag(module.getFlag(ModuleToClassFlags) | MODULE | FINAL)
      setSourceModule(module)
    }
    override def sourceModule = module
    def setSourceModule(module: Symbol) { this.module = module }
  }

  /** An object repreesenting a missing symbol */
  object NoSymbol extends Symbol(null, NoPosition, nme.NOSYMBOL) {
    setInfo(NoType)
    privateWithin = this
    override def setInfo(info: Type): this.type = {
      infos = TypeHistory(1, NoType, null)
      rawflags = rawflags & ~ LOCKED
      validTo = currentPeriod
      this
    }
    override def defString: String = toString
    override def locationString: String = ""
    override def enclClass: Symbol = this
    override def toplevelClass: Symbol = this
    override def enclMethod: Symbol = this
    override def owner: Symbol = throw new Error("no-symbol does not have owner")
    override def sourceFile: AbstractFile = null
    override def ownerChain: List[Symbol] = List()
    override def alternatives: List[Symbol] = List()
    override def reset(completer: Type) {}
    override def info: Type = NoType
    override def rawInfo: Type = NoType
    override def accessBoundary(base: Symbol): Symbol = RootClass
    def cloneSymbolImpl(owner: Symbol): Symbol = throw new Error()
  }

  def cloneSymbols(syms: List[Symbol]): List[Symbol] = {
    val syms1 = syms map (_.cloneSymbol)
    for (sym1 <- syms1) sym1.setInfo(sym1.info.substSym(syms, syms1))
    syms1
  }

  def cloneSymbols(syms: List[Symbol], owner: Symbol): List[Symbol] = {
    val syms1 = syms map (_.cloneSymbol(owner))
    for (sym1 <- syms1) sym1.setInfo(sym1.info.substSym(syms, syms1))
    syms1
  }

  /** An exception for cyclic references of symbol definitions */
  case class CyclicReference(sym: Symbol, info: Type)
  extends TypeError("illegal cyclic reference involving " + sym)

  /** A class for type histories */
  private sealed case class TypeHistory(var validFrom: Period, info: Type, prev: TypeHistory) {
    assert((prev eq null) || phaseId(validFrom) > phaseId(prev.validFrom), this)
    assert(validFrom != NoPeriod)
    override def toString() =
      "TypeHistory(" + phaseOf(validFrom)+":"+runId(validFrom) + "," + info + "," + prev + ")"
  }
/*
  var occs = 0

class MarkForCLDC {
  val atRun: Int = currentRunId
  occs += 1
  println("new "+getClass+" at "+atRun+" ("+occs+" total)")
  override def finalize() {
    occs -=1 
    println("drop "+getClass+" from "+atRun+" ("+occs+" total)")
  }
}
*/
}
