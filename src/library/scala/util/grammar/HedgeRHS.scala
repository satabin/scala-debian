/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: HedgeRHS.scala 16894 2009-01-13 13:09:41Z cunei $


package scala.util.grammar

abstract class HedgeRHS

/** right hand side of a hedge production, deriving a single tree */
case class ConsRHS(tnt: Int, hnt: Int) extends HedgeRHS

/** right hand side of a hedge production, deriving any hedge */
case object AnyHedgeRHS extends HedgeRHS

/** right hand side of a hedge production, deriving the empty hedge */
case object EmptyHedgeRHS extends HedgeRHS
