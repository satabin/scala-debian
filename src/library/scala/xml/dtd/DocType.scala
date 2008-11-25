/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: DocType.scala 10788 2007-04-23 15:38:07Z michelou $


package scala.xml.dtd

/** An XML node for document type declaration.
 *
 *  @author Burak Emir
 *
 *  @param  target name of this DOCTYPE
 *  @param  extID  None, or Some(external ID of this doctype)
 *  @param  intSubset sequence of internal subset declarations
 */
case class DocType(name: String, extID: ExternalID, intSubset: Seq[dtd.Decl]) {  

  if (!Utility.isName(name))
    throw new IllegalArgumentException(name+" must be an XML Name");

  /** hashcode for this processing instruction */
  final override def hashCode() =
    name.hashCode() + 7 * extID.hashCode() + 41*intSubset.toList.hashCode();

  /** returns "&lt;!DOCTYPE + name + extID? + ("["+intSubSet+"]")? >" */
  final override def toString() = {
    val sb = new StringBuilder("<!DOCTYPE ")
    sb.append(name)
    sb.append(' ')
    sb.append(extID.toString())
    if (intSubset.length > 0) {
      sb.append('[')
      for (d <- intSubset) sb.append(d.toString())
      sb.append(']')
    }
    sb.append('>')
    sb.toString()
  }
}
