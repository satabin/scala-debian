
/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Tuple12.scala 16881 2009-01-09 16:28:11Z cunei $

// generated by genprod on Wed Apr 23 10:06:16 CEST 2008  

package scala

/** Tuple12 is the canonical representation of a @see Product12 
 *  
 */
case class Tuple12[+T1, +T2, +T3, +T4, +T5, +T6, +T7, +T8, +T9, +T10, +T11, +T12](_1:T1, _2:T2, _3:T3, _4:T4, _5:T5, _6:T6, _7:T7, _8:T8, _9:T9, _10:T10, _11:T11, _12:T12) 
  extends Product12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]  {

   override def toString() = {
     val sb = new StringBuilder
     sb.append('(').append(_1).append(',').append(_2).append(',').append(_3).append(',').append(_4).append(',').append(_5).append(',').append(_6).append(',').append(_7).append(',').append(_8).append(',').append(_9).append(',').append(_10).append(',').append(_11).append(',').append(_12).append(')')
     sb.toString
   }
  
}
