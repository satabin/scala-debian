/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: Analyzer.scala 16003 2008-09-03 10:07:36Z washburn $

package scala.tools.nsc.typechecker

/** The main attribution phase.
 */ 
trait Analyzer extends AnyRef
            with Contexts
            with Namers
            with Typers
            with Infer
            with Variances
            with EtaExpansion
            with SyntheticMethods 
            with Unapplies
{
  val global : Global
  import global._

  object namerFactory extends SubComponent {
    val global: Analyzer.this.global.type = Analyzer.this.global
    val phaseName = "namer"
    def newPhase(_prev: Phase): StdPhase = new StdPhase(_prev) {
      override val checkable = false
      def apply(unit: CompilationUnit) {
        newNamer(rootContext(unit)).enterSym(unit.body)
      }
    }
  }

  object typerFactory extends SubComponent {
    val global: Analyzer.this.global.type = Analyzer.this.global
    val phaseName = "typer"
    def newPhase(_prev: Phase): StdPhase = new StdPhase(_prev) {
      if (!inIDE) resetTyper()
      def apply(unit: CompilationUnit) {
        unit.body = newTyper(rootContext(unit)).typed(unit.body)
      }
    }
  }
}

