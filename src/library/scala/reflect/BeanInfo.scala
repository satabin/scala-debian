/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: BeanInfo.scala 14532 2008-04-07 12:23:22Z washburn $

package scala.reflect

/** <p>
 *    This attribute indicates that a JavaBean-compliant BeanInfo
 *    class should be generated for this attributed Scala class. 
 *    A val becomes a read-only property. A var becomes a read-write
 *    property. A def becomes a method. 
 *  </p>
 *
 *  @author Ross Judson (rjudson@managedobjects.com)
 */
class BeanInfo extends Annotation
