/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: NamespaceBinding.scala 12905 2007-09-18 09:13:45Z michelou $


package scala.xml

import Predef._

/** The class <code>NamespaceBinding</code> represents namespace bindings
 *  and scopes. The binding for the default namespace is treated as a null
 *  prefix. the absent namespace is represented with the null uri. Neither
 *  prefix nor uri may be empty, which is not checked.
 *
 *  @author  Burak Emir
 *  @version 1.0
 */
@serializable
class NamespaceBinding(val prefix: String,
                       val uri: String,
                       val parent: NamespaceBinding) extends AnyRef {

  private val serialVersionUID = 0 - 2518644165573446725L

  if (null != prefix && 0 == prefix.length())
    throw new IllegalArgumentException("zero length prefix not allowed")

  def getURI(_prefix: String): String =
    if (prefix == _prefix) uri else parent.getURI(_prefix)

  /** Returns some prefix that is mapped to the prefix.
   *
   *  @param _uri
   *  @return
   */
  def getPrefix(_uri: String): String =
    if (_uri == uri) uri else parent.getURI(_uri)

  override def toString(): String = {
    val sb = new StringBuilder()
    toString(sb, TopScope)
    sb.toString()
  }

  def toString(stop: NamespaceBinding): String = {
    val sb = new StringBuilder()
    toString(sb, stop)
    sb.toString()
  }

  def toString(sb: StringBuilder, stop: NamespaceBinding): Unit = {
    if (this ne stop) { // contains?
      sb.append(" xmlns")
      if (prefix ne null) {
        sb.append(':').append(prefix)
      }
      sb.append('=')
      .append('"')
      .append(uri)
      .append('"');
      parent.toString(sb, stop) // copy(ignore)
    }
  }

}
