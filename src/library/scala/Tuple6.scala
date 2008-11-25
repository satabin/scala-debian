
/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Tuple6.scala 14794 2008-04-23 08:15:17Z washburn $

// generated by genprod on Wed Apr 23 10:06:16 CEST 2008  

package scala

/** Tuple6 is the canonical representation of a @see Product6 
 *  
 */
case class Tuple6[+T1, +T2, +T3, +T4, +T5, +T6](_1:T1, _2:T2, _3:T3, _4:T4, _5:T5, _6:T6) 
  extends Product6[T1, T2, T3, T4, T5, T6]  {

   override def toString() = {
     val sb = new StringBuilder
     sb.append('(').append(_1).append(',').append(_2).append(',').append(_3).append(',').append(_4).append(',').append(_5).append(',').append(_6).append(')')
     sb.toString
   }
  
}
