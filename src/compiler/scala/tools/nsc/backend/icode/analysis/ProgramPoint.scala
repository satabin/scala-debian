/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id: ProgramPoint.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc.backend.icode.analysis

/** Program points are locations in the program where we want to
 *  assert certain properties through data flow analysis, e.g.
 *  basic blocks.
 */
trait ProgramPoint[a <: ProgramPoint[a]] {
  def predecessors: List[a]
  def successors: List[a]
}
