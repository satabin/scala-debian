// $Id: bug116.scala 5359 2005-12-16 15:33:49Z dubochet $

class C {
  def this(x: Int) = {
    this();
    class D extends C;
  }
}
