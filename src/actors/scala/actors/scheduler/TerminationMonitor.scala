/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: TerminationMonitor.scala 18781 2009-09-24 18:28:17Z phaller $

package scala.actors.scheduler

import scala.collection.mutable.HashMap

private[actors] trait TerminationMonitor {

  protected var activeActors = 0
  protected val terminationHandlers = new HashMap[Actor, () => Unit]
  private var started = false

  /** newActor is invoked whenever a new actor is started. */
  def newActor(a: Actor) = synchronized {
    activeActors += 1
    if (!started)
      started = true
  }

  /** Registers a closure to be executed when the specified
   *  actor terminates.
   * 
   *  @param  a  the actor
   *  @param  f  the closure to be registered
   */
  def onTerminate(a: Actor)(f: => Unit): Unit = synchronized {
    terminationHandlers += (a -> (() => f))
  }

  /** Registers that the specified actor has terminated.
   *
   *  @param  a  the actor that has terminated
   */
  def terminated(a: Actor) = {
    // obtain termination handler (if any)
    val todo = synchronized {
      terminationHandlers.get(a) match {
        case Some(handler) =>
          terminationHandlers -= a
          handler
        case None =>
          () => { /* do nothing */ }
      }
    }

    // invoke termination handler (if any)
    todo()

    synchronized {
      activeActors -= 1
    }
  }

  /** Checks whether all actors have terminated. */
  @deprecated
  def allTerminated: Boolean = synchronized {
    started && activeActors <= 0
  }

  /** Checks for actors that have become garbage. */
  protected def gc() {}
}
