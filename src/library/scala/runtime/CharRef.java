/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: CharRef.java 14532 2008-04-07 12:23:22Z washburn $


package scala.runtime;


public class CharRef implements java.io.Serializable {
    private static final long serialVersionUID = 6537214938268005702L;

    public char elem;
    public CharRef(char elem) { this.elem = elem; }
    public String toString() { return Character.toString(elem); }
}
