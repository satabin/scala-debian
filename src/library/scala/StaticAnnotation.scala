/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: StaticAnnotation.scala 16894 2009-01-13 13:09:41Z cunei $


package scala

/** <p>
 *    A base class for static annotations. These are available
 *    to the Scala type checker, even across different compilation units.
 *  </p>
 *
 *  @author  Martin Odersky
 *  @version 1.1, 2/02/2007
 */
trait StaticAnnotation extends Annotation {}
