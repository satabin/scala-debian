/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: DoubleRef.cs 16894 2009-01-13 13:09:41Z cunei $

namespace scala.runtime {

  using System;

  [Serializable]
  public class DoubleRef {
    public double elem;
    public DoubleRef(double elem) { this.elem = elem; }
    override public string ToString() { return elem.ToString(); }
  }

}
