/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Properties.scala 16881 2009-01-09 16:28:11Z cunei $


package scala.util

/** A utility to load the library properties from a Java properties file
 *  included in the jar.
 *
 *  @author Stephane Micheloud
 */
object Properties {

  /** The name of the properties file */
  private val propFilename = "/library.properties"

  /** The loaded properties */
  private val props = {
    val props = new java.util.Properties
    val stream = classOf[Application].getResourceAsStream(propFilename)
    if (stream != null)
      props.load(stream)
    props
  }

  /** The version number of the jar this was loaded from, or
    * "(unknown)" if it cannot be determined.
    */
  val versionString: String = {
    val defaultString = "(unknown)"
    "version " + props.getProperty("version.number")
  }
  
  val copyrightString: String = {
    val defaultString = "(c) 2002-2009 LAMP/EPFL"
    props.getProperty("copyright.string", defaultString)
  }

  val encodingString: String = {
    val defaultString = "UTF8" //"ISO-8859-1"
    props.getProperty("file.encoding", defaultString)
  }

  private val writer = new java.io.PrintWriter(Console.err, true)

  val versionMsg = "Scala library " + versionString + " -- " + copyrightString

  def main(args: Array[String]) {
    writer.println(versionMsg)
  }
}
