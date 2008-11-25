/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: UnsupportedFeature.scala 5889 2006-03-05 00:33:02Z mihaylov $


package scala.dbc.exception;


/** A type category for all SQL types that store constant-precision numbers. */
case class UnsupportedFeature (msg: String) extends Exception;
