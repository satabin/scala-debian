/* NSC -- new Scala compiler
 * Copyright 2005-2006 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: CompilerRun.scala 7272 2006-04-27 15:40:29Z michelou $

package scala.tools.nsc

class CompilerRun {
  def firstPhase: Phase = NoPhase
  def terminalPhase: Phase = NoPhase
  def namerPhase: Phase = NoPhase
  def typerPhase: Phase = NoPhase
  def refchecksPhase: Phase = NoPhase
  def explicitOuterPhase: Phase = NoPhase
  def erasurePhase: Phase = NoPhase
  def flattenPhase: Phase = NoPhase
  def mixinPhase: Phase = NoPhase
  def icodePhase: Phase = NoPhase
  def phaseNamed(name: String): Phase = NoPhase
}

