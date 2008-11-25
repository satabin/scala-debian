/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: FloatRef.java 14532 2008-04-07 12:23:22Z washburn $


package scala.runtime;


public class FloatRef implements java.io.Serializable {
    private static final long serialVersionUID = -5793980990371366933L;

    public float elem;
    public FloatRef(float elem) { this.elem = elem; }
    public String toString() { return Float.toString(elem); }
}
