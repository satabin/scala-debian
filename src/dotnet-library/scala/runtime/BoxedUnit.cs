/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: BoxedUnit.cs 9945 2007-02-09 17:07:01Z mihaylov $


namespace scala.runtime {

  using System;

  [Serializable]
  public sealed class BoxedUnit {

    public static readonly BoxedUnit UNIT = new BoxedUnit();

    private BoxedUnit() { }

    override public bool Equals(object other) {
      return this == other;
    }

    override public int GetHashCode() {
      return 0;
    }

    override public string ToString() {
      return "()";
    }
  }

}
