/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id: CopyPropagation.scala 16881 2009-01-09 16:28:11Z cunei $

package scala.tools.nsc.backend.icode.analysis

import scala.collection.mutable.{Map, HashMap}
import scala.tools.nsc.symtab.Flags.DEFERRED

/** A modified copy-propagation like analysis. It 
 *  is augmented with a record-like value which is used
 *  to represent closures.
 *
 *  @author Iulian Dragos
 */
abstract class CopyPropagation {
  val global: Global
  import global._
  import icodes._

  /** Locations can be local variables, this, and fields. */
  abstract sealed class Location;
  case class LocalVar(l: Local) extends Location
  case class Field(r: Record, sym: Symbol) extends Location
  case object This extends Location

  /** Values that can be on the stack. */
  abstract class Value {
    def isRecord = false
  }
  case class Record(cls: Symbol, bindings: Map[Symbol, Value]) extends Value {
    override def isRecord = true
  }
  /** The value of some location in memory. */
  case class Deref(l: Location) extends Value
  
  /** The boxed value of some location. */
  case class Boxed(l: Location) extends Value
  
  /** The constant value c. */
  case class Const(c: Constant) extends Value
  
  /** Unknown. */
  case object Unknown extends Value
  
  /** The bottom record. */
  object AllRecords extends Record(NoSymbol, new HashMap[Symbol, Value])

  /** The lattice for this analysis.   */
  object copyLattice extends CompleteLattice {
    type Bindings = Map[Location, Value]

    def emptyBinding = new HashMap[Location, Value]()

    class State(val bindings: Bindings, var stack: List[Value]) {
      override def equals(that: Any): Boolean = 
        (this eq that.asInstanceOf[AnyRef]) ||
        that.isInstanceOf[State] && {
          val other = that.asInstanceOf[State]

          /* comparison with bottom is reference equality! */
          if ((other eq bottom) || (this eq bottom))
            (this eq other)
          else {
            this.bindings == other.bindings &&
            List.forall2(this.stack, other.stack) { (a, b) => a == b }
          }
        }

      /* Return an alias for the given local. It returns the last
       * local in the chain of aliased locals. Cycles are not allowed
       * to exist (by construction).
       */
      def getAlias(l: Local): Local = {
        var target = l
        var stop = false
        
        while (bindings.isDefinedAt(LocalVar(target)) && !stop) {
          bindings(LocalVar(target)) match {
            case Deref(LocalVar(t)) => target = t
            case _ => stop = true
          }
        }
        target
      }

      /* Return the value bound to the given local. */
      def getBinding(l: Local): Value = {
        var target = l
        var stop = false
        var value: Value = Deref(LocalVar(target))
        
        while (bindings.isDefinedAt(LocalVar(target)) && !stop) {
//          Console.println("finding binding for " + target)
          value = bindings(LocalVar(target))
          value match {
            case Deref(LocalVar(t)) => target = t
            case _ => stop = true
          }
        }
        value
      }

      /* Return the binding for the given field of the given record */
      def getBinding(r: Record, f: Symbol): Value = {
        assert(r.bindings.isDefinedAt(f),
               "Record " + r + " does not contain a field " + f);

        var target: Value = r.bindings(f);
        target match {
          case Deref(LocalVar(l)) => getBinding(l)
          case _ => target
        }
      }
      
      /** Return a local which contains the same value as this field, if any.
       * If the field holds a reference to a local, the returned value is the
       * binding of that local.
       */
      def getFieldValue(r: Record, f: Symbol): Option[Value] = {
        assert(r.bindings.isDefinedAt(f),
            "Record " + r + " does not contain a field " + f);

        var target: Value = r.bindings(f)
        target match {
          case Deref(LocalVar(l)) => Some(Deref(LocalVar(getAlias(l))))
          case Deref(This)    => Some(target)
          case Const(k) => Some(target)
          case _  => None
        }
      }

      override def toString(): String =
        "\nBindings: " + bindings + "\nStack: " + stack;

      def dup: State = {
        val b: Bindings = new HashMap()
        b ++= bindings
        new State(b, stack)
      }
    }

    type Elem = State

    val top    = new State(emptyBinding, Nil)
    val bottom = new State(emptyBinding, Nil)

    val exceptionHandlerStack = Unknown :: Nil

    def lub2(a: Elem, b: Elem): Elem = {
      if (a eq bottom)      b
      else if (b eq bottom) a
      else if (a == b) a
      else {
        val resStack = 
          if (a.stack eq exceptionHandlerStack) a.stack
          else if (b.stack eq exceptionHandlerStack) b.stack
          else {
            if (a.stack.length != b.stack.length)
              throw new LubError(a, b, "Invalid stacks in states: ");
            List.map2(a.stack, b.stack) { (v1, v2) => 
              if (v1 == v2) v1 else Unknown 
            }
          }
      
/*        if (a.stack.length != b.stack.length)
          throw new LubError(a, b, "Invalid stacks in states: ");
        val resStack = List.map2(a.stack, b.stack) { (v1, v2) => 
          if (v1 == v2) v1 else Unknown 
        }
        */
        val commonPairs = a.bindings.toList intersect (b.bindings.toList)
        val resBindings = new HashMap[Location, Value]
        for ((k, v) <- commonPairs)
          resBindings += (k -> v);
        new State(resBindings, resStack)
      }
    }
  }

  final class CopyAnalysis extends DataFlowAnalysis[copyLattice.type] {
    type P = BasicBlock
    val lattice = copyLattice

    var method: IMethod = _

    def init(m: IMethod) {
      this.method = m

      init {
        worklist += m.code.startBlock
        worklist ++= (m.exh map (_.startBlock))
        m.code.blocks.foreach { b =>
          in(b)  = lattice.bottom
          out(b) = lattice.bottom
          assert(out.contains(b))
          log("Added point: " + b)
        } 
        m.exh foreach { e =>
          in(e.startBlock) = new copyLattice.State(copyLattice.emptyBinding, copyLattice.exceptionHandlerStack);
        }

        // first block is special: it's not bottom, but a precisely defined state with no bindings
        in(m.code.startBlock) = new lattice.State(lattice.emptyBinding, Nil);
      }
    }

    override def run {
      forwardAnalysis(blockTransfer)
      if (settings.debug.value) {
        linearizer.linearize(method).foreach(b => if (b != method.code.startBlock)
          assert(in(b) != lattice.bottom, 
            "Block " + b + " in " + this.method + " has input equal to bottom -- not visited?"));
      }
    }

    def blockTransfer(b: BasicBlock, in: lattice.Elem): lattice.Elem =
      b.toList.foldLeft(in)(interpret)
    
    import opcodes._

    /** Abstract interpretation for one instruction. */
    def interpret(in: copyLattice.Elem, i: Instruction): copyLattice.Elem = {
      var out = in.dup

      if (settings.debug.value) {
        log("- " + i)
        log("in: " + in)
        log("\n")
      }

      i match {
        case THIS(_) =>
          out.stack = Deref(This) :: out.stack

        case CONSTANT(k) =>
          if (k.tag != UnitTag)
            out.stack = Const(k) :: out.stack;
          
        case LOAD_ARRAY_ITEM(_) =>
          out.stack = (Unknown :: out.stack.drop(2))

        case LOAD_LOCAL(local) =>
          out.stack = Deref(LocalVar(local)) :: out.stack

        case LOAD_FIELD(field, isStatic) =>
          if (isStatic)
            out.stack = Unknown :: out.stack; /* ignore static fields */
          else {
            val v1 = in.stack match {
              case (r @ Record(cls, bindings)) :: xs =>
                Deref(Field(r, field))

              case _ => Unknown
            }
            out.stack = v1 :: out.stack.drop(1)
          }
            
        case LOAD_MODULE(module) =>
          out.stack = Unknown :: out.stack
          
        case STORE_ARRAY_ITEM(kind) =>
          out.stack = out.stack.drop(3)

        case STORE_LOCAL(local) =>
          cleanReferencesTo(out, LocalVar(local))
          in.stack match {
            case Unknown :: xs => ()
            case v :: vs => 
              v match {
                case Deref(LocalVar(other)) =>
                  if (other != local) 
                    out.bindings += (LocalVar(local) -> v);
                case _ =>
                  out.bindings += (LocalVar(local) -> v)
              }
            case Nil =>
              Predef.error("Incorrect icode in " + method + ". Expecting something on the stack.")
          }
          out.stack = out.stack drop 1;
          
        case STORE_THIS(_) =>
          cleanReferencesTo(out, This)
          out.stack = out.stack drop 1
          
        case STORE_FIELD(field, isStatic) =>
          if (isStatic) 
            out.stack = out.stack.drop(1);
          else {
            out.stack = out.stack.drop(2);
            cleanReferencesTo(out, Field(AllRecords, field));
            in.stack match {
              case v :: Record(_, bindings) :: vs =>
                bindings += (field -> v)
              case _ => ();
            }
          }
          
        case CALL_PRIMITIVE(primitive) =>
          // TODO: model primitives
          out.stack = Unknown :: out.stack.drop(i.consumed)

        case CALL_METHOD(method, style) => style match {
          case Dynamic =>
            out = simulateCall(in, method, false)

          case Static(onInstance) =>
            if (onInstance) {
              val obj = out.stack.drop(method.info.paramTypes.length).head
//              if (method.isPrimaryConstructor) {
              if (method.isPrimaryConstructor/* && isClosureClass(method.owner)*/) {
                obj match {
                  case Record(_, bindings) =>
                    for (v <- out.stack.take(method.info.paramTypes.length + 1)
                         if v ne obj) {
                       bindings ++= getBindingsForPrimaryCtor(in, method);
                    }
                  case _ => ()
                }
                // put the Record back on the stack and remove the 'returned' value
                out.stack = out.stack.drop(1 + method.info.paramTypes.length)
              } else
                out = simulateCall(in, method, false)
            } else 
              out = simulateCall(in, method, true)

          case SuperCall(_) =>
            out = simulateCall(in, method, false)
        }
        
        case BOX(tpe) =>
          val top = out.stack.head
          top match {
            case Deref(loc) => 
              out.stack = Boxed(loc) :: out.stack.tail
            case _ =>
              out.stack = Unknown :: out.stack.drop(1)
          }

        case UNBOX(tpe) =>
          val top = out.stack.head
          top match {
            case Boxed(loc) => Deref(loc) :: out.stack.tail
            case _          => out.stack = Unknown :: out.stack.drop(1)
          }
          
        case NEW(kind) =>
          val v1 = 
            kind match {
              case REFERENCE(cls) =>
                Record(cls, new HashMap[Symbol, Value])
              // bq: changed from _ to null, otherwise would be unreachable
              case null =>
                Unknown
            }
          out.stack = v1 :: out.stack

        case CREATE_ARRAY(elem, dims) =>
          out.stack = Unknown :: out.stack.drop(dims)
          
        case IS_INSTANCE(tpe) =>
          out.stack = Unknown :: out.stack.drop(1)
          
        case CHECK_CAST(tpe) =>
          out.stack = Unknown :: out.stack.drop(1)
          
        case SWITCH(tags, labels) =>
          out.stack = out.stack.drop(1)
          
        case JUMP(whereto) =>
          ()
          
        case CJUMP(success, failure, cond, kind) =>
          out.stack = out.stack.drop(2)
          
        case CZJUMP(success, failure, cond, kind) =>
          out.stack = out.stack.drop(1)
          
        case RETURN(kind) =>
          if (kind != UNIT)
            out.stack = out.stack.drop(1)
          
        case THROW() =>
          out.stack = out.stack.drop(1)
          
        case DROP(kind) =>
          out.stack = out.stack.drop(1)
          
        case DUP(kind) =>
          out.stack = out.stack.head :: out.stack
          
        case MONITOR_ENTER() =>
          out.stack = out.stack.drop(1);         

        case MONITOR_EXIT() =>
          out.stack = out.stack.drop(1)

        case SCOPE_ENTER(_) | SCOPE_EXIT(_) =>
          ()
        
        case LOAD_EXCEPTION() =>
          out.stack = Unknown :: Nil

        case _ =>
          dump
          abort("Unknown instruction: " + i)
      }
      out
    } /* def interpret */

    /** Remove all references to this local variable from both stack
     *  and bindings. It is called when a new assignment destroys 
     *  previous copy-relations.
     */
    final def cleanReferencesTo(s: copyLattice.State, target: Location) {
      def cleanRecord(r: Record): Record = {
        r.bindings retain { (loc, value) =>
          value match {
            case Deref(loc1) if (loc1 == target) => false
            case Boxed(loc1) if (loc1 == target)  => false
            case _ => true
          }
        }
        r
      }

      s.stack = s.stack map { v => v match {
        case Record(_, bindings) =>
          cleanRecord(v.asInstanceOf[Record])
        case Boxed(loc1) if (loc1 == target) => Unknown
        case _ => v
      }}

      s.bindings retain { (loc, value) =>
        (value match {
          case Deref(loc1) if (loc1 == target) => false
          case Boxed(loc1) if (loc1 == target) => false
          case Record(_, _) => 
            cleanRecord(value.asInstanceOf[Record]);
            true
          case _ => true
        }) && 
        (loc match {
          case l: Location if (l == target) => false
          case _ => true
        })
      }
    }

    /** Update the state <code>s</code> after the call to <code>method</code>.
     *  The stack elements are dropped and replaced by the result of the call.
     *  If the method is impure, all bindings to record fields are cleared.
     *
     *  @param state  ...
     *  @param method ...
     *  @param static ...
     *  @return       ...
     */
    final def simulateCall(state: copyLattice.State, method: Symbol, static: Boolean): copyLattice.State = {
      val out = new copyLattice.State(state.bindings, state.stack);
      out.stack = out.stack.drop(method.info.paramTypes.length + (if (static) 0 else 1));
      if (method.info.resultType != definitions.UnitClass.tpe && !method.isConstructor)
        out.stack = Unknown :: out.stack;
      if (!isPureMethod(method))
        invalidateRecords(out);
      out
    }

    /** Drop everything known about mutable record fields.
     *
     *  @param state ...
     */
    final def invalidateRecords(state: copyLattice.State) {
      state.stack = state.stack map { v => v match {
        case Record(cls, bindings) =>
          bindings.retain { (sym: Symbol, v: Value) => !sym.hasFlag(symtab.Flags.MUTABLE) }
          Record(cls, bindings)
        case _ => v
      }}

      state.bindings retain {(loc, value) =>
        value match {
          case Deref(Field(_, _)) => false
          case Boxed(Field(_, _)) => false
          case _ => true
        }
      }
    }

    /** Return bindings from an object fields to the values on the stack. This
     *  method has to find the correct mapping from fields to the order in which
     *  they are passed on the stack. It works for primary constructors.
     */
    private def getBindingsForPrimaryCtor(in: copyLattice.State, ctor: Symbol): Map[Symbol, Value] = {
      val paramAccessors = ctor.owner.constrParamAccessors;
      var values = in.stack.take(1 + ctor.info.paramTypes.length).reverse.drop(1);
      val bindings = new HashMap[Symbol, Value];

      if (settings.debug.value) log("getBindings for: " + ctor + " acc: " + paramAccessors)
      
      var paramTypes = ctor.tpe.paramTypes 
      val diff = paramTypes.length - paramAccessors.length
      diff match {
        case 0 => ()
        case 1 if ctor.tpe.paramTypes.head == ctor.owner.rawowner.tpe =>
          // it's an unused outer
          log("considering unused outer at position 0 in " + ctor.tpe.paramTypes)
          paramTypes = paramTypes.tail
          values = values.tail
        case _ =>
          log("giving up on " + ctor + "(diff: " + diff + ")")
          return bindings
      }
      
      // this relies on having the same order in paramAccessors and
      // the arguments on the stack. It should be the same!
      for ((p, i) <- paramAccessors.zipWithIndex) {
//        assert(p.tpe == paramTypes(i), "In: " + ctor.fullNameString
//               + " having acc: " + (paramAccessors map (_.tpe))+ " vs. params" + paramTypes
//               + "\n\t failed at pos " + i + " with " + p.tpe + " == " + paramTypes(i))
        if (p.tpe == paramTypes(i))
          bindings += (p -> values.head);
        values = values.tail;
      }

      if (settings.debug.value) log("\t" + bindings)
      bindings
    }

    /** Is <code>cls</code> a closure class?
     *
     *  @param cls ...
     *  @return    ...
     */
    final def isClosureClass(cls: Symbol): Boolean = 
        cls.isFinal &&
        cls.tpe.parents.exists { t => 
          val TypeRef(_, sym, _) = t;
          definitions.FunctionClass exists sym.==
        }

    /** Is symbol <code>m</code> a pure method?
     *
     *  @param m ...
     *  @return  ...
     */
    final def isPureMethod(m: Symbol): Boolean = 
      m.isGetter // abstract getters are still pure, as we 'know'

    final override def toString(): String = {
      var res = ""
      for (b <- this.method.code.blocks.toList)
        res = (res + "\nIN(" + b.label + "):\t Bindings: " + in(b).bindings +
               "\nIN(" + b.label +"):\t Stack: " + in(b).stack) + "\n";
      res
    }

  } /* class CopyAnalysis */
}
