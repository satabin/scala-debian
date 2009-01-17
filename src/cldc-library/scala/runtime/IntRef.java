/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: IntRef.java 16894 2009-01-13 13:09:41Z cunei $


package scala.runtime;


public class IntRef {
    public int elem;
    public IntRef(int elem) { this.elem = elem; }
    public String toString() { return String.valueOf(elem); }
}
