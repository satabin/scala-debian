/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Aggregate.scala 5889 2006-03-05 00:33:02Z mihaylov $


package scala.dbc.statement.expression;


abstract class Aggregate extends Expression {
  
  def aggregateName: String;
  
  def setFunction: SetFunction;
  
  def filterClause: Option[Expression];
  
  /** A SQL-99 compliant string representation of the relation sub-
   * statement. This only has a meaning inside another statement. */
  def sqlInnerString: String = (
    aggregateName +
    "(" + setFunction.sqlString + ")" +
    (filterClause match {
      case None => ""
      case Some(fc) => " FILTER (WHERE " + fc.sqlString + ")"
    })
  )
  
}
