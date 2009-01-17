/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id: Repository.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.tools.nsc.backend.icode

import scala.collection._

/**
 *  @author Iulian Dragos
 */
trait Repository {
  val global: Global
  import global._
  import icodes._

  val loaded: mutable.Map[Symbol, IClass] = new mutable.HashMap

  /** Is the given class available as icode? */
  def available(sym: Symbol) = classes.contains(sym) || loaded.contains(sym)

  /** The icode of the given class, if available */
  def icode(sym: Symbol): Option[IClass] = 
    if (classes.contains(sym)) Some(classes(sym))
    else if (loaded.contains(sym)) Some(loaded(sym))
    else None

  /** The icode of the given class. If not available, it loads
   *  its bytecode.
   */
  def icode(sym: Symbol, force: Boolean): IClass = 
    if (available(sym)) icode(sym).get
    else {
      load(sym)
      assert(available(sym))
      loaded(sym)
    }

  /** Load bytecode for given symbol. */
  private def load(sym: Symbol) {
    val (c1, c2) = icodeReader.readClass(sym)
    
    assert(c1.symbol == sym || c2.symbol == sym)
    loaded += (c1.symbol -> c1)
    loaded += (c2.symbol -> c2)
  }
}
