/* NSC -- new Scala compiler
 * Copyright 2006-2007 LAMP/EPFL
 * @author Burak Emir
 */
// $Id: Set64.scala 12502 2007-08-09 15:02:34Z emir $

package scala.tools.nsc.matching

/** An enumeration bit set that can handle enumeration values with ids up
 *  to 63 in a <code>Long</code>. copied, pasted and mutabilitized from
 *  Sean's Enumeration.
 */
class Set64  {

  var underlying: Long = 0

  final def contains(value: Int) = (underlying & (1L << value)) != 0          

//  def |=(  set: IntSet64)  { underlying = underlying | set.underlying   }
  final def |=(value: Int)     { underlying = underlying | (1L << value)    }
//  def &~=(value: Value)    { underlying = underlying & (~(1L << value)  }
//  def &=(set: Set64)       { underlying = underlying & set.underlying)  }

}
