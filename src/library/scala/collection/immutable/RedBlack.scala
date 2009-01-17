/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RedBlack.scala 16894 2009-01-13 13:09:41Z cunei $
package scala.collection.immutable

@serializable
abstract class RedBlack[A] {

  def isSmaller(x: A, y: A): Boolean

  private def blacken[B](t: Tree[B]): Tree[B] = t match {
    case RedTree(k, v, l, r) => BlackTree(k, v, l, r)
    case t => t
  }
  private def mkTree[B](isBlack: Boolean, k: A, v: B, l: Tree[B], r: Tree[B]) = 
    if (isBlack) BlackTree(k, v, l, r) else RedTree(k, v, l, r)
    
  @serializable
  abstract class Tree[+B] {
    def isEmpty: Boolean
    def isBlack: Boolean
    def lookup(x: A): Tree[B]
    def update[B1 >: B](k: A, v: B1): Tree[B1] = blacken(upd(k, v))
    def delete(k: A): Tree[B] = del(k)

    def visit[T](input : T)(f : (T,A,B) => Tuple2[Boolean,T]) : Tuple2[Boolean,T];
    def elements : ImmutableIterator[Pair[A,B]];
    def elementsSlow: Iterator[Pair[A, B]];
    def upd[B1 >: B](k: A, v: B1): Tree[B1]
    def del(k: A): Tree[B]
    def smallest: NonEmpty[B]
    def range(from : Option[A], until : Option[A]) : Tree[B]
    def first : A
    def last : A
    def count : Int
  }
  @serializable
  abstract class NonEmpty[+B] extends Tree[B] {
    def isEmpty = false
    def key: A
    def value: B
    def left: Tree[B]
    def right: Tree[B]
    def lookup(k: A): Tree[B] = 
      if (isSmaller(k, key)) left.lookup(k)
      else if (isSmaller(key, k)) right.lookup(k)
      else this
    def upd[B1 >: B](k: A, v: B1): Tree[B1] = {
      def balanceLeft(isBlack: Boolean, z: A, zv: B, l: Tree[B1], d: Tree[B1]) = l match {
        case RedTree(y, yv, RedTree(x, xv, a, b), c) => 
          RedTree(y, yv, BlackTree(x, xv, a, b), BlackTree(z, zv, c, d))
        case RedTree(x, xv, a, RedTree(y, yv, b, c)) =>
          RedTree(y, yv, BlackTree(x, xv, a, b), BlackTree(z, zv, c, d))
        case _ =>
          mkTree(isBlack, z, zv, l, d)
      }
      def balanceRight(isBlack: Boolean, x: A, xv: B, a: Tree[B1], r: Tree[B1]) = r match {
        case RedTree(z, zv, RedTree(y, yv, b, c), d) => 
          RedTree(y, yv, BlackTree(x, xv, a, b), BlackTree(z, zv, c, d))
        case RedTree(y, yv, b, RedTree(z, zv, c, d)) =>
          RedTree(y, yv, BlackTree(x, xv, a, b), BlackTree(z, zv, c, d))
        case _ =>
          mkTree(isBlack, x, xv, a, r)
      }
      if (isSmaller(k, key)) balanceLeft(isBlack, key, value, left.upd(k, v), right)
      else if (isSmaller(key, k)) balanceRight(isBlack, key, value, left, right.upd(k, v))
      else mkTree(isBlack, k, v, left, right)
    }
    def del(k: A): Tree[B] = {
      if (isSmaller(k, key)) mkTree(isBlack, key, value, left.del(k), right)
      else if (isSmaller(key, k)) mkTree(isBlack, key, value, left, right.del(k))
      else if (left.isEmpty) right
      else if (right.isEmpty) left
      else {
        val s = right.smallest
        mkTree(isBlack, s.key, s.value, left, right.del(s.key))
      }
    }
    def smallest: NonEmpty[B] = if (left.isEmpty) this else left.smallest
    def elements : ImmutableIterator[Pair[A,B]] = 
      left.elements.append(Pair(key,value), () => right.elements)

    def elementsSlow: Iterator[Pair[A, B]] = 
      left.elementsSlow append Iterator.single(Pair(key, value)) append right.elementsSlow
      
    def visit[T](input : T)(f : (T,A,B) => Tuple2[Boolean,T]) : Tuple2[Boolean,T] = {
      val left = this.left.visit(input)(f)
      if (!left._1) return left
      val middle = f(left._2, key, value)
      if (!middle._1) return middle
      return this.right.visit(middle._2)(f)
    }
   override def range(from : Option[A], until : Option[A]) : Tree[B] = {
      if (from == None && until == None) return this
      if (from != None && isSmaller(key, from.get)) return right.range(from, until);
      if (until != None && (isSmaller(until.get,key) || !isSmaller(key,until.get)))
        return left.range(from, until);
      val newLeft = left.range(from, None)
      val newRight = right.range(None, until)
      if ((newLeft eq left) && (newRight eq right)) this
      else if (newLeft eq Empty) newRight.upd(key, value);
      else if (newRight eq Empty) newLeft.upd(key, value);
      else mkTree(isBlack, key, value, newLeft, newRight)
    }
    def first = if (left .isEmpty) key else left.first
    def last  = if (right.isEmpty) key else right.last
    def count = 1 + left.count + right.count
  }
  @serializable
  case object Empty extends Tree[Nothing] {
    def isEmpty = true
    def isBlack = true
    def lookup(k: A): Tree[Nothing] = this
    def upd[B](k: A, v: B): Tree[B] = RedTree(k, v, Empty, Empty)
    def del(k: A): Tree[Nothing] = this
    def smallest: NonEmpty[Nothing] = throw new NoSuchElementException("empty map")
    def elementsSlow: Iterator[Pair[A, Nothing]] = Iterator.empty
    def elements : ImmutableIterator[Pair[A,Nothing]] = ImmutableIterator.empty
    def visit[T](input : T)(f : (T,A,Nothing) => Tuple2[Boolean,T]) = Tuple2(true,input)

    def range(from : Option[A], until : Option[A]) = this
    def first = throw new NoSuchElementException("empty map")
    def last = throw new NoSuchElementException("empty map")
    def count = 0
  }
  @serializable
  case class RedTree[+B](override val key: A,
                         override val value: B,
                         override val left: Tree[B],
                         override val right: Tree[B]) extends NonEmpty[B] {
    def isBlack = false
  }
  @serializable
  case class BlackTree[+B](override val key: A,
                           override val value: B,
                           override val left: Tree[B], 
                           override val right: Tree[B]) extends NonEmpty[B] {
    def isBlack = true
  }
}

