/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: RichClass.scala 14416 2008-03-19 01:17:25Z mihaylov $


package scala.runtime

import Predef.Class

final class RichClass(val self: Class[_]) extends Proxy {

  def isPrimitive(): Boolean = self.IsPrimitive
  def isArray(): Boolean = self.IsArray

  def getClass(): RichClass = this
  def getName(): String = self.Name
  def getComponentType(): Class[_] = self.GetElementType

}
