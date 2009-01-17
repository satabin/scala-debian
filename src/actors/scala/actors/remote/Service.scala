/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Service.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.actors.remote

/**
 * @version 0.9.10
 * @author Philipp Haller
 */
trait Service {
  val kernel = new NetKernel(this)
  val serializer: Serializer
  def node: Node
  def send(node: Node, data: Array[Byte]): Unit
  def terminate(): Unit
}
