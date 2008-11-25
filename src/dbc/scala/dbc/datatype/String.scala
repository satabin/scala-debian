/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: String.scala 5889 2006-03-05 00:33:02Z mihaylov $


package scala.dbc.datatype;


/** A type category for all SQL types that store strings of elements.
 */
abstract class String extends DataType {
  
  /** The maximal possible length of the string defined in characters.
   *  This is an implementation-specific value.
   */
  def maxLength: Option[Int] = None;

}
