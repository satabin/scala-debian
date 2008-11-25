/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: PartialOrdering.scala 15609 2008-07-25 09:47:37Z washburn $

package scala

/** A trait for representing partial orderings.  It is important to 
 * distinguish between a type that has a partial order and a representation 
 * of partial ordering on some type.  This trait is for representing the latter.  
 * 
 * A <a href="http://en.wikipedia.org/wiki/Partial_order">partial ordering</a> 
 * is a binary relation on a type <code>T</code> that is also an equivalence 
 * relation on values of type <code>T</code>.  This relation is exposed as
 * the <code>lteq</code> method of the <code>PartialOrdering</code> trait.  
 * This relation must be:
 * <ul>
 * <li>reflexive: <code>lteq(x, x) == true</code>, for any <code>x</code> of 
 * type <code>T</code>.</li>
 * <li>anti-symmetric: <code>lteq(x, y) == true</code> and <code>lteq(y, x) == true</code>
 * then <code>equiv(x, y)</code>, for any <code>x</code> and <code>y</code> of 
 * type <code>T</code>.</li>
 * <li>transitive: if <code>lteq(x, y) == true</code> and <code>lteq(y, z) == true</code> 
 * then <code>lteq(x, z) == true</code>,  for any <code>x</code>, <code>y</code>,
 * and <code>z</code> of type <code>T</code>.</li>
 * </ul>
 *
 *  @author  Geoffrey Washburn
 *  @version 1.0, 2008-04-0-3
 */

trait PartialOrdering[T] extends Equiv[T] {
  /** Returns <code>true</code> iff <code>x</code> comes before 
   *  <code>y</code> in the ordering.
   */
  def lteq(x: T, y: T): Boolean

  /** Returns <code>true</code> iff <code>y</code> comes before
   *  <code>x</code> in the ordering. 
   */
  def gteq(x: T, y: T): Boolean = lteq(y, x)

  /** Returns <code>true</code> iff <code>x</code> comes before 
   *  <code>y</code> in the ordering and is not the same as <code>y</code>.
   */
  def lt(x: T, y: T): Boolean = lteq(x, y) && !equiv(x, y)

  /** Returns <code>true</code> iff <code>y</code> comes before 
   *  <code>x</code> in the ordering and is not the same as <code>x</code>.
   */
  def gt(x: T, y: T): Boolean = gteq(x, y) && !equiv(x, y)

  /** Returns <code>true</code> iff <code>x</code> is equivalent to
   *  <code>y</code> in the ordering. 
   */
  def equiv(x: T, y: T): Boolean = lteq(x,y) && lteq(y,x)
}
