/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RuleTransformer.scala 10648 2007-04-10 08:40:09Z michelou $


package scala.xml.transform

class RuleTransformer(rules: RewriteRule*) extends BasicTransformer {
  override def transform(n: Node): Seq[Node] = {
    var m: Seq[Node] = super.transform(n)
    val it = rules.elements; while (it.hasNext) {
      val rule = it.next
      val m2 = rule.transform(m)
      //if(!m2.eq(m)) Console.println("applied rule \""+rule.name+"\"");
      m = m2
    }
    m
  }
}
