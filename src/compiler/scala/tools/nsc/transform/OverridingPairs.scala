/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author Martin Odersky
 */
// $Id: OverridingPairs.scala 13220 2007-11-09 15:03:13Z odersky $

package scala.tools.nsc.transform

import collection.mutable.HashMap
import symtab.Flags._
import util.HashSet

/** This abstract class ...
 *
 *  @author Martin Odersky
 *  @version 1.0
 */
abstract class OverridingPairs {

  val global: Global
  import global._

  class Cursor(base: Symbol) {

    private val self = base.thisType

    protected def exclude(sym: Symbol): Boolean = 
      sym.isConstructor || sym.isPrivateLocal || sym.hasFlag(BRIDGE)

    protected def parents: List[Type] = base.info.parents

    protected def matches(sym1: Symbol, sym2: Symbol): Boolean =
      sym1.isType || (self.memberType(sym1) matches self.memberType(sym2))

    private type BitSet = Array[Int]

    private def newBitSet(size: Int): BitSet = new Array((size + 31) >> 5)

    private def include(bs: BitSet, n: Int) {
      val nshifted = n >> 5
      val nmask = 1 << (n & 31)
      bs(nshifted) = bs(nshifted) | nmask
    }

    private def intersectionContainsElementLeq(bs1: BitSet, bs2: BitSet,
                                               n: Int): Boolean =
    {
      val nshifted = n >> 5
      val nmask = 1 << (n & 31);
      ((List.range(0, nshifted) exists (i => (bs1(i) & bs2(i)) != 0)) ||
       ((bs1(nshifted) & bs2(nshifted) & (nmask | nmask - 1)) != 0))
    }

    private val decls = newScope

    { def fillDecls(bcs: List[Symbol], deferredflag: Int) {
        if (!bcs.isEmpty) {
          fillDecls(bcs.tail, deferredflag)
          var e = bcs.head.info.decls.elems;
          while (e ne null) {
            if (e.sym.getFlag(DEFERRED) == deferredflag && !exclude(e.sym))
              decls enter e.sym;
            e = e.next
          }
        }
      }
      fillDecls(base.info.baseClasses, DEFERRED)
      fillDecls(base.info.baseClasses, 0)
    }

    private val size = base.info.baseClasses.length

    private val index = new HashMap[Symbol, Int]

    { var i = 0
      for (bc <- base.info.baseClasses) {
        index(bc) = i
        i += 1
      }
    }

    private val subParents = new Array[BitSet](size)

    { for (i <- List.range(0, size))
        subParents(i) = new BitSet(size);
      for (p <- parents) {
        val pIndex = index(p.typeSymbol)
        for (bc <- p.baseClasses) include(subParents(index(bc)), pIndex)
      }
    }


    private def hasCommonParent(sym1: Symbol, sym2: Symbol) = {
      //assert(index.get(sym1.owner) != None, "" + base + " " + sym1 + " " + sym1.owner);//DEBUG
      //assert(index.get(sym2.owner) != None, "" + base + " " + sym2 + " " + sym2.owner);//DEBUG
      val index1 = index(sym1.owner)
      val index2 = index(sym2.owner)
      val minindex = if (index1 < index2) index1 else index2
      intersectionContainsElementLeq(subParents(index1), subParents(index2), minindex)
    }

    private val visited = new HashSet[ScopeEntry](256)
    private var curEntry = decls.elems
    private var nextEntry = curEntry

    var overriding: Symbol = _
    var overridden: Symbol = _

    //@M: note that next is called once during object initialisation
    def hasNext: Boolean = curEntry ne null

    def next {
      if (curEntry ne null) {
        overriding = curEntry.sym
        if (nextEntry ne null) {
          do {
            nextEntry = decls.lookupNextEntry(nextEntry);
          } while ((nextEntry ne null) &&
                   ((nextEntry.sym hasFlag PRIVATE) ||
                    (overriding.owner == nextEntry.sym.owner) ||
                    (!matches(overriding, nextEntry.sym)) ||
                    (hasCommonParent(overriding, nextEntry.sym)) ||
                    (exclude(overriding))))
        }
        if (nextEntry ne null) {
          overridden = nextEntry.sym;
          //Console.println("yield: " + overriding + overriding.locationString + " / " + overridden + overridden.locationString);//DEBUG
          visited addEntry nextEntry
        } else {
          do {
            curEntry = curEntry.next
          } while ((curEntry ne null) && (visited contains curEntry));
          nextEntry = curEntry
          next
        }
      }
    }

    next //@M: ATTN! this method gets called during construction!
  }
}
