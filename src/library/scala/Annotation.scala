/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Annotation.scala 10201 2007-03-04 10:53:12Z mihaylov $


package scala

/** <p>
 *   A base class for annotations. Annotations extending this class directly
 *   are not preserved for the Scala type checker and are also not stored
 *   as Java annotations in classfiles. To enable either or both of these,
 *   one needs to inherit from
 *   <a href="StaticAnnotation.html"><code>StaticAnnotation</code></a> or/and
 *   <a href="ClassfileAnnotation.html"><code>ClassfileAnnotation</code></a>.
 *  </p>
 *
 *  @author  Martin Odersky
 *  @version 1.1, 2/02/2007
 */
abstract class Annotation {}
