/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id:CharacterLargeObject.scala 6853 2006-03-20 16:58:47 +0100 (Mon, 20 Mar 2006) dubochet $


package scala.dbc.datatype;


/** A SQL type for an unbounded length string of characters with arbitrary
  * character set. */
class CharacterLargeObject extends CharacterString {
  
  def isEquivalent (datatype:DataType) = datatype match {
    case dt:CharacterLargeObject => {
      encoding == dt.encoding
    }
    case _ => false
  }
  
  def isSubtypeOf (datatype:DataType) = isEquivalent(datatype);
  
  /** A SQL-99 compliant string representation of the type. */
  override def sqlString: java.lang.String = "CHARACTER LARGE OBJECT";
  
}
