/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ObjectRef.java 16881 2009-01-09 16:28:11Z cunei $


package scala.runtime;


public class ObjectRef implements java.io.Serializable {
    private static final long serialVersionUID = -9055728157600312291L;

    public Object elem;
    public ObjectRef(Object elem) { this.elem = elem; }
    public String toString() { return "" + elem; }
}
