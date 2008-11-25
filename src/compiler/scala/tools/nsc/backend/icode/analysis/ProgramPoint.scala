/* NSC -- new Scala compiler
 * Copyright 2005-2006 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id: ProgramPoint.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc.backend.icode.analysis

/** Program points are locations in the program where we want to
 *  assert certain properties through data flow analysis, e.g.
 *  basic blocks.
 */
trait ProgramPoint[a <: ProgramPoint[a]] {
  def predecessors: List[a]
  def successors: List[a]
}
