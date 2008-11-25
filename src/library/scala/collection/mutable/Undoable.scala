/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Undoable.scala 10203 2007-03-05 14:21:28Z odersky $


package scala.collection.mutable


/** Classes that mix in the <code>Undoable</code> class provide an operation
 *  <code>undo</code> which can be used to undo the last operation.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 08/07/2003
 */
trait Undoable {

    /** Undo the last operation.
     */
    def undo()
}
