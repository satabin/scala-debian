/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id:Tuple.scala 6853 2006-03-20 16:58:47 +0100 (Mon, 20 Mar 2006) dubochet $


package scala.dbc.result;


/** An ISO-9075:2003 (SQL) table row. This is equivalent to a tuple in the relational model. */
abstract class Tuple {
  
  /** All the fields contained in the tuple. */
  def fields: List[Field];
  
  /** The relation that contains the tuple. */
  def originatingRelation: Relation;
  
  /** The field at the given index. If there is no such field (that is the index is out of bounds), <code>None</code> is returned instead. */
    def apply (index:Int): Field =
      try {
        fields(index)
      } catch {
        case e =>
          throw new java.lang.IndexOutOfBoundsException("Field at index "+index+" does not exist in relation");
      }
    
  /** The field with the given column name. If there is no such field, <code>None</code> is returned instead. */
  def apply (name:String): Field = {
    def findField (fields: List[Field], name:String): Field = fields match {
      case Nil => throw new java.lang.IndexOutOfBoundsException("Field '"+name+"' does not exist in relation")
      case field :: _ if (field.metadata.name == name) => field
      case field :: fields => findField (fields, name)
    }
      findField (fields, name);
  }
}
