/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ShortRef.java 12003 2007-06-13 12:14:15Z mihaylov $


package scala.runtime;


public class ShortRef {
    public short elem;
    public ShortRef(short elem) { this.elem = elem; }
    public String toString() { return String.valueOf(elem); }
}
