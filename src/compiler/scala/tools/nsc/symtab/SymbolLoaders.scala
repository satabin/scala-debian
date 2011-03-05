/* NSC -- new Scala compiler
 * Copyright 2005-2010 LAMP/EPFL
 * @author  Martin Odersky
 */

package scala.tools.nsc
package symtab
 
import java.io.{File, IOException} 
 
import ch.epfl.lamp.compiler.msil.{Type => MSILType, Attribute => MSILAttribute}

import scala.collection.mutable.{HashMap, HashSet, ListBuffer}
import scala.compat.Platform.currentTime
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.{ ClassPath, JavaClassPath }
import classfile.ClassfileParser
import Flags._

import util.Statistics._

/** This class ...
 *
 *  @author  Martin Odersky
 *  @version 1.0
 */
abstract class SymbolLoaders {
  val global: Global
  import global._

  /**
   * A lazy type that completes itself by calling parameter doComplete.
   * Any linked modules/classes or module classes are also initialized.
   */
  abstract class SymbolLoader extends LazyType {

    /** Load source or class file for `root', return */
    protected def doComplete(root: Symbol): Unit
    
    protected def sourcefile: Option[AbstractFile] = None

    /**
     * Description of the resource (ClassPath, AbstractFile, MSILType)
     * being processed by this loader
     */
    protected def description: String

    private var ok = false

    private def setSource(sym: Symbol) {
      sourcefile map (sf => sym match {
        case cls: ClassSymbol => cls.sourceFile = sf
        case mod: ModuleSymbol => mod.moduleClass.sourceFile = sf
        case _ => ()
      })
    }
    override def complete(root: Symbol) : Unit = {
      try {
        val start = currentTime
        val currentphase = phase
        doComplete(root)
        phase = currentphase
        informTime("loaded " + description, start)
        ok = true
        setSource(root)
        setSource(root.companionSymbol) // module -> class, class -> module
      } catch {
        case ex: IOException =>
          ok = false
          if (settings.debug.value) ex.printStackTrace()
          val msg = ex.getMessage()
          error(
            if (msg eq null) "i/o error while loading " + root.name
            else "error while loading " + root.name + ", " + msg);
      }
      initRoot(root)
      if (!root.isPackageClass) initRoot(root.companionSymbol)
    }

    override def load(root: Symbol) { complete(root) }

    private def initRoot(root: Symbol) {
      if (root.rawInfo == this) {
        def markAbsent(sym: Symbol) =
          if (sym != NoSymbol) sym.setInfo(if (ok) NoType else ErrorType);
        markAbsent(root)
        markAbsent(root.moduleClass)
      } else if (root.isClass && !root.isModuleClass) root.rawInfo.load(root)
    }
  }

  /**
   * Load contents of a package
   */
  abstract class PackageLoader[T](classpath: ClassPath[T]) extends SymbolLoader {
    protected def description = "package loader "+ classpath.name

    def enterPackage(root: Symbol, name: String, completer: SymbolLoader) {
      val preExisting = root.info.decls.lookup(newTermName(name))
      if (preExisting != NoSymbol)
        throw new TypeError(
          root+" contains object and package with same name: "+name+"\none of them needs to be removed from classpath")
      val pkg = root.newPackage(NoPosition, newTermName(name))
      pkg.moduleClass.setInfo(completer)
      pkg.setInfo(pkg.moduleClass.tpe)
      root.info.decls.enter(pkg)
    }

    def enterClassAndModule(root: Symbol, name: String, completer: SymbolLoader) {
      val owner = if (root.isRoot) definitions.EmptyPackageClass else root
      val className = newTermName(name)
      assert(owner.info.decls.lookup(name) == NoSymbol, owner.fullName + "." + name)
      val clazz = owner.newClass(NoPosition, name.toTypeName)
      val module = owner.newModule(NoPosition, name)
      clazz setInfo completer
      module setInfo completer
      module.moduleClass setInfo moduleClassLoader
      owner.info.decls enter clazz
      owner.info.decls enter module
      assert(clazz.companionModule == module, module)
      assert(module.companionClass == clazz, clazz)
    }

    /**
     * Tells whether a class with both a binary and a source representation
     * (found in classpath and in sourcepath) should be re-compiled. Behaves
     * similar to javac, i.e. if the source file is newer than the classfile,
     * a re-compile is triggered.
     */
    protected def needCompile(bin: T, src: AbstractFile): Boolean

    /**
     * Tells whether a class should be loaded and entered into the package
     * scope. On .NET, this method returns `false' for all synthetic classes
     * (anonymous classes, implementation classes, module classes), their
     * symtab is encoded in the pickle of another class.
     */
    protected def doLoad(cls: classpath.AnyClassRep): Boolean

    protected def newClassLoader(bin: T): SymbolLoader

    protected def newPackageLoader(pkg: ClassPath[T]): SymbolLoader

    protected def doComplete(root: Symbol) {
      assert(root.isPackageClass, root)
      root.setInfo(new PackageClassInfoType(new Scope(), root))

      val sourcepaths = classpath.sourcepaths
      for (classRep <- classpath.classes if doLoad(classRep)) {
        if (classRep.binary.isDefined && classRep.source.isDefined) {
          val (bin, src) = (classRep.binary.get, classRep.source.get)
          val loader = if (needCompile(bin, src)) new SourcefileLoader(src)
                       else newClassLoader(bin)
          enterClassAndModule(root, classRep.name, loader)
        } else if (classRep.binary.isDefined) {
          enterClassAndModule(root, classRep.name, newClassLoader(classRep.binary.get))
        } else if (classRep.source.isDefined) {
          enterClassAndModule(root, classRep.name, new SourcefileLoader(classRep.source.get))
        }
      }

      for (pkg <- classpath.packages) {
        enterPackage(root, pkg.name, newPackageLoader(pkg))
      }

      // if there's a $member object, enter its members as well.
      val pkgModule = root.info.decl(nme.PACKAGEkw)
      if (pkgModule.isModule && !pkgModule.rawInfo.isInstanceOf[SourcefileLoader]) {
        //println("open "+pkgModule)//DEBUG
        openPackageModule(pkgModule)()
      }
    }
  }

  def openPackageModule(module: Symbol)(packageClass: Symbol = module.owner): Unit = {
    // unlink existing symbols in the package
    for (member <- module.info.decls.iterator) {
      if (!member.hasFlag(PRIVATE) && !member.isConstructor) {
        // todo: handle overlapping definitions in some way: mark as errors
        // or treat as abstractions. For now the symbol in the package module takes precedence.
        for (existing <- packageClass.info.decl(member.name).alternatives)
          packageClass.info.decls.unlink(existing)
      }
    }
    // enter non-private decls the class
    for (member <- module.info.decls.iterator) {
      if (!member.hasFlag(PRIVATE) && !member.isConstructor) {
        packageClass.info.decls.enter(member)
      }
    }
    // enter decls of parent classes
    for (pt <- module.info.parents; val p = pt.typeSymbol) {
      if (p != definitions.ObjectClass && p != definitions.ScalaObjectClass) {
        openPackageModule(p)(packageClass)
      }
    }
  }

  class JavaPackageLoader(classpath: ClassPath[AbstractFile]) extends PackageLoader(classpath) {    
    protected def needCompile(bin: AbstractFile, src: AbstractFile) =
      (src.lastModified >= bin.lastModified)

    protected def doLoad(cls: classpath.AnyClassRep) = true

    protected def newClassLoader(bin: AbstractFile) =
      new ClassfileLoader(bin)

    protected def newPackageLoader(pkg: ClassPath[AbstractFile]) =
      new JavaPackageLoader(pkg)
  }

  class NamespaceLoader(classpath: ClassPath[MSILType]) extends PackageLoader(classpath) {
    protected def needCompile(bin: MSILType, src: AbstractFile) =
      false // always use compiled file on .net

    protected def doLoad(cls: classpath.AnyClassRep) = {
      if (cls.binary.isDefined) {
        val typ = cls.binary.get
        if (typ.IsDefined(clrTypes.SCALA_SYMTAB_ATTR, false)) {
          val attrs = typ.GetCustomAttributes(clrTypes.SCALA_SYMTAB_ATTR, false)
          assert (attrs.length == 1, attrs.length)
          val a = attrs(0).asInstanceOf[MSILAttribute]
          // symtab_constr takes a byte array argument (the pickle), i.e. typ has a pickle.
          // otherwise, symtab_default_constr was used, which marks typ as scala-synthetic.
          a.getConstructor() == clrTypes.SYMTAB_CONSTR
        } else true // always load non-scala types
      } else true // always load source
    }

    protected def newClassLoader(bin: MSILType) =
      new MSILTypeLoader(bin)

    protected def newPackageLoader(pkg: ClassPath[MSILType]) =
      new NamespaceLoader(pkg)

  }

  class ClassfileLoader(val classfile: AbstractFile) extends SymbolLoader {
    private object classfileParser extends ClassfileParser {
      val global: SymbolLoaders.this.global.type = SymbolLoaders.this.global
    }
    
    protected def description = "class file "+ classfile.toString

    protected def doComplete(root: Symbol) {
      val start = startTimer(classReadNanos)
      classfileParser.parse(classfile, root)
      stopTimer(classReadNanos, start)
    }
    override protected def sourcefile = classfileParser.srcfile
  }

  class MSILTypeLoader(typ: MSILType) extends SymbolLoader {
    private object typeParser extends clr.TypeParser {
      val global: SymbolLoaders.this.global.type = SymbolLoaders.this.global
    }

    protected def description = "MSILType "+ typ.FullName + ", assembly "+ typ.Assembly.FullName
    protected def doComplete(root: Symbol) { typeParser.parse(typ, root) }
  }

  class SourcefileLoader(val srcfile: AbstractFile) extends SymbolLoader {
    protected def description = "source file "+ srcfile.toString
    override protected def sourcefile = Some(srcfile)
    protected def doComplete(root: Symbol): Unit = global.currentRun.compileLate(srcfile)
  }

  object moduleClassLoader extends SymbolLoader {
    protected def description = "module class loader"
    protected def doComplete(root: Symbol) { root.sourceModule.initialize }
  }

  object clrTypes extends clr.CLRTypes {
    val global: SymbolLoaders.this.global.type = SymbolLoaders.this.global
    if (global.forMSIL) init()
  }

  /** used from classfile parser to avoid cyclies */
  var parentsLevel = 0
  var pendingLoadActions: List[() => Unit] = Nil
}
