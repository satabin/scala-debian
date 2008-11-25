/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ShortRef.java 14532 2008-04-07 12:23:22Z washburn $


package scala.runtime;


public class ShortRef implements java.io.Serializable {
    private static final long serialVersionUID = 4218441291229072313L;

    public short elem;
    public ShortRef(short elem) { this.elem = elem; }
}