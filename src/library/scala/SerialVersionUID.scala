/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
*/

// $Id: SerialVersionUID.scala 15841 2008-08-19 14:29:45Z rytz $


package scala


/**
 * Annotation for specifying the static SerialVersionUID field
 * of a serializable class
 */
class SerialVersionUID(uid: Long) extends StaticAnnotation
