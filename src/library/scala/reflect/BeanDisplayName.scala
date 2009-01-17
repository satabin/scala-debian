/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: BeanDisplayName.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.reflect

/** Provides a display name when generating bean information. This
 *  annotation can be attached to the bean itself, or to any member.  
 *
 *  @author Ross Judson (rjudson@managedobjects.com)
 */
class BeanDisplayName(val name: String) extends Annotation

