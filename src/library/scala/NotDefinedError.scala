/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: NotDefinedError.scala 16894 2009-01-13 13:09:41Z cunei $


package scala


import Predef._

final class NotDefinedError(msg: String) extends Error("not defined: " + msg)
