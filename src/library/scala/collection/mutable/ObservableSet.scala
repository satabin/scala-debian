/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ObservableSet.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.collection.mutable


/** This class is typically used as a mixin. It adds a subscription
 *  mechanism to the <code>Set</code> class into which this abstract
 *  class is mixed in. Class <code>ObservableSet</code> publishes
 *  events of the type <code>Message</code>.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 08/07/2003
 */
trait ObservableSet[A, This <: ObservableSet[A, This]] 
      extends Set[A]
      with Publisher[Message[A]
      with Undoable, This]
{ self: This =>

  abstract override def +=(elem: A): Unit = if (!contains(elem)) {
    super.+=(elem)
    publish(new Include(elem) with Undoable { def undo = -=(elem) })
  }

  abstract override def -=(elem: A): Unit = if (contains(elem)) {
    super.-=(elem)
    publish(new Remove(elem) with Undoable { def undo = +=(elem) })
  }

  abstract override def clear(): Unit = {
    super.clear
    publish(new Reset with Undoable { 
      def undo: Unit = throw new UnsupportedOperationException("cannot undo") 
    })
  }
}
