/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RevertableHistory.scala 10086 2007-02-21 19:10:41Z odersky $


package scala.collection.mutable


/** A revertable history is a <code>History</code> object which supports
 *  an undo operation. Type variable <code>A</code> refers to the type
 *  of the published events, <code>B</code> denotes the publisher type.
 *  Type <code>B</code> is typically a subtype of <code>Publisher</code>.
 *  
 *  @author  Matthias Zenger
 *  @version 1.0, 08/07/2003
 */
@serializable
class RevertableHistory[A <: Undoable, B] extends History[A, B] with Undoable {

  /** Rollback the full history.
   */
  def undo: Unit = {
    val old = log.toList.reverse
    clear
    old.foreach { case (sub, event) => event.undo }
  }
}
