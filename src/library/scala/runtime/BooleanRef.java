/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: BooleanRef.java 14532 2008-04-07 12:23:22Z washburn $


package scala.runtime;


public class BooleanRef implements java.io.Serializable {
    private static final long serialVersionUID = -5730524563015615974L;

    public boolean elem;
    public BooleanRef(boolean elem) { this.elem = elem; }
    public String toString() { return "" + elem; }
}
