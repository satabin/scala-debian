/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ConsoleLogger.scala 8720 2006-09-20 16:03:18Z michelou $


package scala.util.logging

/**
 *  The trait <code>ConsoleLogger</code> is mixed into a concrete class who
 *  has class <code>Logged</code> among its base classes.
 *
 *  @author  Burak Emir
 *  @version 1.0
 */
trait ConsoleLogger {

  /** logs argument to Console using <code>Console.println</code>
   *
   *  @param msg ...
   */
  def log(msg: String): Unit = Console.println(msg)
}
