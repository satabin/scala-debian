/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Ranged.scala 11110 2007-05-21 12:40:17Z mcdirmid $

package scala.collection

/** Any collection (including maps) whose keys (or elements) are ordered.
 *
 *  @author Sean McDirmid
 */
trait Ranged[K, +A] extends Iterable[A] {
  //protected type SortedSelf <: Ranged[K,A];

  /** Returns the first key of the collection. */
  def firstKey: K

  /** Returns the last key of the collection. */
  def lastKey: K

  /** Comparison function that orders keys. */
  def compare(k0: K, k1: K): Int
 
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
  def rangeImpl(from: Option[K], until: Option[K]): Ranged[K, A]

  /** Creates a ranged projection of this collection with no upper-bound.
   *
   *  @param from The lower-bound (inclusive) of the ranged projection.
   */
  def from(from: K) = rangeImpl(Some(from), None)

  /** Creates a ranged projection of this collection with no lower-bound.
   *
   *  @param until The upper-bound (exclusive) of the ranged projection.
   */
  def until(until: K) = rangeImpl(None, Some(until))
  
  /** Creates a ranged projection of this collection with both a lower-bound
   *  and an upper-bound.
   *
   *  @param from  The upper-bound (exclusive) of the ranged projection.
   *  @param until ...
   *  @return      ...
   */
  def range(from: K, until: K) = rangeImpl(Some(from), Some(until))
}
