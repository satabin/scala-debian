/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: NotDefinedError.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala


import Predef._

final class NotDefinedError(msg: String) extends Error("not defined: " + msg)
