/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Set.scala 14532 2008-04-07 12:23:22Z washburn $

package scala.collection.jcl

/** Analogous to a Java set.
 *
 *  @author Sean McDirmid
 */
trait Set[A] extends scala.collection.mutable.Set[A] with Collection[A] {
  final def contains(a : A) = has(a)

  /** Add will return false if "a" already exists in the set. **/
  override def add(a: A): Boolean

  override def ++(i: Iterable[A]) : this.type = super[Collection].++(i)
  override def --(i: Iterable[A]) : this.type = super[Collection].--(i)
  override def +(t: A) : this.type = super[Collection].+(t)
  override def -(t: A) : this.type = super[Collection].-(t)
  override final def retain(f: A => Boolean) = retainOnly(f)
  override def isEmpty = super[Collection].isEmpty
  override def clear() = super.clear()
  override def subsetOf(set : scala.collection.Set[A]) = set match {
    case set : Set[_] => set.hasAll(this)
    case set => super.subsetOf(set)
  }

  override def transform(f: A => A) = {
    var toAdd : List[A] = Nil
    val i = elements
    while (i.hasNext) {
      val i0 = i.next
      val i1 = f(i0)
      if (i0 != i1) {
        i.remove; toAdd = i1 :: toAdd
      }
    }
    addAll(toAdd)
  }
  class Filter(pp : A => Boolean) extends super.Filter with Set.Projection[A] {
    override def p(a : A) = pp(a)
    override def retainOnly(p0 : A => Boolean): Unit = 
      Set.this.retainOnly(e => !p(e) || p0(e))
    override def add(a : A) = {
      if (!p(a)) throw new IllegalArgumentException
      else Set.this.add(a)
    }
  }
  override def projection : Set.Projection[A] = new Set.Projection[A] {
    override def add(a: A): Boolean = Set.this.add(a)
    override def elements = Set.this.elements
    override def size = Set.this.size
    override def has(a : A) : Boolean = Set.this.has(a)
  }
}

object Set {
  trait Projection[A] extends Collection.Projection[A] with Set[A] {
    override def filter(p : A => Boolean) : Projection[A] = new Filter(p);
    override def projection = this
  }
  def apply[T](set : java.util.Set[T]) = new SetWrapper[T] {
    val underlying = set
  }
}
