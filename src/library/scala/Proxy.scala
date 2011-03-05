/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2010, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://www.scala-lang.org/           **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */



package scala


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
  override def hashCode: Int = self.##
  override def equals(that: Any): Boolean = 
    if(that == null) false 
    else that equals self
  override def toString: String = self.toString
}
