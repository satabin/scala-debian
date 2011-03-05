/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2010, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

package scala.collection
package interfaces

import generic._
import mutable.Buffer
import scala.reflect.ClassManifest

/**
 * @since 2.8
 */
trait TraversableMethods[+A, +This <: TraversableLike[A, This] with Traversable[A]]
{
  // abstract
  def foreach[U](f: A => U): Unit
  
  // maps/iteration
  def flatMap[B, That](f: A => Traversable[B])(implicit bf: CanBuildFrom[This, B, That]): That
  def map[B, That](f: A => B)(implicit bf: CanBuildFrom[This, B, That]): That
  def collect[B, That](pf: PartialFunction[A, B])(implicit bf: CanBuildFrom[This, B, That]): That
  def scanLeft[B, That](z: B)(op: (B, A) => B)(implicit bf: CanBuildFrom[This, B, That]): That // could be fold or new collection too - where to put it?
  def scanRight[B, That](z: B)(op: (A, B) => B)(implicit bf: CanBuildFrom[This, B, That]): That
  
  // new collections
  def ++[B >: A, That](xs: TraversableOnce[B])(implicit bf: CanBuildFrom[This, B, That]): That
  def copyToArray[B >: A](xs: Array[B], start: Int): Unit
  def copyToArray[B >: A](xs: Array[B], start: Int, len: Int): Unit
  def copyToBuffer[B >: A](dest: Buffer[B]): Unit
  
  // conversions
  def toArray[B >: A : ClassManifest]: Array[B]
  def toIterable: Iterable[A]
  def toList: List[A]
  def toSeq: Seq[A]
  def toSet[B >: A]: immutable.Set[B]
  def toStream: Stream[A]
  def toIndexedSeq[B >: A]: immutable.IndexedSeq[B]
  def toBuffer[B >: A]: mutable.Buffer[B]
  
  // strings
  def addString(b: StringBuilder): StringBuilder
  def addString(b: StringBuilder, sep: String): StringBuilder
  def addString(b: StringBuilder, start: String, sep: String, end: String): StringBuilder
  def mkString(sep: String): String
  def mkString(start: String, sep: String, end: String): String
  def mkString: String
  
  // folds
  def /: [B](z: B)(op: (B, A) => B): B
  def :\ [B](z: B)(op: (A, B) => B): B
  def foldLeft[B](z: B)(op: (B, A) => B): B
  def foldRight[B](z: B)(op: (A, B) => B): B
  def reduceLeftOption[B >: A](op: (B, A) => B): Option[B]
  def reduceLeft[B >: A](op: (B, A) => B): B
  def reduceRightOption[B >: A](op: (A, B) => B): Option[B]
  def reduceRight[B >: A](op: (A, B) => B): B
  
  // conditions
  def exists(p: A => Boolean): Boolean
  def forall(p: A => Boolean): Boolean
  def hasDefiniteSize: Boolean
  def isEmpty: Boolean
  def nonEmpty: Boolean  
  
  // element retrieval
  def find(p: A => Boolean): Option[A]
  def head: A
  def headOption: Option[A]
  def last: A
  def lastOption: Option[A]
  
  // subcollections
  def drop(n: Int): Traversable[A]
  def dropWhile(p: A => Boolean): Traversable[A]
  def filter(p: A => Boolean): Traversable[A]
  def filterNot(p: A => Boolean): Traversable[A]
  def init: Traversable[A]
  def slice(from: Int, until: Int): Traversable[A]
  def tail: Traversable[A]
  def take(n: Int): Traversable[A]
  def takeWhile(p: A => Boolean): Traversable[A]
  
  // subdivisions
  def groupBy[K](f: A => K): Map[K, Traversable[A]]
  def partition(p: A => Boolean): (Traversable[A], Traversable[A])
  def span(p: A => Boolean): (Traversable[A], Traversable[A])
  def splitAt(n: Int): (Traversable[A], Traversable[A])

  // info
  def count(p: A => Boolean): Int
  def size: Int
  def stringPrefix: String

  // views
  def view: TraversableView[A, This]
  def view(from: Int, until: Int): TraversableView[A, This]

  // def sum[B >: A](implicit num: Numeric[B]): B    
  // def product[B >: A](implicit num: Numeric[B]): B   
  // def min[B >: A](implicit cmp: Ordering[B]): A
  // def max[B >: A](implicit cmp: Ordering[B]): A
}
