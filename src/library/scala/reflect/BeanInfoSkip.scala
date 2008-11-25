/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: BeanInfoSkip.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.reflect

/** This attribute indicates that bean information should
 *  <strong>not</strong> be generated for the val, var, or def that it is
 *  attached to.  
 *
 *  @author Ross Judson (rjudson@managedobjects.com)
 */
class BeanInfoSkip extends Annotation
