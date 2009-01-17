/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2008-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: $


package scala.util

/**
 * Marshalling of Scala objects using Scala manifests.
 *
 * @author Stephane Micheloud
 * @version 1.0
 */
object Marshal {
  import java.io._
  import scala.reflect.Manifest

  def dump[A](o: A)(implicit m: Manifest[A]): Array[Byte] = {
    val ba = new ByteArrayOutputStream(512)
    val out = new ObjectOutputStream(ba)
    out.writeObject(m)
    out.writeObject(o)
    out.close()
    ba.toByteArray()
  }

  @throws(classOf[ClassCastException])
  def load[A](buffer: Array[Byte])(implicit expected: Manifest[A]): A = {
    val in = new ObjectInputStream(new ByteArrayInputStream(buffer))
    val found = in.readObject.asInstanceOf[Manifest[_]]
    if (! (found <:< expected))
      throw new ClassCastException("type mismatch;"+
        "\n found   : "+found+
        "\n required: "+expected)
    in.readObject.asInstanceOf[A]
  }

}
