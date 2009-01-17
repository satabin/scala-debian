/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: UnsupportedFeature.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.dbc.exception;


/** A type category for all SQL types that store constant-precision numbers. */
case class UnsupportedFeature (msg: String) extends Exception;
