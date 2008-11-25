/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author Martin Odersky
 */
// $Id: SyntaxAnalyzer.scala 16003 2008-09-03 10:07:36Z washburn $

package scala.tools.nsc.ast.parser

import javac._

/** An nsc sub-component.
 */ 
abstract class SyntaxAnalyzer extends SubComponent with Parsers with MarkupParsers with NewScanners with JavaParsers with JavaScanners {

  val phaseName = "parser"

  def newPhase(prev: Phase): StdPhase = new ParserPhase(prev)

  class ParserPhase(prev: scala.tools.nsc.Phase) extends StdPhase(prev) {
    override val checkable = false
    def apply(unit: global.CompilationUnit) {
      global.informProgress("parsing " + unit)
      unit.body =     
        if (unit.source.file.name.endsWith(".java")) new JavaUnitParser(unit).parse()
        else new UnitParser(unit).parse()
    }
  }
}

