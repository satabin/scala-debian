/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: PrettyPrinter.scala 14532 2008-04-07 12:23:22Z washburn $


package scala.xml

import scala.collection.Map

/** Class for pretty printing. After instantiating, you can use the
 *  toPrettyXML methods to convert XML to a formatted string. The class 
 *  can be reused to pretty print any number of XML nodes.
 *
 *  @author  Burak Emir
 *  @version 1.0
 *
 *  @param width the width to fit the output into
 *  @step  indentation
 */
class PrettyPrinter( width:Int, step:Int ) {

  class BrokenException() extends java.lang.Exception

  class Item
  case object Break extends Item {
    override def toString() = "\\"
  }
  case class Box(col: Int, s: String) extends Item
  case class Para(s: String) extends Item

  protected var items: List[Item] = Nil

  protected var cur = 0
  //protected var pmap:Map[String,String] = _

  protected def reset() = {
    cur = 0
    items = Nil
  }

  /** Try to cut at whitespace.
   *
   *  @param s   ...
   *  @param ind ...
   *  @return    ...
   */
  protected def cut(s: String, ind: Int): List[Item] = {
    val tmp = width - cur
    if (s.length < tmp)
      return List(Box(ind, s))
    val sb = new StringBuilder()
    var i = s.indexOf(' ')
    if (i > tmp || i == -1) throw new BrokenException() // cannot break

    var last: List[Int] = Nil
    while (i != -1 && i < tmp) {
      last = i::last
      i = s.indexOf(' ', i+1)
    }
    var res: List[Item] = Nil
    while (Nil != last) try {
      val b = Box(ind, s.substring(0, last.head))
      cur = ind
      res = b :: Break :: cut(s.substring(last.head, s.length), ind)
       // backtrack
      last = last.tail
    } catch {
      case _:BrokenException => last = last.tail
    }
    throw new BrokenException()
  }

  /** Try to make indented box, if possible, else para.
   *
   *  @param ind ...
   *  @param s   ...
   *  @return    ...
   */
  protected def makeBox(ind: Int, s: String) = {
    if (cur < ind)
      cur == ind
    if (cur + s.length > width) {            // fits in this line
      items = Box(ind, s) :: items
      cur += s.length
    } else try {
      for (b <- cut(s, ind).elements)  // break it up
        items = b :: items
    } catch {
      case _:BrokenException => makePara(ind, s) // give up, para
    }
  }

  // dont respect indent in para, but afterwards
  protected def makePara(ind: Int, s: String) = {
    items = Break::Para(s)::Break::items
    cur = ind
  }

  // respect indent
  protected def makeBreak() = { // using wrapping here...
    items = Break :: items
    cur = 0
  }

  /**
   *  @param n ...
   *  @return  ...
   */
  protected def leafTag(n: Node) = {
    val sb = new StringBuilder("<")
    n.nameToString(sb)
    //Utility.appendPrefixedName( n.prefix, n.label, pmap, sb );
    n.attributes.toString(sb)
    //Utility.attr2xml( n.scope, n.attributes, pmap, sb );
    sb.append("/>")
    sb.toString()
  }

  protected def startTag(n: Node, pscope: NamespaceBinding): (String, Int) = {
    val sb = new StringBuilder("<")
    n.nameToString(sb) //Utility.appendPrefixedName( n.prefix, n.label, pmap, sb );
    val i = sb.length + 1
    n.attributes.toString(sb)
    n.scope.toString(sb, pscope)
    sb.append('>')
    (sb.toString(), i)
  }

  protected def endTag(n: Node) = {
    val sb = new StringBuilder("</")
    n.nameToString(sb) //Utility.appendPrefixedName( n.prefix, n.label, pmap, sb );
    sb.append('>')
    sb.toString()
  }

  protected def childrenAreLeaves(n: Node): Boolean = {
    val it = n.child.elements
    while (it.hasNext)
      it.next match {
        case _:Atom[_] | _:Comment | _:EntityRef | _:ProcInstr =>
        case _:Node =>
          return false
      }
    true
  }

  protected def fits(test: String) =
    test.length < width - cur

  /** @param tail: what we'd like to sqeeze in */
  protected def traverse(node: Node, pscope: NamespaceBinding, ind: Int): Unit =  node match {

      case Text(s) if s.trim() == "" =>
        ;
      case _:Atom[_] | _:Comment | _:EntityRef | _:ProcInstr => 
        makeBox( ind, node.toString().trim() )
      case g @ Group(xs) =>
        traverse(xs.elements, pscope, ind)
      case _ =>
        val test = {
          val sb = new StringBuilder()
          Utility.toXML(node, pscope, sb, false)
          if (node.attribute("http://www.w3.org/XML/1998/namespace", "space") == "preserve")
            sb.toString()
          else
            TextBuffer.fromString(sb.toString()).toText(0)._data
        }
        if (childrenAreLeaves(node) && fits(test)) {
          makeBox(ind, test)
        } else {
          val (stg, len2) = startTag(node, pscope)
          val etg = endTag(node)
          if (stg.length < width - cur) { // start tag fits
            makeBox(ind, stg)
            makeBreak()
            traverse(node.child.elements, node.scope, ind + step)
            makeBox(ind, etg)
          } else if (len2 < width - cur) {
            // <start label + attrs + tag + content + end tag
            makeBox(ind, stg.substring(0, len2))
            makeBreak() // todo: break the rest in pieces
            /*{ //@todo
             val sq:Seq[String] = stg.split(" ");
             val it = sq.elements;
             it.next;
             for (c <- it) {
               makeBox(ind+len2-2, c)
               makeBreak()
             }
             }*/
            makeBox(ind, stg.substring(len2, stg.length))
            makeBreak()
            traverse(node.child.elements, node.scope, ind + step)
            makeBox(cur, etg)
            makeBreak()
          } else { // give up
            makeBox(ind, test)
            makeBreak()
          }
        }
  }

  protected def traverse(it: Iterator[Node], scope: NamespaceBinding, ind: Int ): Unit =
    for (c <- it) {
      traverse(c, scope, ind)
      makeBreak()
    }

  /** Appends a formatted string containing well-formed XML with
   *  given namespace to prefix mapping to the given string buffer.
   *
   * @param n    the node to be serialized
   * @param pmap the namespace to prefix mapping
   * @param sb   the stringbuffer to append to
   */
  def format(n: Node, sb: StringBuilder ): Unit = // entry point
    format(n, null, sb)

  def format(n: Node, pscope: NamespaceBinding, sb: StringBuilder): Unit = { // entry point
    var lastwasbreak = false
    reset()
    traverse(n, pscope, 0)
    var cur = 0
    for (b <- items.reverse) b match {
      case Break =>
        if (!lastwasbreak) sb.append('\n')  // on windows: \r\n ?
        lastwasbreak = true
        cur = 0
//        while( cur < last ) {
//          sb.append(' '); 
//          cur = cur + 1; 
//        }

      case Box(i, s) =>
        lastwasbreak = false
        while (cur < i) {
          sb.append(' ')
          cur += 1
        }
        sb.append(s)
      case Para( s ) =>
        lastwasbreak = false
        sb.append(s)
    }
  }

  // public convenience methods

  /** returns a formatted string containing well-formed XML with 
   *  default namespace prefix mapping
   *
   *  @param n the node to be serialized
   *  @return  ...
   */
  def format(n: Node): String = format(n, null) //Utility.defaultPrefixes(n))

  /** Returns a formatted string containing well-formed XML with 
   *  given namespace to prefix mapping.
   *
   *  @param n    the node to be serialized
   *  @param pmap the namespace to prefix mapping
   *  @return     ...
   */
  def format(n: Node, pscope: NamespaceBinding): String = {
    val sb = new StringBuilder()
    format(n, pscope, sb)
    sb.toString()
  }

  /** Returns a formatted string containing well-formed XML nodes with
   *  default namespace prefix mapping.
   *
   *  @param nodes ...
   *  @return      ...
   */
  def formatNodes(nodes: Seq[Node]): String =
    formatNodes(nodes, null)

  /** Returns a formatted string containing well-formed XML.
   *
   *  @param nodes the sequence of nodes to be serialized
   *  @param pmap  the namespace to prefix mapping
   */
  def formatNodes(nodes: Seq[Node], pscope: NamespaceBinding): String = {
    var sb = new StringBuilder()
    formatNodes(nodes, pscope, sb)
    sb.toString()
  }

  /** Appends a formatted string containing well-formed XML with
   *  the given namespace to prefix mapping to the given stringbuffer.
   *
   *  @param n    the node to be serialized
   *  @param pmap the namespace to prefix mapping
   *  @param sb   the string buffer to which to append to
   */
  def formatNodes(nodes: Seq[Node], pscope: NamespaceBinding, sb: StringBuilder): Unit =
    for (n <- nodes.elements) {
      sb.append(format(n, pscope))
    }

}
