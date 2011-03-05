//############################################################################
// Serialization
//############################################################################

import java.lang.System

object Serialize {
  @throws(classOf[java.io.IOException])
  def write[A](o: A): Array[Byte] = {
    val ba = new java.io.ByteArrayOutputStream(512)
    val out = new java.io.ObjectOutputStream(ba)
    out.writeObject(o)
    out.close()
    ba.toByteArray()
  }
  @throws(classOf[java.io.IOException])
  @throws(classOf[ClassNotFoundException])
  def read[A](buffer: Array[Byte]): A = {
    val in =
      new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(buffer))
    in.readObject().asInstanceOf[A]
  }
  def check[A, B](x: A, y: B) {
    println("x = " + x)
    println("y = " + y)
    println("x equals y: " + (x equals y) + ", y equals x: " + (y equals x))
    println()
  }
}
import Serialize._

//############################################################################
// Test classes in package "scala"

object Test1_scala {

  private def arrayToString[A](arr: Array[A]): String =
    arr.mkString("Array[",",","]")

  private def arrayEquals[A, B](a1: Array[A], a2: Array[B]): Boolean =
    (a1.length == a2.length) &&
    (Iterator.range(0, a1.length) forall { i => a1(i) == a2(i) })

  @serializable
  object WeekDay extends Enumeration {
    type WeekDay = Value
    val Monday, Tuesday, Wednesday, Thusday, Friday, Saturday, Sunday = Value
  }
  import WeekDay._, BigDecimal._, RoundingMode._

  val x0 = List(1, 2, 3)
  val x1 = Nil
  val x2 = None
  val x3 = Array(1, 2, 3)
  val x4 = { x: Int => 2 * x }
  val x5 = 'hello
  val x6 = ("BannerLimit", 12345)
  val x7 = BigDecimal.RoundingMode
  val x8 = WeekDay
  val x9 = UP      // named element
  val x10 = Monday // unamed element

  try {
    val y0: List[Int]          = read(write(x0))
    val y1: List[Nothing]      = read(write(x1))
    val y2: Option[Nothing]    = read(write(x2))
    val y3: Array[Int]         = read(write(x3))
    val y4: Function[Int, Int] = read(write(x4))
    val y5: Symbol             = read(write(x5))
    val y6: (String, Int)      = read(write(x6))
    val y7: RoundingMode.type  = read(write(x7))
    val y8: WeekDay.type       = read(write(x8))
    val y9: RoundingMode       = read(write(x9))
    val y10: WeekDay           = read(write(x10))

    println("x0 = " + x0)
    println("y0 = " + y0)
    println("x0 eq y0: " + (x0 eq y0) + ", y0 eq x0: " + (y0 eq x0))
    println("x0 equals y0: " + (x0 equals y0) + ", y0 equals x0: " + (y0 equals x0))
    println()
    println("x1 = " + x1)
    println("y1 = " + y1)
    println("x1 eq y1: " + (x1 eq y1) + ", y1 eq x1: " + (y1 eq x1))
    println()
    println("x2 = " + x2)
    println("y2 = " + y2)
    println("x2 eq y2: " + (x2 eq y2) + ", y2 eq x2: " + (y2 eq x2))
    println()
    println("x3 = " + arrayToString(x3))
    println("y3 = " + arrayToString(y3))
    println("arrayEquals(x3, y3): " + arrayEquals(x3, y3))
    println()
    println("x4 = <na>")
    println("y4 = <na>")
    println("x4(2): " + x4(2) + " - y4(2): " + y4(2))
    println()
    println("x5 = " + x5)
    println("y5 = " + y5)
    println("x5 eq y5: " + (x5 eq y5) + ", y5 eq x5: " + (y5 eq x5))
    println("x5 equals y5: " + (x5 equals y5) + ", y5 equals x5: " + (y5 equals x5))
    println()
    println("x6 = " + x6)
    println("y6 = " + y6)
    println("x6 eq y6: " + (x6 eq y6) + ", y6 eq x6: " + (y6 eq x6))
    println("x6 equals y6: " + (x6 equals y6) + ", y6 equals x6: " + (y6 equals x6))
    println()
    println("x7 = " + x7)
    println("y7 = " + y7)
    println("x7 eq y7: " + (x7 eq y7) + ", y7 eq x7: " + (y7 eq x7))
    println("x7 equals y7: " + (x7 equals y7) + ", y7 equals x7: " + (y7 equals x7))
    println()
    println("x8 = " + x8)
    println("y8 = " + y8)
    println("x8 eq y8: " + (x8 eq y8) + ", y8 eq x8: " + (y8 eq x8))
    println("x8 equals y8: " + (x8 equals y8) + ", y8 equals x8: " + (y8 equals x8))
    println()
    println("x9 = " + x9)
    println("y9 = " + y9)
    println("x9 eq y9: " + (x9 eq y9) + ", y9 eq x9: " + (y9 eq x9))
    println("x9 equals y9: " + (x9 equals y9) + ", y9 equals x9: " + (y9 equals x9))
    println()
    println("x10 = " + x10)
    println("y10 = " + y10)
    println("x10 eq y10: " + (x10 eq y10) + ", y10 eq x10: " + (y10 eq x10))
    println("x10 equals y10: " + (x10 equals y10) + ", y10 equals x10: " + (y10 equals x10))
    println()
    println("x9 eq x10: " + (x9 eq x10) + ", x10 eq x9: " + (x10 eq x9))
    println("x9 equals x10: " + (x9 equals x10) + ", x10 equals x9: " + (x10 equals x9))
    println("x9 eq y10: " + (x9 eq y10) + ", y10 eq x9: " + (y10 eq x9))
    println("x9 equals y10: " + (x9 equals y10) + ", y10 equals x9: " + (y10 equals x9))
    println()
  }
  catch {
    case e: Exception =>
      e.printStackTrace()
      println("Error in Test1_scala: " + e)
  }
}

//############################################################################
// Test classes in package "scala.collection.immutable"

@serializable
object Test2_immutable {
  import scala.collection.immutable.{
    BitSet, HashMap, ListMap, ListSet, Queue, Stack, TreeSet, TreeMap, Vector}

  val x1 = List(
    ("buffers", 20),
    ("layers", 2),
    ("title", 3)
  )

  val m1 = new HashMap[Int, String] + (0 -> "A", 1 -> "B", 2 -> "C")

  val x2 = new ListMap[String, Int] + ("buffers" -> 20, "layers" -> 2, "title" -> 3)

  val x3 = {
    val bs = new collection.mutable.BitSet()
    bs += 2; bs += 3
    bs.toImmutable
  }

  val x4 = new ListSet[Int]() + 3 + 5

  val x5 = Queue("a", "b", "c")

  val x6 = Stack("a", "b", "c")

  val x7 = new TreeMap[Int, String] + (42 -> "FortyTwo")

  val x8 = new TreeSet[Int]() + 2 + 0

  val x9 = Vector(1, 2, 3)

  try {
    val y1: List[Pair[String, Int]] = read(write(x1))
    val n1: HashMap[Int, String]    = read(write(m1))
    val y2: ListMap[String, Int]    = read(write(x2))
    val y3: BitSet                  = read(write(x3))
    val y4: ListSet[Int]            = read(write(x4))
    val y5: Queue[String]           = read(write(x5))
    val y6: Stack[String]           = read(write(x6))
    val y7: TreeMap[Int, String]    = read(write(x7))
    val y8: TreeSet[Int]            = read(write(x8))
    val y9: Vector[Int]             = read(write(x9))

    check(x1, y1)
    check(m1, n1)
    check(x2, y2)
    check(x3, y3)
    check(x4, y4)
    check(x5, y5)
    check(x6, y6)
    check(x7, y7)
    check(x8, y8)
    check(x9, y9)
  }
  catch {
    case e: Exception =>
      println("Error in Test2_immutable: " + e)
      throw e
  }
}

//############################################################################
// Test classes in package "scala.collection.mutable"

object Test3_mutable {
  import scala.collection.mutable.{
    ArrayBuffer, BitSet, HashMap, HashSet, History, LinkedList, ListBuffer,
    Publisher, Queue, RevertableHistory, Stack}

  val x0 = new ArrayBuffer[String]
  x0 ++= List("one", "two")

  val x2 = new BitSet()
  x2 += 0
  x2 += 8
  x2 += 9

  val x1 = new HashMap[String, Int]
  x1 ++= Test2_immutable.x1

  val x3 = new HashSet[String]
  x3 ++= Test2_immutable.x1.map(p => p._1)

  val x4 = new LinkedList[Int](2, null)
  x4.append(new LinkedList(3, null))

  val x5 = new Queue[Int]
  x5 ++= Test2_immutable.x1.map(p => p._2)

  val x6 = new Stack[Int]
  x6 pushAll x5

  val x7 = new ListBuffer[String]
  x7 ++= List("white", "black")

  @serializable
  class Feed extends Publisher[String, Feed] {
    override def toString() = "Feed"
  }
  val feed = new Feed

  val x8 = new History[String, Feed]
  x8.notify(feed, "hello")

  try {
    val y0: ArrayBuffer[String]   = read(write(x0))
    val y1: HashMap[String, Int]  = read(write(x1))
    val y2: BitSet                = read(write(x2))
    val y3: HashSet[String]       = read(write(x3))
    val y4: LinkedList[Int]       = read(write(x4))
    val y5: Queue[Int]            = read(write(x5))
    val y6: Stack[Int]            = read(write(x6))
    val y7: ListBuffer[String]    = read(write(x7))
    val y8: History[String, Feed] = read(write(x8))

    check(x0, y0)
    check(x1, y1)
    check(x2, y2)
    check(x3, y3)
    check(x4, y4)
    check(x5, y5)
    check(x6, y6)
    check(x7, y7)
    check(x8, y8)
  }
  catch {
    case e: Exception =>
      println("Error in Test3_mutable: " + e)
  }
}

//############################################################################
// Test classes in package "scala.xml"

object Test4_xml {
  import scala.xml.Elem

  val x1 = <html><title>title</title><body></body></html>;

  case class Person(name: String, age: Int)

  class AddressBook(a: Person*) {
    private val people: List[Person] = a.toList
    def toXHTML =
      <table cellpadding="2" cellspacing="0">
        <tr>
          <th>Last Name</th>
          <th>First Name</th>
        </tr>
        { for (p <- people) yield
        <tr>
          <td> { p.name } </td>
          <td> { p.age.toString() } </td>
        </tr> }
      </table>;
  }

  val people = new AddressBook(
    Person("Tom", 20),
    Person("Bob", 22),
    Person("James", 19))

  val x2 =
    <html>
      <body>
       { people.toXHTML }
      </body>
    </html>;

  try {
    val y1: scala.xml.Elem = read(write(x1))
    val y2: scala.xml.Elem = read(write(x2))

    check(x1, y1)
    check(x2, y2)
  }
  catch {
    case e: Exception =>
      println("Error in Test4_xml: " + e)
  }
}

//############################################################################
// Test user-defined classes WITHOUT nesting

@serializable
class Person(_name: String) {
  private var name = _name
  override def toString() = name
  override def equals(that: Any): Boolean =
    that.isInstanceOf[Person] &&
    (name == that.asInstanceOf[Person].name)
}

@serializable
class Employee(_name: String) {
  private var name = _name
  override def toString() = name
}
@serializable
object bob extends Employee("Bob")

object Test5 {
  val x1 = new Person("Tim")
  val x2 = bob

  try {
    val y1: Person   = read(write(x1))
    val y2: Employee = read(write(x2))

    check(x1, y1)
    check(x2, y2)
  }
  catch {
    case e: Exception =>
      println("Error in Test5: " + e)
  }
}

//############################################################################
// Test user-defined classes WITH nesting

@serializable
object Test6 {
  @serializable
  object bill extends Employee("Bill") {
    val x = paul
  }
  @serializable
  object paul extends Person("Paul") {
    val x = 4  //  bill; => StackOverflowException !!!
  }
  val x1 = new Person("John")
  val x2 = bill
  val x3 = paul

  try {
    val y1: Person   = read(write(x1))
    val y2: Employee = read(write(x2))
    val y3: Person   = read(write(x3))

    check(x1, y1)
    check(x2, y2)
    check(x3, y3)
  }
  catch {
    case e: Exception =>
      println("Error in Test6: " + e)
  }
}

//############################################################################
// Test code

object Test {
  def main(args: Array[String]) {
    Test1_scala
    Test2_immutable
    Test3_mutable
    Test4_xml
    Test5
    Test6
  }
}

//############################################################################

