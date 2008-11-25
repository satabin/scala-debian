/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SymtabAttribute.cs 9945 2007-02-09 17:07:01Z mihaylov $

namespace scala.runtime {

  using System;

  public class SymtabAttribute : Attribute {
    public byte[] symtab;

    public SymtabAttribute(byte[] symtab) { this.symtab = symtab; }
    public SymtabAttribute() {}
  }

}
