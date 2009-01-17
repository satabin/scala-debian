/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ExceptionHandling.java 16894 2009-01-13 13:09:41Z cunei $


package scala.runtime;


public abstract class ExceptionHandling {

  public static Throwable tryCatch(Runnable runnable) {
    try {
      runnable.run();
      return null;
    } catch (Throwable exception) {
      return exception;
    }
  }

}
