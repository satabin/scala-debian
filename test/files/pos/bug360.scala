// $Id: bug360.scala 11911 2007-06-05 15:57:59Z odersky $

abstract class Bug360A { self: Bug360C =>
  def f: String = "hello";
}
trait Bug360B { self: Bug360C =>
  object d {
    Console.println(f);
  }
}
abstract class Bug360C extends Bug360A with Bug360B;
