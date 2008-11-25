/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: FJTaskScheduler2.scala 16207 2008-10-06 08:59:54Z phaller $

package scala.actors

import compat.Platform

import java.lang.{Runnable, Thread, InterruptedException, System, Runtime}

import scala.collection.Set
import scala.collection.mutable.{ArrayBuffer, Buffer, HashMap, Queue, Stack, HashSet}

/**
 * FJTaskScheduler2
 *
 * @version 0.9.18
 * @author Philipp Haller
 */
class FJTaskScheduler2 extends Thread with IScheduler {
  // as long as this thread runs, JVM should not exit
  setDaemon(false)

  var printStats = false

  val rt = Runtime.getRuntime()
  val minNumThreads = 4

  val coreProp = try {
    System.getProperty("actors.corePoolSize")
  } catch {
    case ace: java.security.AccessControlException =>
      null
  }
  val maxProp =
    try {
      System.getProperty("actors.maxPoolSize")
    } catch {
      case ace: java.security.AccessControlException =>
        null
    }

  val initCoreSize =
    if (null ne coreProp) Integer.parseInt(coreProp)
    else {
      val numCores = rt.availableProcessors()
      if (2 * numCores > minNumThreads)
        2 * numCores
      else
        minNumThreads
    }

  val maxSize =
    if (null ne maxProp) Integer.parseInt(maxProp)
    else 256

  private var coreSize = initCoreSize

  private val executor =
    new FJTaskRunnerGroup(coreSize)

  private var terminating = false
  private var suspending = false

  private var lastActivity = Platform.currentTime

  private var submittedTasks = 0

  def printActorDump {}

  private val TICK_FREQ = 50
  private val CHECK_FREQ = 100

  def onLockup(handler: () => Unit) =
    lockupHandler = handler

  def onLockup(millis: Int)(handler: () => Unit) = {
    //LOCKUP_CHECK_FREQ = millis / CHECK_FREQ
    lockupHandler = handler
  }

  private var lockupHandler: () => Unit = null

  override def run() {
    try {
      while (!terminating) {
        this.synchronized {
          try {
            wait(CHECK_FREQ)
          } catch {
            case _: InterruptedException =>
              if (terminating) throw new QuitException
          }

          if (!suspending) {

            ActorGC.gc()

            // check if we need more threads
            if (Platform.currentTime - lastActivity >= TICK_FREQ
                && coreSize < maxSize
                && executor.checkPoolSize()) {
                  //Debug.info(this+": increasing thread pool size")
                  coreSize += 1
                  lastActivity = Platform.currentTime
                }
            else {
              if (ActorGC.allTerminated) {
                // if all worker threads idle terminate
                if (executor.getActiveCount() == 0) {
                  Debug.info(this+": initiating shutdown...")

                  // Note that we don't have to shutdown
                  // the FJTaskRunnerGroup since there is
                  // no separate thread associated with it,
                  // and FJTaskRunner threads have daemon status.

                  // terminate timer thread
                  Actor.timer.cancel()
                  throw new QuitException
                }
              }
            }
          }
        } // sync

      } // while (!terminating)
    } catch {
      case _: QuitException =>
        // allow thread to exit
        if (printStats) executor.stats()
    }
  }

  /**
   *  @param  task the task to be executed
   */
  def execute(task: Runnable): Unit =
    executor execute task

  def execute(fun: => Unit): Unit =
    executor.execute(new Runnable {
      def run() { fun }
    })

  /**
   *  @param  a the actor
   */
  def tick(a: Actor) {
    lastActivity = Platform.currentTime
  }

  /** Shuts down all idle worker threads.
   */
  def shutdown(): Unit = synchronized {
    terminating = true
    // terminate timer thread
    Actor.timer.cancel()
  }

  def snapshot(): LinkedQueue = {
    suspending = true
    executor.snapshot()
  }

}
