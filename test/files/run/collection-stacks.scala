import scala.collection._

object Test extends Application {
  def mutableStack[T](xs: T*): mutable.Stack[T] = {
    val s = new mutable.Stack[T]
    s.push(xs: _*)
    s
  }
  
  def immutableStack[T](xs: T*): immutable.Stack[T] = {
    immutable.Stack.Empty push xs
  }
  
  def check[T](expected: T, got: T) {
    println(got + ": " + (expected == got))
  }
  
  // check #957
  check("1-2-3", immutableStack(1, 2, 3).elements.mkString("-"))
  check("1-2-3", mutableStack(1, 2, 3).elements.mkString("-"))
  
  println("apply")
  check(1, immutableStack(1, 2, 3).apply(0))
  check(1, mutableStack(1, 2, 3).apply(0))
  check(3, immutableStack(1, 2, 3).apply(2))
  check(3, mutableStack(1, 2, 3).apply(2))
  
  println("top")
  check(3, immutableStack(1, 2, 3).top)
  check(3, mutableStack(1, 2, 3).top)
  
  println("pop")
  check("1-2", immutableStack(1, 2, 3).pop.mkString("-"))
  check(3, mutableStack(1, 2, 3).pop())
  check("1-2", { val s = mutableStack(1, 2, 3); s.pop(); s.toList.mkString("-") })
}

// vim: set ts=2 sw=2 et:
