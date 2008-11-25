/* NSC -- new Scala compiler
 * Copyright 2005-2008 LAMP/EPFL
 * @author Martin Odersky
 */
// $Id: NewLinePrintWriter.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc
import java.io.{Writer, PrintWriter}

class NewLinePrintWriter(out: Writer, autoFlush: Boolean)
extends PrintWriter(out, autoFlush) {
  def this(out: Writer) = this(out, false)
  override def println() { print("\n"); flush() }
}

