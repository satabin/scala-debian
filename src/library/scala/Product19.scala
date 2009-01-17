
/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Product19.scala 16881 2009-01-09 16:28:11Z cunei $

// generated by genprod on Wed Apr 23 10:06:16 CEST 2008  

package scala

import Predef._

object Product19 {
  def unapply[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](x: Product19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]): Option[Product19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]] = 
    Some(x)
}

/** Product19 is a cartesian product of 19 components 
 *  
 */
trait Product19[+T1, +T2, +T3, +T4, +T5, +T6, +T7, +T8, +T9, +T10, +T11, +T12, +T13, +T14, +T15, +T16, +T17, +T18, +T19] extends Product {

  /**
   *  The arity of this product.
   *  @return 19
   */
  override def productArity = 19

  /**
   *  Returns the n-th projection of this product if 0&lt;=n&lt;arity,
   *  otherwise null.
   *
   *  @param n number of the projection to be returned 
   *  @return  same as _(n+1)
   *  @throws  IndexOutOfBoundsException
   */
  @throws(classOf[IndexOutOfBoundsException])
  override def productElement(n: Int) = n match {
    case 0 => _1
    case 1 => _2
    case 2 => _3
    case 3 => _4
    case 4 => _5
    case 5 => _6
    case 6 => _7
    case 7 => _8
    case 8 => _9
    case 9 => _10
    case 10 => _11
    case 11 => _12
    case 12 => _13
    case 13 => _14
    case 14 => _15
    case 15 => _16
    case 16 => _17
    case 17 => _18
    case 18 => _19
    case _ => throw new IndexOutOfBoundsException(n.toString())
  }

  /** projection of this product */
  def _1: T1

/** projection of this product */
  def _2: T2

/** projection of this product */
  def _3: T3

/** projection of this product */
  def _4: T4

/** projection of this product */
  def _5: T5

/** projection of this product */
  def _6: T6

/** projection of this product */
  def _7: T7

/** projection of this product */
  def _8: T8

/** projection of this product */
  def _9: T9

/** projection of this product */
  def _10: T10

/** projection of this product */
  def _11: T11

/** projection of this product */
  def _12: T12

/** projection of this product */
  def _13: T13

/** projection of this product */
  def _14: T14

/** projection of this product */
  def _15: T15

/** projection of this product */
  def _16: T16

/** projection of this product */
  def _17: T17

/** projection of this product */
  def _18: T18

/** projection of this product */
  def _19: T19



  
}
