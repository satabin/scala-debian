/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: BoxedUnit.java 14532 2008-04-07 12:23:22Z washburn $


package scala.runtime;


public final class BoxedUnit implements java.io.Serializable {
    private static final long serialVersionUID = 8405543498931817370L;

    public final static BoxedUnit UNIT = new BoxedUnit();

    private BoxedUnit() { }

    public boolean equals(java.lang.Object other) {
	return this == other;
    }

    public int hashCode() {
	return 0;
    }

    public String toString() {
	return "()";
    }
}
