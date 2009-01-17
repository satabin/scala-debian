/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Ranged.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.collection.jcl;

/** Any collection (including maps) whose keys (or elements) are ordered.
 *
 *  @author Sean McDirmid
 */
trait Ranged[K,A] extends scala.collection.Ranged[K,A] with MutableIterable[A] {
  protected type SortedSelf <: Ranged[K,A];

  /** Comparison function that orders keys. */
  def compare(k0: K, k1: K): Int;

  /** Creates a ranged projection of this collection. Any mutations in the
   *  ranged projection will update this collection and vice versa.  Note: keys
   *  are not garuanteed to be consistent between this collection and the projection.
   *  This is the case for buffers where indexing is relative to the projection. 
   *
   *  @param from  The lower-bound (inclusive) of the ranged projection.
   *               <code>None</code> if there is no lower bound.
   *  @param until The upper-bound (exclusive) of the ranged projection.
   *               <code>None</code> if there is no upper bound.
   */
  def rangeImpl(from: Option[K], until: Option[K]) : SortedSelf;
  /** Creates a ranged projection of this collection with no upper-bound.
   ** @param from The lower-bound (inclusive) of the ranged projection.
   **/
  override final def from(from: K): SortedSelf = rangeImpl(Some(from), None);
  /** Creates a ranged projection of this collection with no lower-bound.
   ** @param until The upper-bound (exclusive) of the ranged projection.
   **/
  override final def until(until: K): SortedSelf = rangeImpl(None, Some(until));
  
  /** Creates a ranged projection of this collection with both a lower-bound and an upper-bound.
   ** @param from The upper-bound (exclusive) of the ranged projection.
   **/
  override final def range(from: K, until: K) : SortedSelf = rangeImpl(Some(from),Some(until));

  /** A wrapper around Java comparators. */
  protected class Comparator[K <% Ordered[K]] extends java.util.Comparator[Any] {
    def compare(x0 : Any, x1 : Any) = {
      x0.asInstanceOf[K].compare(x1.asInstanceOf[K]); //!!!
    }
  }
}
