/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Subscriber.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.collection.mutable


/** <code>Subscriber[A, B]</code> objects may subscribe to events of
 *  type <code>A</code> published by an object of type <code>B</code>.
 *  <code>B</code> is typically a subtype of <a href="Publisher.html"
 *  target="contentFrame"><code>Publisher</code></a>.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 08/07/2003
 */
trait Subscriber[-A, -B] {
  def notify(pub: B, event: A): Unit
}
