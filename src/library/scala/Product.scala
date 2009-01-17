/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Product.scala 16881 2009-01-09 16:28:11Z cunei $


package scala

/** The trait <code>Product</code> defines access functions for instances
 *  of products, in particular case classes.
 *
 *  @author  Burak Emir
 *  @version 1.0
 */
trait Product extends AnyRef {

  /** for a product <code>A(x_1,...,x_k)</code>, returns <code>x_(n+1)</code>
   *  for <code>0 &lt;= n &lt; k</code>
   *
   *  @param  n the index of the element to return
   *  @throws IndexOutOfBoundsException
   *  @return  The element <code>n</code> elements after the first element
   */
  def productElement(n: Int): Any

  /** return k for a product <code>A(x_1,...,x_k)</code>
   */
  def productArity: Int

  /** 
   *  By default the empty string. Implementations may override this
   *  method in order to prepend a string prefix to the result of the 
   *  toString methods. 
   */
  def productPrefix = ""

}
