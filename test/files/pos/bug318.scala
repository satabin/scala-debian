// $Id: bug318.scala 5359 2005-12-16 15:33:49Z dubochet $

object Test {
  def fun: Int = {
    object o {
      def a: Int = 1;
      class C { def b: Int =  a; }
    }
    0
  }
}
