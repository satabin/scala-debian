/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Tree.scala 14884 2008-05-02 14:55:17Z odersky $

/* General Balanced Trees - highly efficient functional dictionaries.
**
** This is a scala version of gb_trees.erl which is
** copyrighted (C) 1999-2001 by Sven-Olof Nystrom, and Richard Carlsson
**
** An efficient implementation of Prof. Arne Andersson's General
** Balanced Trees. These have no storage overhead compared to plain
** unbalanced binary trees, and their performance is in general better
** than AVL trees.
**
** NOTE: This code was until 2007-04-01 under a GPL license. The author has
** given permission to remove that license, so that now this code is under
** the general license for the Scala libraries. 
*/

package scala.collection.immutable


//import Predef.NoSuchElementException

/** <p>
 *    General Balanced Trees - highly efficient functional dictionaries.
 *  </p>
 *  <p>
 *    An efficient implementation of Prof. Arne Andersson's
 *    <a href="http://citeseer.ist.psu.edu/andersson99general.html"
 *    target="_top">General Balanced Trees</a>. These have no storage overhead
 *    compared to plain unbalanced binary trees, and their performance is in
 *    general better than AVL trees.
 *  </p>
 *  <p>
 *    This implementation does not balance the trees after deletions.
 *    Since deletions don't increase the height of a tree, this should
 *    be OK in most applications. A balance method is provided for those
 *    cases where rebalancing is needed.
 *  </p>
 *  <p>
 *    The tree consists of entries conatining a key with an order.
 *  </p>
 *  <p>
 *    When instanciating the tree an order for the keys has to be
 *    supplied.
 *  </p>
 *
 *  @author  Erik Stenman, Michel Schinz
 *  @version 1.1, 2005-01-20
 */

@serializable
abstract class Tree[A <% Ordered[A], B]() extends AnyRef {
  /* Data structure:
  ** - size:Int - the number of elements in the tree.
  ** - tree:T, which is composed of nodes of the form:
  **   - GBNode(key: A, entry:B, smaller:T, bigger:T),
  **   - and the "empty tree" node GBLeaf.
  **
  ** Original balance condition h(T) <= ceil(c * log(|T|)) has been
  ** changed to the similar (but not quite equivalent) condition
  ** 2 ^ h(T) <= |T| ^ c.
  **
  */

  /** The type returned when creating a new tree.
   *  This type should be defined by concrete implementations
   *  e.g. <pre>
   *  class C[T](...) extends Tree[A,B](...) {
   *    type This = C[T];
   *  </pre>
   */
  protected type This <: Tree[A,B]
  protected def getThis: This

  /**
   *  The type of nodes that the tree is build from.
   */
  protected type aNode = GBTree[A,B]

  /** The nodes in the tree.
   */
  protected def tree: aNode = GBLeaf[A,B]()

  /** <p>
   *    This abstract method should be defined by a concrete implementation
   *    <code>C[T]</code> as something like:
   *  </p>
   *  <pre>
   *    <b>override def</b> New(sz: Int, t: aNode): This {
   *      <b>new</b> C[T](order) {
   *        <b>override def</b> size = sz
   *        <b>override protected def</b> tree: aNode = t
   *    }
   *  </pre>
   *  <p>
   *     The concrete implementation should also override the def of This
   *     <code>override type This = C[T];</code>
   *  </p>
   */
  protected def New(sz: Int, t: aNode): This

  /** The size of the tree, returns 0 (zero) if the tree is empty.
   *
   *  @return The number of nodes in the tree as an integer.
   */
  def size: Int = 0

  /** A new tree with the entry added is returned,
   *  assuming that key is <em>not</em> in the tree.
   *
   *  @param key   ...
   *  @param entry ...
   *  @return      ...
   */
  protected def add(key: A, entry: B): This = {
    val newSize = size + 1
    New(newSize, tree.insert(key, entry, newSize * newSize).node)
  }

  /** A new tree with the entry added is returned,
   *  if key is <em>not</em> in the tree, otherwise
   *  the key is updated with the new entry.
   */
  protected def updateOrAdd(key: A, entry: B): This =
    if (tree.isDefinedAt(key))
      New(size,tree.update(key,entry))
    else
      add(key,entry)

  /** Removes the key from the tree.
   *
   *  @param key ...
   *  @return    ...
   */
  protected def deleteAny(key: A): This =
    if (tree.isDefinedAt(key))
      delete(key)
    else
      getThis

  /** Removes the key from the tree, assumimg that key is present.
   *
   *  @param key ...
   *  @return    ...
   */
  private def delete(key: A): This =
    New(size - 1, tree.delete(key))

  /** Check if this map maps <code>key</code> to a value and return the
   *  value if it exists.
   *
   *  @param  key the key of the mapping of interest
   *  @return     the value of the mapping, if it exists
   */
  protected def findValue(key: A): Option[B] =
    tree.get(key)

  /** Gives you an iterator over all elements in the tree.
   *  The iterator structure corresponds to
   *  the call stack of an in-order traversal.
   *
   *  Note: The iterator itself has a state, i.e., it is not functional.
   */
  protected def entries: Iterator[B] =
    new Iterator[B] {
      var iter = tree.mk_iter(scala.Nil)
      def hasNext = !iter.isEmpty
      def next = iter match {
        case GBNode(_,v,_,t)::iter_tail =>
          iter = t.mk_iter(iter_tail)
          v
        case scala.Nil =>
          throw new NoSuchElementException("next on empty iterator")
      }
    }

  /** Create a new balanced tree from the tree. Might be useful to call
   *  after many deletions, since deletion does not rebalance the tree.
   */
  def balance: This =
    New(size, tree.balance(size))
}

protected abstract class InsertTree[A <% Ordered[A],B]() extends AnyRef {
  def insertLeft(k: A, v: B, t: GBTree[A,B]): InsertTree[A,B]
  def insertRight(k: A, v: B, t: GBTree[A,B]): InsertTree[A,B]
  def node: GBTree[A,B]
}

/**
 *  <code>ITree</code> is an internal class used by
 *  <a href="Tree.html" target="contentFrame"><code>Tree</code></a>.
 */
private case class ITree[A <% Ordered[A],B](t: GBTree[A,B])
             extends InsertTree[A,B] {
  def insertLeft(key: A, value: B, bigger: GBTree[A,B]) =
    ITree(GBNode(key, value, t, bigger))
  def insertRight(key: A, value: B, smaller: GBTree[A,B]) =
    ITree(GBNode(key, value, smaller, t))
  def node = t
}

/**
 *  <code>INode</code> is an internal class used by
 *  <a href="Tree.html" target="contentFrame"><code>Tree</code></a>.
 */
private case class INode[A <% Ordered[A],B](t1: GBTree[A,B],
                                            height: Int,
                                            size: Int)
             extends InsertTree[A,B] {
  def insertLeft(key: A, value: B, bigger: GBTree[A,B]) =
    balance_p(GBNode(key, value, t1, bigger), bigger);
  def insertRight(key: A, value: B, smaller: GBTree[A,B]) =
    balance_p(GBNode(key, value, smaller, t1),smaller);
  protected def balance_p(t:GBTree[A,B],subtree:GBTree[A,B]):InsertTree[A,B] = {
    val (subHeight, subSize) = subtree.count
    val totalHeight = 2 * Math.max(height, subHeight)
    val totalSize = size + subSize + 1
    val BalanceHeight = totalSize * totalSize
    if (totalHeight > BalanceHeight) ITree(t.balance(totalSize))
    else INode(t, totalHeight, totalSize)
  }
  def node = t1
}

/**
 *  <code>GBTree</code> is an internal class used by
 *  <a href="Tree.html" target="contentFrame"><code>Tree</code></a>.
 *
 *  @author  Erik Stenman
 *  @version 1.0, 2005-01-20
 */
@serializable
protected abstract class GBTree[A <% Ordered[A],B] extends AnyRef {
  type aNode = GBTree[A,B]
  type anInsertTree = InsertTree[A,B]

  /** Calculates 2^h, and size, where h is the height of the tree
  *   and size is the number of nodes in the tree.
  */
  def count: (Int,Int)
  def isDefinedAt(Key: A): Boolean
  def get(key: A): Option[B]
  def apply(key: A): B
  def update(key: A, value: B): aNode
  def insert(key: A, value: B, size: Int): anInsertTree
  def toList(acc: List[(A,B)]): List[(A,B)]
  def mk_iter(iter_tail: List[aNode]): List[aNode]
  def delete(key: A): aNode
  def merge(t: aNode): aNode
  def takeSmallest: (A,B,aNode)
  def balance(s: Int): GBTree[A,B]
}

private case class GBLeaf[A <% Ordered[A],B]() extends GBTree[A,B] {
  def count = (1, 0)
  def isDefinedAt(key: A) = false
  def get(_key: A) = None
  def apply(key: A) = throw new NoSuchElementException("key " + key + " not found")
  def update(key: A, value: B) = throw new NoSuchElementException("key " + key + " not found")
  def insert(key: A, value: B, s: Int): anInsertTree = {
    if (s == 0)
      INode(GBNode(key, value, this, this), 1, 1)
    else
      ITree(GBNode(key, value, this, this))
  }
  def toList(acc: List[(A,B)]): List[(A,B)] = acc
  def mk_iter(iter_tail: List[GBTree[A,B]]) = iter_tail
  def merge(larger: GBTree[A,B]) = larger
  def takeSmallest: (A,B, GBTree[A,B]) =
    throw new NoSuchElementException("takeSmallest on empty tree")
  def delete(_key: A) = throw new NoSuchElementException("Delete on empty tree.")
  def balance(s: Int) = this
  override def hashCode() = 0
}

private case class GBNode[A <% Ordered[A],B](key: A,
                                             value: B,
                                             smaller: GBTree[A,B],
                                             bigger: GBTree[A,B])
             extends GBTree[A,B] {
  def count: (Int,Int) = {
    val (sHeight, sSize) = smaller.count
    val (bHeight, bSize) = bigger.count
    val mySize = sSize + bSize + 1
    if (mySize == 1)
      (1, mySize)
    else
      (2 * Math.max(sHeight, bHeight), mySize)
  }

  def isDefinedAt(sKey: A): Boolean =
    if (sKey < key) smaller.isDefinedAt(sKey)
    else if (sKey > key) bigger.isDefinedAt(sKey)
    else true

  def get(sKey: A): Option[B] =
    if (sKey < key) smaller.get(sKey)
    else if (sKey > key) bigger.get(sKey)
    else Some(value)

  def apply(sKey: A): B =
    if (sKey < key) smaller.apply(sKey)
    else if (sKey > key) bigger.apply(sKey)
    else value

  def update(newKey: A, newValue: B): aNode =
    if (newKey < key)
      GBNode(key, value, smaller.update(newKey,newValue), bigger)
    else if (newKey > key)
      GBNode(key, value, smaller, bigger.update(newKey,newValue))
    else
      GBNode(newKey, newValue, smaller, bigger)

  def insert(newKey: A, newValue: B, s: Int): anInsertTree = {
    if (newKey < key)
      smaller.insert(newKey, newValue, s / 2).insertLeft(key, value, bigger)
    else if (newKey > key)
      bigger.insert(newKey, newValue, s / 2).insertRight(key, value, smaller)
    else
      throw new NoSuchElementException("Key exists: " + newKey)
  }

  def toList(acc: List[(A,B)]): List[(A,B)] =
    smaller.toList((key, value) :: bigger.toList(acc))

  def mk_iter(iter_tail:List[aNode]):List[aNode] =
    smaller.mk_iter(this :: iter_tail)

  def delete(sKey:A):aNode = {
    if (sKey < key)
      GBNode(key, value, smaller.delete(sKey), bigger)
    else if (sKey > key)
      GBNode(key, value, smaller, bigger.delete(sKey))
    else
      smaller.merge(bigger)
  }

  def merge(larger: aNode): GBTree[A,B] = larger match {
    case GBLeaf() =>
      this
    case _ =>
      val (key1, value1, larger1) = larger.takeSmallest
      GBNode(key1, value1, this, larger1)
  }

  def takeSmallest: (A, B, aNode) = smaller match {
    case GBLeaf() =>
      (key, value, bigger)
    case _ =>
      val (key1, value1, smaller1) = smaller.takeSmallest
      (key1, value1, GBNode(key, value, smaller1, bigger))
  }

  /**
   *  @param s ...
   *  @return  ...
   */
  def balance(s: Int): GBTree[A,B] =
    balance_list(toList(scala.Nil), s)

  protected def balance_list(list: List[(A,B)], s: Int): GBTree[A,B] = {
    val empty = GBLeaf[A,B]();
    def bal(list: List[(A,B)], s: Int): (aNode, List[(A,B)]) = {
      if (s > 1) {
        val sm = s - 1
        val s2 = sm / 2
        val s1 = sm - s2
        val (t1, (k, v) :: l1) = bal(list, s1)
        val (t2, l2) = bal(l1, s2)
        val t = GBNode(k, v, t1, t2)
        (t, l2)
      } else if (s == 1) {
        val (k,v) :: rest = list
        (GBNode(k, v, empty, empty), rest)
      } else
        (empty, list)
    }
    bal(list, s)._1
  }

  override def hashCode() =
    value.hashCode() + smaller.hashCode() + bigger.hashCode()
}

/* Here is the e-mail where the Author agreed to the change in license.

from	Erik Stenman <happi.stenman@gmail.com>
to	martin odersky <martin.odersky@epfl.ch>,
date	Tue, Apr 29, 2008 at 3:31 PM
subject	Re: test
mailed-by	chara.epfl.ch
signed-by	gmail.com
	
Hi Martin,

I am fine with that change, and since I don't have a scala distribution handy,
I am also fine with you doing the change yourself. Is that OK?

Sorry for my dead home address, I'll add an English response to it at some time.

I am doing fine, and my family is also doing fine.
Hope all is well with you too.

Cheers,
Erik
- Hide quoted text -

On Tue, Apr 29, 2008 at 3:13 PM, martin odersky <martin.odersky@epfl.ch> wrote:

    Hi Erik,

    I tried to send mail to happi@home.se, but got a response n swedish. I
    was sort of guessing from the response that it contained an
    alternative e-mail address and tried to send it there.

    Anyway,  I hope things are going well with you!

    There was some discussion recently about the license of Tree.scala in
    package collection.immutable. It's GPL, whereas the rest of the Scala
    library is BSD. It seems this poses problems with Scala being packaged
    with Fedora. Would it be OK with you to change the license to the
    general one of Scala libraries? You could simply remove the references
    to the GPL
    license in the code and send it back to me if that's OK with you. On
    the other hand, if there's a problem we'll try something else instead.

    All the best

     -- Martin
*/
