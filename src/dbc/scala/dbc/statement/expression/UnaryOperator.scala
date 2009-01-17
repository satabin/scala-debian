/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: UnaryOperator.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.dbc.statement.expression;


abstract class UnaryOperator extends Expression {
  
  /** The name of the operator */
  def operator: String;
  
  /** Whether the operator comes before the operand or not. */
  def operatorIsLeft: Boolean;
  
  /** The operand applied to the operator. */
  def operand: Expression;
  
  /** A SQL-99 compliant string representation of the relation sub-
   * statement. This only has a meaning inside another statement. */
  def sqlInnerString: String = operatorIsLeft match {
    case true => operator + " " + operand.sqlInnerString;
    case false => operand.sqlInnerString + " " + operator;
  }
}
