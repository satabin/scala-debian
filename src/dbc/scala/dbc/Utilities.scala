/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Utilities.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.dbc;


/** An object offering transformation methods (views) on various values.
 *  This object's members must be visible in an expression to use value
 *  auto-conversion.
 */
object Utilities {

  implicit def constantToValue (obj: statement.expression.Constant): Value =
    obj.constantValue;

  implicit def valueToConstant (obj: Value): statement.expression.Constant =
    new statement.expression.Constant {
      val constantValue = obj;
    }

}
