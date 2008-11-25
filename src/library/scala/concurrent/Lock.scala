/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Lock.scala 9110 2006-11-01 16:03:28Z mihaylov $


package scala.concurrent

/** This class ...
 *
 *  @author  Martin Odersky
 *  @version 1.0, 10/03/2003
 */
class Lock {
  var available = true

  def acquire = synchronized {
    while (!available) wait()
    available = false
  }

  def release = synchronized {
    available = true
    notify()
  }
}
