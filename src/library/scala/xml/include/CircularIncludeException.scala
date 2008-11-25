/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: CircularIncludeException.scala 12905 2007-09-18 09:13:45Z michelou $

package scala.xml.include

/**
 * <p>
 * A <code>CircularIncludeException</code> is thrown when
 * an included document attempts to include itself or 
 * one of its ancestor documents.
 * </p>
 */
class CircularIncludeException(message: String) extends XIncludeException {

    /**
     * Constructs a <code>CircularIncludeException</code> with <code>null</code>
     * as its error detail message.
     */
    def this() = this(null);

}
