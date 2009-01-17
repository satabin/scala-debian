/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://www.scala-lang.org/           **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Scriptable.scala 16881 2009-01-09 16:28:11Z cunei $


package scala.collection.mutable


/** Classes that mix in the <code>Scriptable</code> class allow
 *  messages to be sent to objects of that class.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 09/05/2004
 */
trait Scriptable[A] {

    /** Send a message to this scriptable object.
     */
    def <<(cmd: A): Unit
}
