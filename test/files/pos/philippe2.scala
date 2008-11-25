// $Id: philippe2.scala 5552 2006-01-17 15:29:35Z michelou $

import scala._;
class m1() {
  def n() = 0;
  def foo(i: Int)(j: Int): Unit = ();
  val bar: Int => Unit = foo(n());
}
