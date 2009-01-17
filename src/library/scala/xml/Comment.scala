/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Comment.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.xml

/** The class <code>Comment</code> implements an XML node for comments.
 *
 * @author Burak Emir
 * @param text the text contained in this node, may not contain "--"
 */
case class Comment(commentText: String) extends SpecialNode {  

  final override def typeTag$: Int = -3

  if (commentText.indexOf("--") != -1) 
    throw new IllegalArgumentException("text containts \"--\"")

  /** structural equality */
  override def equals(x: Any): Boolean = x match {
    case Comment(x) => x.equals(commentText)
    case _ => false
  }

  /** the constant &quot;#REM&quot; */
  def label = "#REM"

  /** hashcode for this Comment */
  override def hashCode() = commentText.hashCode()

  override def text = ""

  /** Appends &quot;<!-- text -->&quot; to this string buffer.
   */
  override def toString(sb: StringBuilder) =
    sb.append("<!--").append(commentText).append("-->")
}
