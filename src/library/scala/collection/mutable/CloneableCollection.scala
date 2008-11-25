/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: CloneableCollection.scala 12003 2007-06-13 12:14:15Z mihaylov $


package scala.collection.mutable

/** The J2ME version of the library defined this trait with a clone method
 * to substitute for the lack of Object.clone there
 */
trait CloneableCollection {
  override def clone(): AnyRef = super.clone()
}
