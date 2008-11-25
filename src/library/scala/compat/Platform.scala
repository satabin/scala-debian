/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Platform.scala 14478 2008-04-02 11:02:48Z michelou $


package scala.compat


import java.lang.System
import Predef._

object Platform {

  type StackOverflowError = java.lang.StackOverflowError
  type ConcurrentModificationException = java.util.ConcurrentModificationException

  /**
   *  @param src     ..
   *  @param srcPos  ..
   *  @param dest    ..
   *  @param destPos ..
   *  @param length  ..
   */
  def arraycopy(src: AnyRef, srcPos: Int, dest: AnyRef, destPos: Int, length: Int) {
    System.arraycopy(src, srcPos, dest, destPos, length)
  }

  /** Create array of the same type as arrayInstance with the given
   *  length.
   *
   *  @param elemClass ..
   *  @param length    ..
   *  @return          ..
   */
  def createArray(elemClass: Class[_], length: Int): AnyRef =
    java.lang.reflect.Array.newInstance(elemClass, length)

  def arrayclear(arr: Array[Int]) { java.util.Arrays.fill(arr, 0) }

  def getClassForName(name: String): Class[_] = java.lang.Class.forName(name)

  val EOL = System.getProperty("line.separator", "\n")

  def currentTime: Long = System.currentTimeMillis()

  def collectGarbage: Unit = System.gc()

  /** The name of the default character set encoding as a string */
  def defaultCharsetName: String = {
    import java.io._
    new OutputStreamWriter(new ByteArrayOutputStream).getEncoding()
  }

}
