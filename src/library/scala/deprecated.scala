/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: deprecated.scala 10201 2007-03-04 10:53:12Z mihaylov $


package scala

/**
 * An annotation that designates the definition to which it is applied as deprecated.
 * Access to the member then generates a deprecated warning.
 */
class deprecated extends StaticAnnotation {}
