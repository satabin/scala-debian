/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
*/

// $Id: SerialVersionUID.scala 16894 2009-01-13 13:09:41Z cunei $


package scala


/**
 * Annotation for specifying the static SerialVersionUID field
 * of a serializable class
 */
class SerialVersionUID(uid: Long) extends StaticAnnotation
