/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: remote.scala 15822 2008-08-18 15:20:19Z rytz $


package scala

/**
 * An annotation that designates the class to which it is applied as remotable.
 *
 * @see Method <a href="ScalaObject.html#$tag()">$tag</a> in trait
 *      <a href="ScalaObject.html">scala.ScalaObject</a>.
 */
class remote extends StaticAnnotation {}
