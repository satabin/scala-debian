/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: TreeRHS.scala 8720 2006-09-20 16:03:18Z michelou $


package scala.util.grammar

/** right hand side of a tree production */
abstract class TreeRHS

/** right hand side of a tree production, labelled with a letter from an alphabet */
case class LabelledRHS[A](label: A, hnt: Int) extends TreeRHS

case object AnyTreeRHS extends TreeRHS
