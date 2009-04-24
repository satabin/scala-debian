//############################################################################
// Lists
//############################################################################
// $Id: lists.scala 17137 2009-02-17 20:09:49Z rytz $

//############################################################################

import testing.SUnit._

/** Test the Scala implementation of class <code>scala.List</code>.
 *
 *  @author Stephane Micheloud
 */
object Test extends TestConsoleMain {
  def suite = new TestSuite(
    Test1, //count, exists, filter, ..
    Test2, //#468
    Test3, //#1691
    Test4  //#1721
  )
}

object Test1 extends TestCase("ctor") with Assert {
  override def enableStackTrace = false
  override def runTest {
    val xs1 = List(1, 2, 3)
    val xs2 = List('a', 'b')
    val xs3 = List(List(1, 2), List(4, 5))
    val xs4 = List(2, 4, 6, 8)
    val xs5 = List(List(3, 4), List(3), List(4, 5))

  {
    val n1 = xs1 count { e => e % 2 != 0 }
    val n2 = xs4 count { e => e < 5 }
    assertEquals("check_count", 4, n1 + n2)
  }
  /* deprecated
  {
    val ys1 = xs1 diff xs4
    val ys2 = xs3 diff xs5
    assertEquals("check_diff", 3, ys1.length + ys2.length)
  }*/
  {
    val b1 = xs1 exists { e => e % 2 == 0 }
    val b2 = xs4 exists { e => e == 5 }
    assertEquals("check_exists", false , b1 & b2)
  }
  {
    val ys1 = xs1 filter { e => e % 2 == 0 }
    val ys2 = xs4 filter { e => e < 5 }
    assertEquals("check_filter", 3, ys1.length + ys2.length)
  }
  {
    val n1 = xs1.foldLeft(0)((e1, e2) => e1 + e2)
    val ys1 = xs4.foldLeft(List[Int]())((e1, e2) => e2 :: e1)
    assertEquals("check_foldLeft", 10, n1 + ys1.length)
  }
  {
    val b1 = xs1 forall { e => e < 10}
    val b2 = xs4 forall { e => e % 2 == 0 }
    assertEquals("check_forall", true, b1 & b2)
  }
  {
    val ys1 = xs1 intersect xs4
    val ys2 = xs3 intersect xs5
    assertEquals("check_intersect", 2, ys1.length + ys2.length)
  }
  {
    val ys1 = xs1 remove { e => e % 2 != 0 }
    val ys2 = xs4 remove { e => e < 5 }
    assertEquals("check_remove", 3, ys1.length + ys2.length)
  }
  {
    val ys1 = xs1 union xs4
    val ys2 = xs3 union xs5
    assertEquals("check_union", 10, ys1.length + ys2.length)
  }
  {
    val ys1 = xs1 zip xs2
    val ys2 = xs1 zip xs3
    assertEquals("check_zip", 4, ys1.length + ys2.length)
  }
  {
    val ys1 = xs1.zipAll(xs2, 0, '_')
    val ys2 = xs2.zipAll(xs1, '_', 0)
    val ys3 = xs1.zipAll(xs3, 0, List(-1))
    assertEquals("check_zipAll", 9, ys1.length + ys2.length + ys3.length)
  }
  }
}

object Test2 extends TestCase("t0468") with Assert {
  override def enableStackTrace = false
  override def runTest {
    val xs1 = List(1, 2, 3)
    val xs2 = List(0)
 
    val ys1 = xs1 ::: List(4)
    assertEquals("check_+", List(1, 2, 3, 4), ys1)

    val ys2 = ys1 - 4
    assertEquals("check_-", xs1, ys2)

    val n2 = (xs1 ++ ys1).length
    val n3 = (xs1 ++ Nil).length
    val n4 = (xs1 ++ ((new collection.mutable.ArrayBuffer[Int]) + 0)).length
    assertEquals("check_++", 14, n2 + n3 + n4)
  }
}

object Test3 extends TestCase("t1691") with Assert {
  override def enableStackTrace = false
  override def runTest {
    try {
      List.range(1, 10, 0)
    } catch {
      case e: IllegalArgumentException => ()
      case _ => throw new Error("List.range(1, 10, 0)")
    }
    try {
      List.range(1, 10, x => 4)
    } catch {
      case e: IllegalArgumentException => ()
      case _ => throw new Error("List.range(1, 10, x => 4)")
    }
    assertEquals(List.range(10, 0, x => x - 2),
		 List(10, 8, 6, 4, 2))
  }
}

object Test4 extends TestCase("t1721") with Assert {
  override def enableStackTrace = false
  override def runTest {
    assertTrue(List(1,2,3).endsWith(List(2,3)))
    assertFalse(List(1,2,3).endsWith(List(1,3)))
    assertTrue(List(1,2,3).endsWith(List()))
    assertFalse(List(1,2,3).endsWith(List(0,1,2,3)))
    assertTrue(List(1,2,3).endsWith(List(1,2,3)))
    assertFalse(List().endsWith(List(1,2,3)))
    assertTrue(List().endsWith(List()))
  }
}
