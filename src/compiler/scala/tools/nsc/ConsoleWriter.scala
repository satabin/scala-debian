/* NSC -- new Scala compiler
 * Copyright 2006-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: ConsoleWriter.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.nsc

import java.io.Writer

/** A Writer that writes onto the Scala Console.
 *
 *  @author  Lex Spoon
 *  @version 1.0
 */
class ConsoleWriter extends Writer {
  def close = flush
  
  def flush = Console.flush
  
  def write(cbuf: Array[Char], off: Int, len: Int) {
    if (len > 0)
      write(new String(cbuf.subArray(off, off+len)))
  }

  override def write(str: String) { Console.print(str) }
}
