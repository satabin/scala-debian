// $Id: bug183.scala 5359 2005-12-16 15:33:49Z dubochet $

object Test {
  new Foo(0);
  class Foo(x: Int);
}
