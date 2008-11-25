/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: native.scala 15822 2008-08-18 15:20:19Z rytz $


package scala

/**
 * Marker for native methods.
 * <p>
 *   <code>@native def f(x: Int, y: List[Long]): String = ..</code>
 * </p>
 * <p>
 *   Method body is not generated if method is marked with <code>@native</code>,
 *   but it is type checked when present.
 * </p>
 */
class native extends StaticAnnotation {}
