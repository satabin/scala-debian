/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: TreeCheckers.scala 16039 2008-09-05 15:22:09Z washburn $

package scala.tools.nsc.typechecker

import scala.tools.nsc.symtab.Flags._
import scala.tools.nsc.util.{Position, NoPosition}

abstract class TreeCheckers extends Analyzer {

  import global._

  val tpeOfTree = new scala.collection.mutable.HashMap[Tree, Type]

  def checkTrees {
    if (settings.verbose.value)
      Console.println("[consistency check at the beginning of phase " + phase + "]")
    for (unit <- currentRun.units) check(unit)
  }

  def check(unit: CompilationUnit) {
    informProgress("checking "+unit)
    val context = rootContext(unit)
    context.checking = true
    tpeOfTree.clear
    val checker = new TreeChecker(context)

    val unit0 = currentRun.currentUnit
    currentRun.currentUnit = unit
    checker.precheck.traverse(unit.body)
    checker.typed(unit.body)
    checker.postcheck.traverse(unit.body)
    currentRun.advanceUnit
    assert(currentRun.currentUnit == unit)
    currentRun.currentUnit = unit0
  }

  override def newTyper(context: Context): Typer = new TreeChecker(context)

  class TreeChecker(context0: Context) extends Typer(context0) {

    import infer._

    override def typed(tree: Tree, mode: Int, pt: Type): Tree = {
      tree match {
        case EmptyTree | TypeTree() =>
          ;
        case _ =>
          try {
            if (!tpeOfTree.contains(tree)) {
              tpeOfTree.update(tree, tree.tpe)
              tree.tpe = null
            }
            val newtree = super.typed(tree, mode, pt);
            if ((newtree ne tree) && !newtree.isInstanceOf[Literal])
              error(tree.pos, "trees differ\n old: " + tree + " [" + tree.getClass() +
                    "]\n new: " + newtree + " [" + newtree.getClass() + "]")
          } catch {
            case ex: Throwable =>
              Console.println("exception while typing "+tree)
              throw ex
          }
      }
      tree
    }

    object precheck extends Traverser {
      override def traverse(tree: Tree) {
        try {
          tree match {
            case DefDef(_, _, _, _, _, _) =>
              if (tree.symbol.hasFlag(ACCESSOR) && 
                  !tree.symbol.isDeferred &&
                  !tree.symbol.tpe.resultType.isInstanceOf[ConstantType]) {
                assert(tree.symbol.accessed != NoSymbol, tree.symbol)
                assert(tree.symbol.accessed.getter(tree.symbol.owner) == tree.symbol ||
                       tree.symbol.accessed.setter(tree.symbol.owner) == tree.symbol)
              }
            case ValDef(_, _, _, _) =>
              if (tree.symbol.hasGetter) {
                assert(tree.symbol.getter(tree.symbol.owner) != NoSymbol, tree.symbol)
              }
            case Apply(_, args) =>
              assert(args forall (EmptyTree !=))
            case Select(_, _) =>
              assert(tree.symbol != NoSymbol, tree)
            case This(_) =>
              if (!(tree.symbol.isStatic && (tree.symbol hasFlag MODULE))) {
                var o = currentOwner
                while (o != tree.symbol) {
                  o = o.owner
                  if (o == NoSymbol) {
                    error(tree.pos, "tree symbol "+tree.symbol+" does not point to enclosing class; tree = "+tree)
                    return
                  }
                }
              }
            case _ =>
          }
          if (tree.pos == NoPosition && tree != EmptyTree) {
            error(tree.pos, "tree without position: " + tree)
          } else if ((tree.tpe eq null) && phase.id >= currentRun.typerPhase.id) {
            error(tree.pos, "tree without type: " + tree)
          } else if (tree.isDef && tree.symbol.owner != currentOwner) {
            var owner = currentOwner
            while (owner.isTerm && !owner.isMethod && tree.symbol.owner != owner)
              owner = owner.owner;
            if (tree.symbol.owner != owner) {
              error(tree.pos, "" + tree.symbol + " has wrong owner: " + tree.symbol.owner +
                    tree.symbol.owner.locationString + ", should be: " +
                    currentOwner + currentOwner.locationString)
            }
          } else {
            super.traverse(tree)
          }
        } catch {
          case ex: Throwable =>
            if (settings.debug.value)
              Console.println("exception when traversing " + tree);
            throw(ex)
        }
      }
    }

    object postcheck extends Traverser {
      override def traverse(tree: Tree) {
        try {
          tree match {
            case EmptyTree | TypeTree() =>
              ;
            case _ =>
              tpeOfTree.get(tree) match {
                case Some(oldtpe) =>
                  if (!(oldtpe =:= tree.tpe))
                    error(tree.pos, "types differ\n old: " + oldtpe +
                          "\n new: " + tree.tpe + "\n tree: " + tree)
                  tree.tpe = oldtpe
                  super.traverse(tree)
                case None =>
              }
          }
        } catch {
          case ex: Throwable =>
            if (settings.debug.value)
              Console.println("exception when traversing " + tree);
            throw(ex)
        }
      }
    }
  }
}
