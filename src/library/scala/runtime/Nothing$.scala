/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Nothing$.scala 16570 2008-11-18 11:20:05Z dragos $


package scala.runtime


/**
 * Dummy class which exist only to satisfy the JVM. It corresponds
 * to <code>scala.Nothing</code>. If such type appears in method
 * signatures, it is erased to this one.
 */

sealed abstract class Nothing$ extends Throwable
