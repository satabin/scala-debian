/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: BufferedSource.scala 16881 2009-01-09 16:28:11Z cunei $


package scala.io

import java.io.InputStream
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.channels.{ByteChannel, Channels, ReadableByteChannel}
import java.nio.charset.{Charset, CharsetDecoder}

object BufferedSource {

  /** same as fromInputStream(inpStream, Charset.forName(enc), buffer_size, do_reset) */
  def fromInputStream(inpStream: InputStream, enc: String, buffer_size: Int, do_reset: () => Source): BufferedSource =
    fromInputStream(inpStream, Charset.forName(enc), buffer_size, do_reset)

  /** same as fromInputStream(inpStream, charSet.newDecoder(), buffer_size, do_reset) */
  def fromInputStream(inpStream: InputStream, charSet: Charset, buffer_size: Int, do_reset: () => Source): BufferedSource =
    fromInputStream(inpStream, charSet.newDecoder(), buffer_size, do_reset)

  /** constructs a BufferedSource instance from an input stream, using given decoder */
  def fromInputStream(inpStream: InputStream, decoder: CharsetDecoder, buffer_size: Int, do_reset: () => Source): BufferedSource = {
    val byteChannel = Channels.newChannel(inpStream)
    return new {
      val buf_size = buffer_size
     } with BufferedSource(byteChannel, decoder) {
      override def reset = do_reset()
      def close { inpStream.close }
    }
  }
}

/** This object provides convenience methods to create an iterable
 *  representation of a source file.
 *
 *  @author  Burak Emir
 *  @version 1.0, 19/08/2004
 */
abstract class BufferedSource(byteChannel: ReadableByteChannel, decoder: CharsetDecoder) extends Source {

  val buf_size: Int

  def close: Unit

  val byteBuffer = ByteBuffer.allocate(buf_size)
  var charBuffer = CharBuffer.allocate(buf_size)
  byteBuffer.position(byteBuffer.limit())
  charBuffer.position(charBuffer.limit())
  decoder.reset()
  var endOfInput = false

  def fillBuffer() = {
    byteBuffer.compact()
    charBuffer.compact()
    var num_bytes = byteChannel.read(byteBuffer)
    while (0 == num_bytes) {
      Thread.sleep(1);  // wait 1 ms for new data
      num_bytes = byteChannel.read(byteBuffer) 
    }
    endOfInput = (num_bytes == -1)
    byteBuffer.flip()
    decoder.decode(byteBuffer, charBuffer, endOfInput)
    if (endOfInput) decoder.flush(charBuffer)
    charBuffer.flip()
  }
  override val iter = new Iterator[Char] {
    var buf_char = { 
      fillBuffer() 
      if (endOfInput) ' ' else charBuffer.get()
    }
    def hasNext = { charBuffer.remaining() > 0 || !endOfInput}
    def next = { 
      val c = buf_char
      if (charBuffer.remaining() == 0) {
        fillBuffer()
      }
      if (!endOfInput) {
        buf_char = charBuffer.get()
      }
      c
    }
  }
}

