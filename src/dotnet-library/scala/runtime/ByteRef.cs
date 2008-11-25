/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ByteRef.cs 9945 2007-02-09 17:07:01Z mihaylov $

namespace scala.runtime {

  using System;

  [Serializable]
  public class ByteRef {
    public byte elem;
    public ByteRef(byte elem) { this.elem = elem; }
    override public string ToString() { return elem.ToString(); }
  }

}
