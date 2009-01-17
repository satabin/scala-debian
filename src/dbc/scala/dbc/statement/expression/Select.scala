/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Select.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.dbc.statement.expression;


abstract class Select extends Expression {

  /** The actual select statement */
  def selectStatement: statement.Select;

  /** A SQL-99 compliant string representation of the expression. */
  override def sqlString: String = selectStatement.sqlString;
  
  /** A SQL-99 compliant string representation of the relation sub-
   * statement. This only has a meaning inside another statement. */
  def sqlInnerString: String = "("+selectStatement.sqlString+")";
  
}
