/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: TIMEOUT.scala 8724 2006-09-21 10:33:43Z michelou $


package scala.concurrent

/**
 * The message sent to a message box when the period specified in
 * <code>receiveWithin</code> expires.
 *
 * @author  Martin Odersky
 * @version 1.0, 10/03/2003
 */
case object TIMEOUT
