/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2010, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */



package scala

import collection.immutable.StringOps
import collection.mutable.ArrayOps
import collection.generic.CanBuildFrom
import annotation.elidable
import annotation.elidable.ASSERTION

/** The <code>Predef</code> object provides definitions that are
 *  accessible in all Scala compilation units without explicit
 *  qualification.
 */
object Predef extends LowPriorityImplicits {
  /** Return the runtime representation of a class type.  This is a stub method.
   *  The actual implementation is filled in by the compiler.
   */
  def classOf[T]: Class[T] = null

  type String        = java.lang.String
  type Class[T]      = java.lang.Class[T]

  // miscelleaneous -----------------------------------------------------
  scala.`package`                         // to force scala package object to be seen.
  scala.collection.immutable.List         // to force Nil, :: to be seen.

  type Function[-A, +B] = Function1[A, B]
    
  type Map[A, +B] = collection.immutable.Map[A, B]
  type Set[A] = collection.immutable.Set[A]
  val Map = collection.immutable.Map
  val Set = collection.immutable.Set

  type Manifest[T] = scala.reflect.Manifest[T]
  type ClassManifest[T] = scala.reflect.ClassManifest[T]
  def implicitly[T](implicit e: T) = e
  def manifest[T](implicit m: Manifest[T]) = m
  def classManifest[T](implicit m: ClassManifest[T]) = m

  // @see `conforms` for the implicit version
  def identity[A](x: A): A = x 

  def currentThread = java.lang.Thread.currentThread()

  @inline def locally[T](x: T): T = x

  // errors and asserts -------------------------------------------------

  def error(message: String): Nothing = throw new RuntimeException(message)

  def exit(): Nothing = exit(0)

  def exit(status: Int): Nothing = {
    java.lang.System.exit(status)
    throw new Throwable()
  }

  /** Tests an expression, throwing an AssertionError if false.
   *  Calls to this method will not be generated if -Xelide-below
   *  is at least ASSERTION.
   *
   *  @see elidable
   *  @param p   the expression to test
   */
  @elidable(ASSERTION)
  def assert(assertion: Boolean) {
    if (!assertion)
      throw new java.lang.AssertionError("assertion failed")
  }

  /** Tests an expression, throwing an AssertionError if false.
   *  Calls to this method will not be generated if -Xelide-below
   *  is at least ASSERTION.
   *
   *  @see elidable
   *  @param p   the expression to test
   *  @param msg a String to include in the failure message
   */
  @elidable(ASSERTION)
  def assert(assertion: Boolean, message: => Any) {
    if (!assertion)
      throw new java.lang.AssertionError("assertion failed: "+ message)
  }

  /** Tests an expression, throwing an AssertionError if false.
   *  This method differs from assert only in the intent expressed:
   *  assert contains a predicate which needs to be proven, while
   *  assume contains an axiom for a static checker.  Calls to this method
   *  will not be generated if -Xelide-below is at least ASSERTION.
   *
   *  @see elidable
   *  @param p   the expression to test
   */
  @elidable(ASSERTION)
  def assume(assumption: Boolean) {
    if (!assumption)
      throw new java.lang.AssertionError("assumption failed")
  }

  /** Tests an expression, throwing an AssertionError if false.
   *  This method differs from assert only in the intent expressed:
   *  assert contains a predicate which needs to be proven, while
   *  assume contains an axiom for a static checker.  Calls to this method
   *  will not be generated if -Xelide-below is at least ASSERTION.
   *
   *  @see elidable
   *  @param p   the expression to test
   *  @param msg a String to include in the failure message
   */
  @elidable(ASSERTION)
  def assume(assumption: Boolean, message: => Any) {
    if (!assumption)
      throw new java.lang.AssertionError("assumption failed: "+ message)
  }

  /** Tests an expression, throwing an IllegalArgumentException if false.
   *  This method is similar to assert, but blames the caller of the method
   *  for violating the condition.
   *
   *  @param p   the expression to test
   */
  def require(requirement: Boolean) {
    if (!requirement)
      throw new IllegalArgumentException("requirement failed")
  }

  /** Tests an expression, throwing an IllegalArgumentException if false.
   *  This method is similar to assert, but blames the caller of the method
   *  for violating the condition.
   *
   *  @param p   the expression to test
   *  @param msg a String to include in the failure message
   */
  def require(requirement: Boolean, message: => Any) {
    if (!requirement)
      throw new IllegalArgumentException("requirement failed: "+ message)
  }
  
  final class Ensuring[A](val x: A) {
    def ensuring(cond: Boolean): A = { assert(cond); x }
    def ensuring(cond: Boolean, msg: Any): A = { assert(cond, msg); x }
    def ensuring(cond: A => Boolean): A = { assert(cond(x)); x }
    def ensuring(cond: A => Boolean, msg: Any): A = { assert(cond(x), msg); x }
  }
  implicit def any2Ensuring[A](x: A): Ensuring[A] = new Ensuring(x)

  // tupling ------------------------------------------------------------

  type Pair[+A, +B] = Tuple2[A, B]
  object Pair {
    def apply[A, B](x: A, y: B) = Tuple2(x, y)
    def unapply[A, B](x: Tuple2[A, B]): Option[Tuple2[A, B]] = Some(x)
  }

  type Triple[+A, +B, +C] = Tuple3[A, B, C]
  object Triple {
    def apply[A, B, C](x: A, y: B, z: C) = Tuple3(x, y, z)
    def unapply[A, B, C](x: Tuple3[A, B, C]): Option[Tuple3[A, B, C]] = Some(x)
  }

  final class ArrowAssoc[A](val x: A) {
    @inline def -> [B](y: B): Tuple2[A, B] = Tuple2(x, y)
    def →[B](y: B): Tuple2[A, B] = ->(y)
  }
  implicit def any2ArrowAssoc[A](x: A): ArrowAssoc[A] = new ArrowAssoc(x)

  // printing and reading -----------------------------------------------

  def print(x: Any) = Console.print(x)
  def println() = Console.println()
  def println(x: Any) = Console.println(x)
  def printf(text: String, xs: Any*) = Console.print(format(text, xs: _*))
  def format(text: String, xs: Any*) = augmentString(text).format(xs: _*)

  def readLine(): String = Console.readLine()
  def readLine(text: String, args: Any*) = Console.readLine(text, args)
  def readBoolean() = Console.readBoolean()
  def readByte() = Console.readByte()
  def readShort() = Console.readShort()
  def readChar() = Console.readChar()
  def readInt() = Console.readInt()
  def readLong() = Console.readLong()
  def readFloat() = Console.readFloat()
  def readDouble() = Console.readDouble()
  def readf(format: String) = Console.readf(format)
  def readf1(format: String) = Console.readf1(format)
  def readf2(format: String) = Console.readf2(format)
  def readf3(format: String) = Console.readf3(format)
  
  // views --------------------------------------------------------------

  implicit def byteWrapper(x: Byte)     = new runtime.RichByte(x)
  implicit def shortWrapper(x: Short)   = new runtime.RichShort(x)
  implicit def intWrapper(x: Int)       = new runtime.RichInt(x)
  implicit def charWrapper(c: Char)     = new runtime.RichChar(c)
  implicit def longWrapper(x: Long)     = new runtime.RichLong(x)
  implicit def floatWrapper(x: Float)   = new runtime.RichFloat(x)
  implicit def doubleWrapper(x: Double) = new runtime.RichDouble(x)  
  implicit def booleanWrapper(x: Boolean) = new runtime.RichBoolean(x)

  implicit def exceptionWrapper(exc: Throwable) = new runtime.RichException(exc)

  implicit def genericArrayOps[T](xs: Array[T]): ArrayOps[T] = (xs: AnyRef) match { // !!! drop the AnyRef and get unreachable code errors!
    case x: Array[AnyRef] => refArrayOps[AnyRef](x).asInstanceOf[ArrayOps[T]]
    case x: Array[Int] => intArrayOps(x).asInstanceOf[ArrayOps[T]]
    case x: Array[Double] => doubleArrayOps(x).asInstanceOf[ArrayOps[T]]
    case x: Array[Long] => longArrayOps(x).asInstanceOf[ArrayOps[T]]
    case x: Array[Float] => floatArrayOps(x).asInstanceOf[ArrayOps[T]]
    case x: Array[Char] => charArrayOps(x).asInstanceOf[ArrayOps[T]]
    case x: Array[Byte] => byteArrayOps(x).asInstanceOf[ArrayOps[T]] 
    case x: Array[Short] => shortArrayOps(x).asInstanceOf[ArrayOps[T]]
    case x: Array[Boolean] => booleanArrayOps(x).asInstanceOf[ArrayOps[T]]
    case x: Array[Unit] => unitArrayOps(x).asInstanceOf[ArrayOps[T]]
    case null => null
  }
  
  implicit def refArrayOps[T <: AnyRef](xs: Array[T]): ArrayOps[T] = new ArrayOps.ofRef[T](xs)
  implicit def intArrayOps(xs: Array[Int]): ArrayOps[Int] = new ArrayOps.ofInt(xs)
  implicit def doubleArrayOps(xs: Array[Double]): ArrayOps[Double] = new ArrayOps.ofDouble(xs)
  implicit def longArrayOps(xs: Array[Long]): ArrayOps[Long] = new ArrayOps.ofLong(xs)
  implicit def floatArrayOps(xs: Array[Float]): ArrayOps[Float] = new ArrayOps.ofFloat(xs)
  implicit def charArrayOps(xs: Array[Char]): ArrayOps[Char] = new ArrayOps.ofChar(xs)
  implicit def byteArrayOps(xs: Array[Byte]): ArrayOps[Byte] = new ArrayOps.ofByte(xs)
  implicit def shortArrayOps(xs: Array[Short]): ArrayOps[Short] = new ArrayOps.ofShort(xs)
  implicit def booleanArrayOps(xs: Array[Boolean]): ArrayOps[Boolean] = new ArrayOps.ofBoolean(xs)
  implicit def unitArrayOps(xs: Array[Unit]): ArrayOps[Unit] = new ArrayOps.ofUnit(xs)

  // Primitive Widenings --------------------------------------------------------------

  implicit def byte2short(x: Byte): Short = x.toShort
  implicit def byte2int(x: Byte): Int = x.toInt
  implicit def byte2long(x: Byte): Long = x.toLong
  implicit def byte2float(x: Byte): Float = x.toFloat
  implicit def byte2double(x: Byte): Double = x.toDouble

  implicit def short2int(x: Short): Int = x.toInt
  implicit def short2long(x: Short): Long = x.toLong
  implicit def short2float(x: Short): Float = x.toFloat
  implicit def short2double(x: Short): Double = x.toDouble

  implicit def char2int(x: Char): Int = x.toInt
  implicit def char2long(x: Char): Long = x.toLong
  implicit def char2float(x: Char): Float = x.toFloat
  implicit def char2double(x: Char): Double = x.toDouble

  implicit def int2long(x: Int): Long = x.toLong
  implicit def int2float(x: Int): Float = x.toFloat
  implicit def int2double(x: Int): Double = x.toDouble

  implicit def long2float(x: Long): Float = x.toFloat
  implicit def long2double(x: Long): Double = x.toDouble

  implicit def float2double(x: Float): Double = x.toDouble
  
  // "Autoboxing" --------------------------------------------------------------  

  implicit def byte2Byte(x: Byte)           = java.lang.Byte.valueOf(x)
  implicit def short2Short(x: Short)        = java.lang.Short.valueOf(x)
  implicit def char2Character(x: Char)      = java.lang.Character.valueOf(x)
  implicit def int2Integer(x: Int)          = java.lang.Integer.valueOf(x)
  implicit def long2Long(x: Long)           = java.lang.Long.valueOf(x)
  implicit def float2Float(x: Float)        = java.lang.Float.valueOf(x)
  implicit def double2Double(x: Double)     = java.lang.Double.valueOf(x)
  implicit def boolean2Boolean(x: Boolean)  = java.lang.Boolean.valueOf(x)

  // Strings and CharSequences --------------------------------------------------------------

  implicit def any2stringadd(x: Any) = new runtime.StringAdd(x)
  implicit def augmentString(x: String): StringOps = new StringOps(x)
  implicit def unaugmentString(x: StringOps): String = x.repr

  implicit def stringCanBuildFrom: CanBuildFrom[String, Char, String] = 
    new CanBuildFrom[String, Char, String] { 
      def apply(from: String) = new scala.collection.mutable.StringBuilder 
      def apply() = new scala.collection.mutable.StringBuilder 
    }

  implicit def seqToCharSequence(xs: collection.IndexedSeq[Char]): CharSequence = new CharSequence {
    def length: Int = xs.length
    def charAt(index: Int): Char = xs(index)
    def subSequence(start: Int, end: Int): CharSequence = seqToCharSequence(xs.slice(start, end))
    override def toString: String = xs.mkString("")
  }

  implicit def arrayToCharSequence(xs: Array[Char]): CharSequence = new CharSequence {
    def length: Int = xs.length
    def charAt(index: Int): Char = xs(index)
    def subSequence(start: Int, end: Int): CharSequence = arrayToCharSequence(xs.slice(start, end))
    override def toString: String = xs.mkString("")
  }
  
  // Type Constraints --------------------------------------------------------------

  // used, for example, in the encoding of generalized constraints
  // we need a new type constructor `<:<` and evidence `conforms`, as 
  // reusing `Function2` and `identity` leads to ambiguities (any2stringadd is inferred)
  // to constrain any abstract type T that's in scope in a method's argument list (not just the method's own type parameters)
  // simply add an implicit argument of type `T <:< U`, where U is the required upper bound (for lower-bounds, use: `U <: T`)
  // in part contributed by Jason Zaugg
  sealed abstract class <:<[-From, +To] extends (From => To)
  implicit def conforms[A]: A <:< A = new (A <:< A) {def apply(x: A) = x} // not in the <:< companion object because it is also intended to subsume identity (which is no longer implicit)
 
  sealed abstract class =:=[From, To] extends (From => To)
  object =:= {
    implicit def tpEquals[A]: A =:= A = new (A =:= A) {def apply(x: A) = x}
  }
 
  sealed abstract class <%<[-From, +To] extends (From => To)
  object <%< {
    implicit def conformsOrViewsAs[A <% B, B]: A <%< B = new (A <%< B) {def apply(x: A) = x}
  }
 
  /** A type for which there is always an implicit value.
   *  @see fallbackCanBuildFrom in Array.scala
   */
  class DummyImplicit
  
  object DummyImplicit {
  
    /** An implicit value yielding a DummyImplicit.
     *   @see fallbackCanBuildFrom in Array.scala
     */
    implicit def dummyImplicit: DummyImplicit = new DummyImplicit
  }
}
