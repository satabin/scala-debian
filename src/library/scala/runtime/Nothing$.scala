/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Nothing$.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.runtime


/**
 * Dummy class which exist only to satisfy the JVM. It corresponds
 * to <code>scala.Nothing</code>. If such type appears in method
 * signatures, it is erased to this one.
 */

sealed abstract class Nothing$ extends Throwable
