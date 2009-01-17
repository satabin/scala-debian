/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ByteRef.java 16894 2009-01-13 13:09:41Z cunei $


package scala.runtime;


public class ByteRef {
    public byte elem;
    public ByteRef(byte elem) { this.elem = elem; }
    public String toString() { return String.valueOf(elem); }
}
