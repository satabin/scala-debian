/* NSC -- new Scala compiler
 * Copyright 2005-2008 LAMP/EPFL
 * @author  Iulian Dragos
 */

// $Id: Inliners.scala 16511 2008-11-06 17:26:52Z dragos $

package scala.tools.nsc.backend.opt

import scala.collection.mutable.{Map, HashMap, Set, HashSet}
import scala.tools.nsc.symtab._

/**
 *  @author Iulian Dragos
 */
abstract class Inliners extends SubComponent {
  import global._
  import icodes._
  import icodes.opcodes._

  val phaseName = "inliner"

  /** The maximum size in basic blocks of methods considered for inlining. */
  final val MAX_INLINE_SIZE = 16
  
  /** Create a new phase */
  override def newPhase(p: Phase) = new InliningPhase(p)

  /** The Inlining phase.
   */
  class InliningPhase(prev: Phase) extends ICodePhase(prev) {
    def name = phaseName
    val inliner = new Inliner

    override def apply(c: IClass) {
      inliner.analyzeClass(c)
    }
  }

  /** 
   * Simple inliner.
   *
   */
  class Inliner {

    val fresh = new HashMap[String, Int]
    
    /* fresh name counter */
    var count = 0
    
    def freshName(s: String) = fresh.get(s) match {
      case Some(count) =>
        fresh(s) = count + 1
        s + count
      case None =>
        fresh(s) = 1
        s + "0"
    }
    
    lazy val ScalaInlineAttr   = definitions.getClass("scala.inline")
    lazy val ScalaNoInlineAttr = definitions.getClass("scala.noinline")

    /** Inline the 'callee' method inside the 'caller' in the given 
     *  basic block, at the given instruction (which has to be a CALL_METHOD).
     */
    def inline(caller: IMethod,
               block:  BasicBlock,
               instr:  Instruction,
               callee: IMethod) {
       log("Inlining " + callee + " in " + caller + " at pos: " + 
           (try {
             instr.pos.offset.get             
           } catch {
             case _ => "<nopos>"
           }));

       val targetPos = instr.pos
       val a = new analysis.MethodTFA(callee)

       /* The exception handlers that are active at the current block. */
       val activeHandlers = caller.exh.filter(_.covered.contains(block))

       /* Map 'original' blocks to the ones inlined in the caller. */
       val inlinedBlock: Map[BasicBlock, BasicBlock] = new HashMap
       
       val varsInScope: Set[Local] = new HashSet[Local] ++ block.varsInScope.elements

       val instrBefore = block.toList.takeWhile {
         case i @ SCOPE_ENTER(l) => varsInScope += l
           i ne instr
         case i =>
           i ne instr
       }
       val instrAfter  = block.toList.drop(instrBefore.length + 1);

       assert(!instrAfter.isEmpty, "CALL_METHOD cannot be the last instrcution in block!");

       // store the '$this' into the special local
       val inlinedThis = new Local(caller.symbol.newVariable(instr.pos, freshName("$inlThis")), REFERENCE(definitions.ObjectClass), false);

       /** buffer for the returned value */
       val retVal = 
         if (callee.returnType != UNIT)
           new Local(caller.symbol.newVariable(instr.pos, freshName("$retVal")), callee.returnType, false);
         else 
           null;

       /** Add a new block in the current context. */
       def newBlock = {
         val b = caller.code.newBlock
         activeHandlers.foreach (_.addCoveredBlock(b))
         if (retVal ne null) b.varsInScope += retVal
         b.varsInScope += inlinedThis
         b.varsInScope ++= varsInScope
         b
       }

       def translateExh(e: ExceptionHandler) = {
         var handler: ExceptionHandler = e.dup
         handler.covered = handler.covered.map(inlinedBlock)
         handler.setStartBlock(inlinedBlock(e.startBlock))
         handler
       }
       
       var inlinedLocals: Map[Local, Local] = new HashMap
       
       /** alfa-rename `l' in caller's context. */
       def dupLocal(l: Local): Local = {
         val sym = caller.symbol.newVariable(l.sym.pos, freshName(l.sym.name.toString()));
//         sym.setInfo(l.sym.tpe);
         val dupped = new Local(sym, l.kind, false)
         inlinedLocals(l) = dupped
         dupped
       }

       def addLocals(m: IMethod, ls: List[Local]) = 
         m.locals = m.locals ::: ls;
       def addLocal(m: IMethod, l: Local): Unit = 
         addLocals(m, List(l));

       val afterBlock = newBlock;
       
       /** Map from nw.init instructions to their matching NEW call */
       val pending: collection.jcl.Map[Instruction, NEW] = new collection.jcl.HashMap
       
       /** Map an instruction from the callee to one suitable for the caller. */
       def map(i: Instruction): Instruction = {
         val newInstr = i match {
           case THIS(clasz) =>
             LOAD_LOCAL(inlinedThis);
             
           case STORE_THIS(_) =>
             STORE_LOCAL(inlinedThis)

           case JUMP(whereto) =>
             JUMP(inlinedBlock(whereto));

           case CJUMP(success, failure, cond, kind) =>
             CJUMP(inlinedBlock(success), inlinedBlock(failure), cond, kind);

           case CZJUMP(success, failure, cond, kind) =>
             CZJUMP(inlinedBlock(success), inlinedBlock(failure), cond, kind);

           case SWITCH(tags, labels) =>
             SWITCH(tags, labels map inlinedBlock);

           case RETURN(kind) =>
             JUMP(afterBlock);

           case LOAD_LOCAL(l) if inlinedLocals.isDefinedAt(l) => 
             LOAD_LOCAL(inlinedLocals(l))

           case STORE_LOCAL(l) if inlinedLocals.isDefinedAt(l) => 
             STORE_LOCAL(inlinedLocals(l))
             
           case LOAD_LOCAL(l) =>
             assert(caller.locals contains l, 
                 "Could not find local '" + l + "' in locals, nor in inlinedLocals: " + inlinedLocals)
             i
           case STORE_LOCAL(l) =>
             assert(caller.locals contains l, 
                 "Could not find local '" + l + "' in locals, nor in inlinedLocals: " + inlinedLocals)
             i
             
           case SCOPE_ENTER(l) if inlinedLocals.isDefinedAt(l) =>
             SCOPE_ENTER(inlinedLocals(l))

           case SCOPE_EXIT(l) if inlinedLocals.isDefinedAt(l) =>
             SCOPE_EXIT(inlinedLocals(l))

           case nw @ NEW(sym) =>
             val r = NEW(sym)
             pending(nw.init) = r
             r
             
           case CALL_METHOD(meth, Static(true)) if (meth.isClassConstructor) =>
             CALL_METHOD(meth, Static(true))
             
           case _ => i
         }
         // check any pending NEW's
         if (pending isDefinedAt i) {
           pending(i).init = newInstr.asInstanceOf[CALL_METHOD]
           pending -= i
         }
         newInstr
       }

       addLocals(caller, callee.locals map dupLocal);
       addLocal(caller, inlinedThis);
       if (retVal ne null)
         addLocal(caller, retVal);
       callee.code.blocks.foreach { b => 
         inlinedBlock += (b -> newBlock) 
         inlinedBlock(b).varsInScope ++= (b.varsInScope map inlinedLocals)
       }

       // analyse callee
       a.run

       // re-emit the instructions before the call
       block.open
       block.clear
       instrBefore.foreach(i => block.emit(i, i.pos))

       // store the arguments into special locals
       callee.params.reverse.foreach { param =>
         block.emit(STORE_LOCAL(inlinedLocals(param)), targetPos);
       }
       block.emit(STORE_LOCAL(inlinedThis), targetPos);

       // jump to the start block of the callee
       block.emit(JUMP(inlinedBlock(callee.code.startBlock)), targetPos);
       block.close

       // duplicate the other blocks in the callee
       linearizer.linearize(callee).foreach { bb => 
         var info = a.in(bb);
         for (i <- bb) {
           i match {
             case RETURN(kind) => kind match {
                 case UNIT =>
                   if (!info.stack.types.isEmpty) {
                     info.stack.types foreach { t => inlinedBlock(bb).emit(DROP(t), targetPos); }
                   }
                 case _ =>
                   if (info.stack.length > 1) {
                     inlinedBlock(bb).emit(STORE_LOCAL(retVal), targetPos);
                     info.stack.types.drop(1) foreach { t => inlinedBlock(bb).emit(DROP(t), targetPos); }
                     inlinedBlock(bb).emit(LOAD_LOCAL(retVal), targetPos);
                   }
               }
             case _ => ();
           }
           inlinedBlock(bb).emit(map(i), targetPos);
           info = a.interpret(info, i);
         }
         inlinedBlock(bb).close
       }

       instrAfter.foreach(i => afterBlock.emit(i, i.pos));
       afterBlock.close;
       count += 1

       // add exception handlers of the callee
       caller.exh = (callee.exh map translateExh) ::: caller.exh;
       assert(pending.isEmpty, "Pending NEW elements: " + pending)
     }

    def analyzeClass(cls: IClass): Unit = if (settings.inline.value) {
      if (settings.debug.value)
      	log("Analyzing " + cls);
      cls.methods.foreach { m => if (!m.symbol.isConstructor) analyzeMethod(m)
     }}


    val tfa = new analysis.MethodTFA();
    tfa.stat = settings.statistics.value

    def analyzeMethod(m: IMethod): Unit = try {
      var retry = false
      var count = 0
      fresh.clear
      // how many times have we already inlined this method here?
      val inlinedMethods: Map[Symbol, Int] = new HashMap[Symbol, Int] {
        override def default(k: Symbol) = 0       
      }

      do {
        retry = false;
        if (m.code ne null) {
          if (settings.debug.value)
            log("Analyzing " + m + " count " + count);
          tfa.init(m)
          tfa.run
          for (bb <- linearizer.linearize(m)) {
            var info = tfa.in(bb);
            for (i <- bb.toList) {
              if (!retry) {
                i match {
                  case CALL_METHOD(msym, Dynamic) => 
                    val receiver = info.stack.types.drop(msym.info.paramTypes.length).head match {
                      case REFERENCE(s) => s;
                      case _ => NoSymbol;
                    }
                    var concreteMethod = msym;
                    if (receiver != msym.owner && receiver != NoSymbol) { 
                      if (settings.debug.value)
                        log("" + i + " has actual receiver: " + receiver);
                    }
                    if (!concreteMethod.isFinal && receiver.isFinal) {
                      concreteMethod = lookupImpl(concreteMethod, receiver)
                      if (settings.debug.value)
                        log("\tlooked up method: " + concreteMethod.fullNameString)
                    } 

                    if (receiver == definitions.PredefModule.moduleClass) {
                      log("loading predef")
                      icodes.icode(receiver, true)
                    }
                    if (settings.debug.value)
                      log("Treating " + i 
                          + "\n\treceiver: " + receiver
                          + "\n\ticodes.available: " + icodes.available(receiver)
                          + "\n\tconcreteMethod.isFinal: " + concreteMethod.isFinal);

                    if (   icodes.available(receiver) 
                        && (isClosureClass(receiver)
                            || concreteMethod.isFinal
                            || receiver.isFinal)) {
                      icodes.icode(receiver).get.lookupMethod(concreteMethod) match {
                        case Some(inc) =>
                          if (inc.symbol != m.symbol
                              && (inlinedMethods(inc.symbol) < 2)
                              && (inc.code ne null)
                              && shouldInline(m, inc)
                              && isSafeToInline(m, inc, info.stack)) {
                            retry = true;
                            if (!isClosureClass(receiver)) // only count non-closures
                                count = count + 1;
                            inline(m, bb, i, inc);
                            inlinedMethods(inc.symbol) = inlinedMethods(inc.symbol) + 1

                            /* Remove this method from the cache, as the calls-private relation
                               might have changed after the inlining. */
                            callsPrivate -= m; 
                          } else {
                            if (settings.debug.value)
                              log("inline failed for " + inc + " because:\n\tinc.symbol != m.symbol: " + (inc.symbol != m.symbol)
                                  + "\n\t(inlinedMethods(inc.symbol) < 2): " + (inlinedMethods(inc.symbol) < 2)
                                  + "\n\tinc.code ne null: " + (inc.code ne null) + (if (inc.code ne null)
                                    "\n\tisSafeToInline(m, inc, info.stack): " + isSafeToInline(m, inc, info.stack)
                                    + "\n\tshouldInline heuristics: " + shouldInline(m, inc) else ""));
                          }
                        case None =>
                          ();
                      }
                    }

                  case _ => ();
                }
                info = tfa.interpret(info, i)
              }}}
        if (tfa.stat) log(m.symbol.fullNameString + " iterations: " + tfa.iterations + " (size: " + m.code.blocks.length + ")")
      }} while (retry && count < 15)
      m.normalize
    } catch {
      case e => 
        Console.println("############# Caught exception: " + e + " #################");
        Console.println("\nMethod: " + m + 
                        "\nMethod owner: " + m.symbol.owner);
        e.printStackTrace();
        m.dump
        throw e
    }
    
    /** Cache whether a method calls private members. */
    val callsPrivate: Map[IMethod, Boolean] = new HashMap;
    
    def isRecursive(m: IMethod): Boolean = m.recursive
    
    /** A method is safe to inline when:
     *    - it does not contain calls to private methods when
     *      called from another class
     *    - it is not inlined into a position with non-empty stack,
     *      while having a top-level finalizer (see liftedTry problem)
     *    - it is not recursive
     * Note:
     *    - synthetic private members are made public in this pass.
     */
    def isSafeToInline(caller: IMethod, callee: IMethod, stack: TypeStack): Boolean = {
      var callsPrivateMember = false

      if (callee.recursive) return false
      
      callsPrivate get (callee) match {
        case Some(b) =>
          callsPrivateMember = b
        case None =>
          for (b <- callee.code.blocks)
            for (i <- b.toList)
              i match {
                case CALL_METHOD(m, style) =>
                  if (m.hasFlag(Flags.PRIVATE) || 
                      (style.isSuper && !m.isClassConstructor))
                    callsPrivateMember = true;

                case LOAD_FIELD(f, _) =>
                  if (f.hasFlag(Flags.PRIVATE)) 
                    if (f.hasFlag(Flags.SYNTHETIC) || f.hasFlag(Flags.PARAMACCESSOR)) {
                      if (settings.debug.value)
                        log("Making not-private symbol out of synthetic: " + f);
                      f.setFlag(Flags.notPRIVATE)
                    } else
                      callsPrivateMember = true;

                case STORE_FIELD(f, _) =>
                  if (f.hasFlag(Flags.PRIVATE))
                    if (f.hasFlag(Flags.SYNTHETIC) || f.hasFlag(Flags.PARAMACCESSOR)) {
                      if (settings.debug.value)
                        log("Making not-private symbol out of synthetic: " + f);
                      f.setFlag(Flags.notPRIVATE)
                    } else
                      callsPrivateMember = true;

                case _ => ()
              }
          callsPrivate += (callee -> callsPrivateMember)
        }

      if (callsPrivateMember && (caller.symbol.owner != callee.symbol.owner))
        return false;

      if (stack.length > (1 + callee.symbol.info.paramTypes.length) &&
          callee.exh != Nil) {
        if (settings.debug.value) log("method " + callee.symbol + " is used on a non-empty stack with finalizer.");
        false
      } else
        true
    }
    
    private def lookupImpl(meth: Symbol, clazz: Symbol): Symbol = {
      //println("\t\tlooking up " + meth + " in " + clazz.fullNameString + " meth.owner = " + meth.owner)
      if (meth.owner == clazz 
          || clazz == definitions.NullClass 
          || clazz == definitions.NothingClass) meth
      else {
        val implementingMethod = meth.overridingSymbol(clazz) 
        if (implementingMethod != NoSymbol) 
          implementingMethod
        else if (meth.owner.isTrait) 
          meth
        else
          lookupImpl(meth, clazz.tpe.parents(0).typeSymbol)
      }
    }
    
    /** small method size (in blocks) */
    val SMALL_METHOD_SIZE = 4
    
    /** Decide whether to inline or not. Heuristics:
     *   - it's bad to make the caller larger (> SMALL_METHOD_SIZE) 
     *        if it was small
     *   - it's bad to inline large methods
     *   - it's good to inline higher order functions 
     *   - it's good to inline closures functions. 
     *   - it's bad (useless) to inline inside bridge methods
     */
    def shouldInline(caller: IMethod, callee: IMethod): Boolean = {
       if (caller.symbol.hasFlag(Flags.BRIDGE)) return false;
       if (callee.symbol.hasAttribute(ScalaNoInlineAttr)) return false
       if (callee.symbol.hasAttribute(ScalaInlineAttr)) return true
       if (settings.debug.value)
         log("shouldInline: " + caller + " with " + callee)
       var score = 0
       if (callee.code.blocks.length <= SMALL_METHOD_SIZE) score = score + 1
       if (caller.code.blocks.length <= SMALL_METHOD_SIZE 
           && ((caller.code.blocks.length + callee.code.blocks.length) > SMALL_METHOD_SIZE)) {
         score -= 1
         if (settings.debug.value)
           log("shouldInline: score decreased to " + score + " because small " + caller + " would become large")
       }
       if (callee.code.blocks.length > MAX_INLINE_SIZE)
         score -= 1
         
       if (callee.symbol.tpe.paramTypes.exists(t => definitions.FunctionClass.contains(t.typeSymbol))) {
         if (settings.debug.value)
           log("increased score to: " + score)
         score += 2
       }
       if (isClosureClass(callee.symbol.owner))
         score += 2
         
       score > 0
     }
  } /* class Inliner */
    
  /** Is the given class a subtype of a function trait? */
  def isClosureClass(cls: Symbol): Boolean = {
    val res = cls.isFinal && cls.hasFlag(Flags.SYNTHETIC) &&
        cls.tpe.parents.exists { t => 
          val TypeRef(_, sym, _) = t;
          definitions.FunctionClass exists sym.==
        }
    res
  }
} /* class Inliners */
