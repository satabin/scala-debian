/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: BoxedDoubleArray.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.runtime


import Predef._

@serializable
final class BoxedDoubleArray(val value: Array[Double]) extends BoxedArray {

  def length: Int = value.length

  def apply(index: Int): Any = Double.box(value(index))

  def update(index: Int, elem: Any) {
    value(index) = Double.unbox(elem.asInstanceOf[AnyRef])
  }

  def unbox(elemTag: String): AnyRef = value
  def unbox(elemClass: Class[_]): AnyRef = value

  override def equals(other: Any) =
    value == other ||
    other.isInstanceOf[BoxedDoubleArray] && value == other.asInstanceOf[BoxedDoubleArray].value

  override def hashCode(): Int = value.hashCode()

  def subArray(start: Int, end: Int): Array[Double] = {
    val result = new Array[Double](end - start)
    Array.copy(value, start, result, 0, end - start)
    result
  }

  final override def filter(p: Any => Boolean): BoxedArray = {
    val include = new Array[Boolean](value.length)
    var len = 0
    var i = 0
    while (i < value.length) {
      if (p(value(i))) { include(i) = true; len += 1 }
      i += 1
    }
    val result = new Array[Double](len)
    len = 0
    i = 0
    while (len < result.length) {
      if (include(i)) { result(len) = value(i); len += 1 }
      i += 1
    }
    new BoxedDoubleArray(result)
  }
  override protected def newArray(length : Int, elements : Iterator[Any]) = {
    val result = new Array[Double](length)
    elements.map(_.asInstanceOf[Double]).copyToArray(result, 0)
    new BoxedDoubleArray(result)
  }
}
