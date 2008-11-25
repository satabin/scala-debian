/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2008, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://www.scala-lang.org/           **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Proxy.scala 13854 2008-01-31 14:30:29Z michelou $


package scala


import Predef._

/** This class implements a simple proxy that forwards all calls to
 *  methods of class <code>Any</code> to another object <code>self</code>.
 *  Please note that only those methods can be forwarded that are
 *  overridable and public.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 26/04/2004
 */
trait Proxy {
  def self: Any
  override def hashCode: Int = self.hashCode
  override def equals(that: Any): Boolean = that match {
    case that: Proxy => self equals that.self
    case that        => false
  }
  override def toString: String = self.toString
}
