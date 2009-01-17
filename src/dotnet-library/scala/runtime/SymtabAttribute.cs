/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SymtabAttribute.cs 16894 2009-01-13 13:09:41Z cunei $

namespace scala.runtime {

  using System;

  public class SymtabAttribute : Attribute {
    public byte[] symtab;

    public SymtabAttribute(byte[] symtab) { this.symtab = symtab; }
    public SymtabAttribute() {}
  }

}
