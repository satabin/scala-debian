/* NSC -- new Scala compiler
 * Copyright 2005-2008 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: Position.scala 16263 2008-10-15 13:58:23Z cunei $

package scala.tools.nsc.util

object Position {
  // a static field
  private val tabInc = 8
}

trait Position {
  import Position.tabInc
  def offset: Option[Int] = None
  def source: Option[SourceFile] = None

  def line: Option[Int] = 
    if (offset.isEmpty || source.isEmpty) None
    else Some(source.get.offsetToLine(offset.get) + 1)

  def column: Option[Int] =
    if (offset.isEmpty || source.isEmpty)
      None
    else {
      var column = 1
      // find beginning offset for line
      val line = source.get.offsetToLine(offset.get)
      var coffset = source.get.lineToOffset(line)
      var continue = true
      while (continue) {
        if (coffset == offset.getOrElse(-1)) continue = false
        else if (source.get.asInstanceOf[BatchSourceFile].content(coffset) == '\t') 
          column = ((column - 1) / tabInc * tabInc) + tabInc + 1
        else column += 1
        coffset += 1
      }
      Some(column)
    }

  def lineContent: String = {
    val line = this.line
    if (!line.isEmpty) source.get.lineToString(line.get - 1)
    else "NO_LINE"
  }
   
  /** Map this position to a position in an original source
   * file.  If the SourceFile is a normal SourceFile, simply
   * return this.
   */
  def inUltimateSource(source : SourceFile) = 
    if (source == null) this else source.positionInUltimateSource(this)
  
  def dbgString = {
    (if (source.isEmpty) "" else "source-" + source.get.path) +
      (if (line.isEmpty) "" else "line-" + line.get) +
        (if (offset.isEmpty) "" 
         else if (offset.get >= source.get.length) "out-of-bounds-" + offset.get 
         else {
           val ret = "offset=" + offset.get;
           var add = "";
           /*
           while (offset.get + add.length < source.get.length &&
                  add.length < 10) add = add + source.get.content(offset.get + add.length());
           */
           ret + " c[0..9]=\"" + add + "\"";
         })
  }
  
}

case object NoPosition extends Position
case class FakePos(msg: String) extends Position {
  override def toString=msg
}

case class LinePosition(source0: SourceFile, line0: Int) extends Position {
  assert(line0 >= 1)
  override def offset = None
  override def column = None
  override def line = Some(line0)
  override def source = Some(source0)
}

case class OffsetPosition(source0: SourceFile, offset0: Int) extends Position {
  override def source = Some(source0)
  override def offset = Some(offset0)
  override def equals(that : Any) = that match {
  case that : OffsetPosition => offset0 == that.offset0 && source0.file == that.source0.file 
  case that => false
  }
  override def hashCode = offset0 + source0.file.hashCode
}

/** new for position ranges */
class RangePosition(source0: SourceFile, offset0: Int, start: Int, end: Int) 
extends OffsetPosition(source0, offset0)
  
