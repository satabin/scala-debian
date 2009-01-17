/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Lex Spoon
 */
// $Id: RegexCache.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc.util
import java.util.regex.Pattern
import scala.collection.mutable

object RegexCache {
  /** Maps patterns to compiled regexes */
  private val regexMap = mutable.Map.empty[String, Pattern]

  /** Lists the regexes that have been recorded in order */
  private val regexList = new mutable.Queue[String]
  
  private val regexesToCache = 1000

  /** Compile a regex and add it to the cache */
  private def compileAndAdd(regex: String): Pattern = {
    val pattern = Pattern.compile(regex)

    regexMap += (regex -> pattern)
    regexList += regex

    if (regexMap.size > regexesToCache)
      regexMap -= regexList.dequeue()

    pattern
  }


  /** Compile a regex, caching */
  def apply(regex: String): Pattern =
    regexMap.get(regex) match {
      case Some(pattern) => pattern
      case None => compileAndAdd(regex)
    }
}
