/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: BeanDescription.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.reflect

/** Provides a short description that will be included when generating
 *  bean information. This annotation can be attached to the bean itself,
 *  or to any member. 
 *
 *  @author Ross Judson (rjudson@managedobjects.com)
 */
class BeanDescription(val description: String) extends Annotation

