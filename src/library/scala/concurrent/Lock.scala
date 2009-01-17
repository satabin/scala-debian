/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Lock.scala 16894 2009-01-13 13:09:41Z cunei $


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
