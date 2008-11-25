/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: LongRef.java 12003 2007-06-13 12:14:15Z mihaylov $


package scala.runtime;


public class LongRef {
    public long elem;
    public LongRef(long elem) { this.elem = elem; }
    public String toString() { return String.valueOf(elem); }
}
