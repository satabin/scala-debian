object Test extends Application {
  val a = Array(1, 2, 3)
  println(a.deepToString)

  val aaiIncomplete = new Array[Array[Array[Int]]](3)
  println(aaiIncomplete(0))

  val aaiComplete: Array[Array[Int]] = new Array[Array[Int]](3, 3)
  for (i <- 0 until 3; j <- 0 until 3)
    aaiComplete(i)(j) = i + j
  println(aaiComplete.deepToString)
}
