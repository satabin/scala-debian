/* NSC -- new Scala compiler
 * Copyright 2005-2010 LAMP/EPFL
 * @author Paul Phillips
 */

package scala.tools.nsc
package io

import java.io.{ PrintStream, ByteArrayOutputStream }

/** A sink for when you want to discard all output.
 */
class NullPrintStream extends PrintStream(new ByteArrayOutputStream()) { }

object NullPrintStream extends NullPrintStream {
  def setOut() = Console setOut this
  def setErr() = Console setErr this
  def setOutAndErr() = { setOut() ; setErr() }
}
