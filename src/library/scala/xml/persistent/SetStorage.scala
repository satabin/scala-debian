/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SetStorage.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.xml.persistent

import scala.collection.mutable
import java.io.File

/** A persistent store with set semantics. This class allows to add and remove 
 *  trees, but never contains two structurally equal trees.
 *
 *  @author Burak Emir
 */
class SetStorage(file: File) extends CachedFileStorage(file) {

  private var theSet: mutable.HashSet[Node] = new mutable.HashSet[Node]

  // initialize

  {
    val it = super.initialNodes
    dirty = it.hasNext
    for(val x <- it) {
      theSet += x;
    }
  }

  /* forwarding methods to hashset*/

  def += (e: Node): Unit = synchronized { this.dirty = true; theSet += e }

  def -= (e: Node): Unit = synchronized { this.dirty = true; theSet -= e }

  def nodes = synchronized { theSet.elements }

}
