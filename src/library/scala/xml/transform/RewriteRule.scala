/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RewriteRule.scala 10648 2007-04-10 08:40:09Z michelou $


package scala.xml.transform

/** a RewriteRule, when applied to a term, yields either
 *  the resulting of rewriting or the term itself it the rule
 *  is not applied.
 *
 *  @author  Burak Emir
 *  @version 1.0
 */
abstract class RewriteRule extends BasicTransformer {
  /** a name for this rewrite rule */
  val name = this.toString()
  override def transform(ns: Seq[Node]): Seq[Node] = super.transform(ns)
  override def transform(n: Node): Seq[Node] = n
}

