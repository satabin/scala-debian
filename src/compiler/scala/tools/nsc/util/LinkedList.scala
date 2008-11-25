/* NSC -- new Scala compiler
 * Copyright 2005-2006 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: LinkedList.scala 8770 2006-09-26 14:16:35Z michelou $

package scala.tools.nsc.util

class LinkedList[T] {
  var next: LinkedList[T] = null
  var elem: T = _
}
