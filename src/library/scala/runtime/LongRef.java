/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: LongRef.java 14532 2008-04-07 12:23:22Z washburn $


package scala.runtime;


public class LongRef implements java.io.Serializable {
    private static final long serialVersionUID = -3567869820105829499L;

    public long elem;
    public LongRef(long elem) { this.elem = elem; }
    public String toString() { return Long.toString(elem); }
}
