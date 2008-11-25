/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ExceptionHandling.java 14532 2008-04-07 12:23:22Z washburn $


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
