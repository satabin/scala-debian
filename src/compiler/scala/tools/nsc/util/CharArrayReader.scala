/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: CharArrayReader.scala 15467 2008-07-02 08:05:41Z washburn $

package scala.tools.nsc.util

import scala.tools.nsc.util.SourceFile.{LF, FF, CR, SU}

class CharArrayReader(buf: RandomAccessSeq[Char], start: Int, /* startline: int, startcol: int, */
                      decodeUni: Boolean, error: String => Unit) extends Iterator[Char] with Cloneable {

  def this(buf: RandomAccessSeq[Char], decodeUni: Boolean, error: String => Unit) =
    this(buf, 0, /* 1, 1, */ decodeUni, error)

  /** produce a duplicate of this char array reader which starts reading
    *  at current position, independent of what happens to original reader
	*/
  def dup: CharArrayReader = clone().asInstanceOf[CharArrayReader]

  /** layout constant
   */
  val tabinc = 8

  /** the line and column position of the current character
  */
  var ch: Char = _
  var bp = start
  var oldBp = -1
  var oldCh: Char = _
  
  //private var cline: Int = _
  //private var ccol: Int = _
  def cpos = bp
  var isUnicode: Boolean = _
  var lastLineStartPos: Int = 0
  var lineStartPos: Int = 0
  var lastBlankLinePos: Int = 0

  private var onlyBlankChars = false
  //private var nextline = startline
  //private var nextcol = startcol

  private def markNewLine() {
    lastLineStartPos = lineStartPos
    if (onlyBlankChars) lastBlankLinePos = lineStartPos
    lineStartPos = bp
    onlyBlankChars = true
    //nextline += 1
    //nextcol = 1
  }

  def hasNext: Boolean = if (bp < buf.length) true 
  else {
    false
  }

  def last: Char = if (bp > start + 2) buf(bp - 2) else ' ' // XML literals

  def next: Char = {
    //cline = nextline
    //ccol = nextcol
    val buf = this.buf.asInstanceOf[runtime.BoxedCharArray].value
    if(!hasNext) {
      ch = SU
      return SU  // there is an endless stream of SU's at the end 
    }
    oldBp = bp
    oldCh = ch
    ch = buf(bp)
    isUnicode = false
    bp = bp + 1
    ch match {
      case '\t' =>
        // nextcol = ((nextcol - 1) / tabinc * tabinc) + tabinc + 1;
      case CR =>
        if (buf(bp) == LF) {
          ch = LF
          bp += 1
        }
        markNewLine()
      case LF | FF =>
        markNewLine()
      case '\\' =>
        def evenSlashPrefix: Boolean = {
          var p = bp - 2
          while (p >= 0 && buf(p) == '\\') p -= 1
          (bp - p) % 2 == 0
        }
        def udigit: Int = {
          val d = digit2int(buf(bp), 16)
          if (d >= 0) { bp += 1; /* nextcol = nextcol + 1 */ }
          else error("error in unicode escape");
          d
        }
        // nextcol += 1
        if (buf(bp) == 'u' && decodeUni && evenSlashPrefix) {
          do {
            bp += 1 //; nextcol += 1
          } while (buf(bp) == 'u');
          val code = udigit << 12 | udigit << 8 | udigit << 4 | udigit
          ch = code.asInstanceOf[Char]
          isUnicode = true
        }
      case _ =>
        if (ch > ' ') onlyBlankChars = false
        // nextcol += 1
    }
    ch
  }

  def rewind {
    if (oldBp == -1) throw new IllegalArgumentException
    bp = oldBp
    ch = oldCh
    oldBp = -1
    oldCh = 'x'
  }

  def copy: CharArrayReader =
    new CharArrayReader(buf, bp, /* nextcol, nextline, */ decodeUni, error)

  def digit2int(ch: Char, base: Int): Int = {
    if ('0' <= ch && ch <= '9' && ch < '0' + base)
      ch - '0'
    else if ('A' <= ch && ch < 'A' + base - 10)
      ch - 'A' + 10
    else if ('a' <= ch && ch < 'a' + base - 10)
      ch - 'a' + 10
    else
      -1
  }
}
