// $Id: bug177.scala 5359 2005-12-16 15:33:49Z dubochet $

class A {
  def foo = {
    object Y {
      def bar = 1;
    }
    Y.bar
  }
}
