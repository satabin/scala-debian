/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ApproximateNumeric.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.dbc.value;


abstract class ApproximateNumeric [Type] extends Value {
  
  val dataType: datatype.ApproximateNumeric[Type];
  
  def sqlString = nativeValue.toString();
  
  }

object ApproximateNumeric {
  
  implicit def approximateNumericToFloar (obj:value.ApproximateNumeric[Float]): Float = obj.nativeValue;
  implicit def approximateNumericToDouble (obj:value.ApproximateNumeric[Double]): Double = obj.nativeValue;
  
}
