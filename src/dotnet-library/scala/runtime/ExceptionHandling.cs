/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ExceptionHandling.cs 16894 2009-01-13 13:09:41Z cunei $

namespace scala.runtime {

  using System;

  public abstract class ExceptionHandling {

    public static Exception tryCatch(Runnable runnable) {
      try {
        runnable.run();
        return null;
      } catch (Exception exception) {
        return exception;
      }
    }
  }

}
