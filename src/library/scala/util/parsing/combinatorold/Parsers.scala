/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2006-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: Parsers.scala 13912 2008-02-07 12:40:59Z moors $

package scala.util.parsing.combinatorold

import scala.util.parsing.input._
import scala.collection.mutable.{Map=>MutableMap}

// TODO: better error handling (labelling like parsec's <?>)
// TODO: memoisation (like packrat parsers?) 

/** <p>
 *    <code>Parsers</code> is a component that <i>provides</i> generic
 *    parser combinators.
 *  </p>
 *  <p>
 *    It <i>requires</i> the type of the elements these parsers should parse 
 *    (each parser is polymorphic in the type of result it produces).
 *  </p>
 *  <p>
 *    There are two aspects to the result of a parser: (1) success or failure,
 *    and (2) the result. A <code>Parser[T]</code> provides both kinds of
 *    information, but a <code>UnitParser</code> only signals success/failure.
 *    When composing a `UnitParser' with a normal <code>Parser</code>, the
 *    <code>UnitParser</code> only contributes to whether the combined parser
 *    is successful (i.e., its result is discarded).
 *  </p>
 *  <p>
 *    The term ``parser combinator'' refers to the fact that these parsers
 *    are constructed from primitive parsers and composition operators, such
 *    as sequencing, alternation, optionality, repetition, lifting, and so on.
 *  </p>
 *  <p>
 *    A ``primitive parser'' is a parser that accepts or rejects a single
 *    piece of input, based on a certain criterion, such as whether the
 *    input...
 *  </p><ul>
 *    <li> is equal to some given object, </li>
 *    <li> satisfies a certain predicate, </li>
 *    <li> is in the domain of a given partial function,.... </li>
 *  </ul>
 *  <p>
 *    Even more primitive parsers always produce the same result, irrespective
 *    of the input.
 *  </p>
 *
 * @requires Elem the type of elements the provided parsers consume 
 *              (When consuming invidual characters, a parser is typically called a ``scanner'', 
 *               which produces ``tokens'' that are consumed by what is normally called a ``parser''.
 *               Nonetheless, the same principles apply, regardless of the input type.)</p>
 *<p>
 * @provides Input = Reader[Elem] 
 *              The type of input the parsers in this component expect.</p>
 *<p>
 * @provides Parser[+T] extends (Input => ParseResult[T]) 
 *              Essentially, a `Parser[T]' is a function from `Input' to `ParseResult[T]'.</p>
 *<p>
 * @provides ParseResult[+T] is like an `Option[T]', in the sense that it is either
 *              `Success[T]', which consists of some result (:T) (and the rest of the input) or
 *              `Failure[T]', which provides an error message (and the rest of the input).</p>
 *
 * @author Martin Odersky, Iulian Dragos, Adriaan Moors 
 */
trait Parsers {
  /** the type of input elements */
  type Elem

  /** The parser input is an abstract reader of input elements */
  type Input = Reader[Elem]

  /** A base class for parser results. 
   *  A result is either successful or not (failure may be fatal, i.e.,
   *  an Error, or not, i.e., a Failure)
   *  On success, provides a result of type <code>T</code>. 
   */
  sealed abstract class ParseResult[+T] {
    /** Functional composition of ParseResults
     * 
     * @param `f' the function to be lifted over this result
     * @return `f' applied to the result of this `ParseResult', packaged up as a new `ParseResult'
     */
    def map[U](f: T => U): ParseResult[U]
    
    /** Partial functional composition of ParseResults
     * 
     * @param `f' the partial function to be lifted over this result
     * @param error a function that takes the same argument as `f' and produces an error message 
     *        to explain why `f' wasn't applicable (it is called when this is the case)
     * @return <i>if `f' f is defined at the result in this `ParseResult',</i>
     *         `f' applied to the result of this `ParseResult', packaged up as a new `ParseResult'.
     *         If `f' is not defined, `Failure'.
     */
    def mapPartial[U](f: PartialFunction[T, U], error: T => String): ParseResult[U]   
    
    def isEmpty = !successful
    
    /** Returns the embedded result */
    def get: T
    
    def getOrElse[B >: T](default: => B): B = 
        if (isEmpty) default else this.get
    
    val next: Input
    
    val successful: Boolean
  }

  /** The success case of ParseResult: contains the result and the remaining input.
   *
   *  @param result The parser's output 
   *  @param next   The parser's remaining input
   */
  case class Success[+T](result: T, override val next: Input) extends ParseResult[T] {
    def map[U](f: T => U) = Success(f(result), next)
    def mapPartial[U](f: PartialFunction[T, U], error: T => String): ParseResult[U] 
       = if(f.isDefinedAt(result)) Success(f(result), next) 
         else Failure(error(result), next)

    def get: T = result
    
    /** The toString method of a Success */
    override def toString = "["+next.pos+"] parsed: "+result
    
    val successful = true
  }

  /** A common super-class for unsuccessful parse results
   */
  sealed abstract class NoSuccess(val msg: String, override val next: Input) extends ParseResult[Nothing] { // when we don't care about the difference between Failure and Error
    val successful = false

    def map[U](f: Nothing => U) = this
    def mapPartial[U](f: PartialFunction[Nothing, U], error: Nothing => String): ParseResult[U] = this

    def get: Nothing = error("No result when parsing failed")
  }
  
  /** The failure case of ParseResult: contains an error-message and the remaining input.
   * Parsing will back-track when a failure occurs.
   *
   *  @param msg    An error message string describing the failure.
   *  @param next   The parser's unconsumed input at the point where the failure occurred.
   */
  case class Failure(override val msg: String, override val next: Input) extends NoSuccess(msg, next) {
    /** The toString method of a Failure yields an error message */
    override def toString = "["+next.pos+"] failure: "+msg+"\n\n"+next.pos.longString
  }
  
  /** The fatal failure case of ParseResult: contains an error-message and the remaining input.
   * No back-tracking is done when a parser returns an `Error' 
   *
   *  @param msg    An error message string describing the error.
   *  @param next   The parser's unconsumed input at the point where the error occurred.
   */
  case class Error(override val msg: String, override val next: Input) extends NoSuccess(msg, next) {
    /** The toString method of an Error yields an error message */
    override def toString = "["+next.pos+"] error: "+msg+"\n\n"+next.pos.longString
  }
  
  /** The root class of parsers. 
   *  Parsers are functions from the Input type to ParseResult 
   */
  abstract class Parser[+T] extends (Input => ParseResult[T]) {
    /** An unspecified method that defines the behaviour of this parser.
     */
    def apply(in: Input): ParseResult[T]

                                      
    // the operator formerly known as +++, ++, &, but now, behold the venerable ~
    // it's short, light (looks like whitespace), has few overloaded meaning (thanks to the recent change from ~ to unary_~)
    // and we love it!
                                      
    /** A parser combinator for sequential composition 
     *
     * <p> `p ~ q' succeeds if `p' succeeds and `q' succeeds on the input
     *          left over by `p'.</p>
     * 
     * @param q a parser that will be executed after `p' (this parser) succeeds
     * @return a `Parser' that -- on success -- returns a `~' (like a Pair, but easier to pattern match on) 
     *         that contains the result of `p' and that of `q'. 
     *         The resulting parser fails if either `p' or `q' fails.
     */
    def ~ [U](q: => Parser[U]): Parser[~[T, U]] = new Parser[~[T, U]] {
      def apply(in: Input) = seq(Parser.this, q)((x, y) => new ~(x,y))(in)
      override def toString = "~"
    }
   
     /* not really useful: V cannot be inferred because Parser is covariant in first type parameter (V is always trivially Nothing)
    def ~~ [U, V](q: => Parser[U])(implicit combine: (T, U) => V): Parser[V] = new Parser[V] {
      def apply(in: Input) = seq(Parser.this, q)((x, y) => combine(x,y))(in)
    }  */   
     
    /** A parser combinator for sequential composition with a unit-parser 
     *
     * <p> `p ~ q' succeeds if `p' succeeds and `q' succeeds on the input
     *          left over by `p'.</p>
     *
     * @param q a parser (convertible to a UnitParser) that will be executed after `p' (this parser)
     *          succeeds
     * @return a `Parser' that -- on success -- returns the result of `p'.
     *         The resulting parser fails if either `p' or `q' fails.
     */ 
    def ~ [Q <% UnitParser](q: => Q): Parser[T] = new Parser[T] {
      def apply(in: Input) = seq(Parser.this, q)((x, y) => x)(in)
      override def toString = "~"
    }
    
    /** A parser combinator for non-back-tracking sequential composition 
     *
     *<p>`p ~! q' succeeds if `p' succeeds and `q' succeeds on the input
     *          left over by `p'. In case of failure, no back-tracking is performed 
     *          (in an earlier parser produced by the | combinator).</p>
     * 
     * @param q a parser that will be executed after `p' (this parser) succeeds
     * @return a `Parser' that -- on success -- returns a `~' (like a Pair, but easier to pattern match on) 
     *         that contains the result of `p' and that of `q'. 
     *         The resulting parser fails if either `p' or `q' fails, this failure is fatal.
     */
    def ~! [U](q: => Parser[U]): Parser[~[T, U]] = new Parser[~[T, U]] with OnceParser[~[T, U]] {
      def apply(in: Input) = seq(Parser.this, commit(q))((x, y) => new ~(x,y))(in)
      override def toString = "~!"
    }
    
    /** A parser combinator for non-back-tracking sequential composition with a unit-parser
     *
     *<p>`p ~! q' succeeds if `p' succeeds and `q' succeeds on the input
     *          left over by `p'. In case of failure, no back-tracking is performed 
     *          (in an earlier parser produced by the | combinator).</p>
     * 
     * @param q a parser that will be executed after `p' (this parser) succeeds
     * @return a `Parser' that -- on success -- returns the result of `p'.
     *         The resulting parser fails if either `p' or `q' fails, this failure is fatal.
     */
    def ~! [Q <% UnitParser](q: => Q): Parser[T] = new Parser[T] with OnceParser[T] {
      def apply(in: Input) = seq(Parser.this, commit(q))((x, y) => x)(in)
      override def toString = "~!"
    }

    /** A parser combinator for alternative composition 
     *
     *<p>`p | q' succeeds if `p' succeeds or `q' succeeds
     *          Note that `q' is only tried if `p's failure is non-fatal (i.e., back-tracking is
     *          allowed).</p>
     * 
     * @param q a parser that will be executed if `p' (this parser) fails (and allows back-tracking)
     * @return a `Parser' that returns the result of the first parser to succeed (out of `p' and `q')
     *         The resulting parser succeeds if (and only if) <ul>
     *           <li> `p' succeeds, <i>or</i>  </li>
     *           <li> if `p' fails allowing back-tracking and `q' succeeds. </li> </ul>
     */
    def | [U >: T](q: => Parser[U]): Parser[U] = new Parser[U] {
      def apply(in: Input) = Parser.this(in) match {
        case s1 @ Success(_, _) => s1
        case e1 @ Error(_, _) => e1
        case f1 @ Failure(_, next1) => q(in) match {
              case s2 @ Success(_, _) => s2
              case f2 @ Failure(_, next2) => if (next2.pos < next1.pos) f1 else f2
              case e2 @ Error(_, next2) => if (next2.pos < next1.pos) f1 else e2
        }
      }
      override def toString = "|"
    }
    
    /** A parser combinator for alternative with longest match composition 
     *
     *<p>`p ||| q' succeeds if `p' succeeds or `q' succeeds
     *          If `p' and `q' both succeed, the parser that consumed the most
     *          characters accepts.</p>
     * 
     * @param q a parser that accepts if p consumes less characters.
     * @return a `Parser' that returns the result of the parser consuming the most characteres (out of `p' and `q').
     */
    def ||| [U >: T](q: => Parser[U]): Parser[U] = new Parser[U] {
      def apply(in: Input) = {
        val res1 = Parser.this(in)
        val res2 = q(in)
        
        (res1, res2) match {
          case (s1 @ Success(_, next1), s2 @ Success(_, next2)) => if (next2.pos < next1.pos) s1 else s2
          case (s1 @ Success(_, _), _) => s1
          case (_, s2 @ Success(_, _)) => s2
          case (e1 @ Error(_, _), _) => e1
          case (f1 @ Failure(_, next1), f2 @ Failure(_, next2)) => if (next2.pos < next1.pos) f1 else f2
          case (f1 @ Failure(_, next1), e2 @ Error(_, next2)) => if (next2.pos < next1.pos) f1 else e2
        }
      }
      override def toString = "|||"
    }

    /** A parser combinator for function application 
     *
     *<p>`p ^^ f' succeeds if `p' succeeds; it returns `f' applied to the result of `p'.</p>
     *
     * @param f a function that will be applied to this parser's result (see `map' in `ParseResult').
     * @return a parser that has the same behaviour as the current parser, but whose result is
     *         transformed by `f'.
     */
    def ^^ [U](f: T => U): Parser[U] = new Parser[U] {
      def apply(in: Input) = Parser.this(in).map(f)
      override def toString = Parser.this.toString+"^^"
    }    
    
    /** A parser combinator for partial function application 
     *
     *<p>`p ^? (f, error)' succeeds if `p' succeeds AND `f' is defined at the result of `p'; 
     *  in that case, it returns `f' applied to the result of `p'. If `f' is not applicable,
     *  error(the result of `p') should explain why.</p>
     *
     * @param f a partial function that will be applied to this parser's result 
     *          (see `mapPartial' in `ParseResult').
     * @param error a function that takes the same argument as `f' and produces an error message 
     *        to explain why `f' wasn't applicable
     * @return a parser that succeeds if the current parser succeeds <i>and</i> `f' is applicable 
     *         to the result. If so, the result will be transformed by `f'.     
     */
    def ^? [U](f: PartialFunction[T, U], error: T => String): Parser[U] = new Parser[U] {
      def apply(in: Input) = Parser.this(in).mapPartial(f, error)
      override def toString = Parser.this.toString+"^?"
    }    
     
    /** A parser combinator for partial function application 
     *
     *<p>`p ^? f' succeeds if `p' succeeds AND `f' is defined at the result of `p'; 
     *  in that case, it returns `f' applied to the result of `p'.</p>
     *
     * @param f a partial function that will be applied to this parser's result 
     *          (see `mapPartial' in `ParseResult').
     * @return a parser that succeeds if the current parser succeeds <i>and</i> `f' is applicable 
     *         to the result. If so, the result will be transformed by `f'.     
     */
    def ^? [U](f: PartialFunction[T, U]): Parser[U] = new Parser[U] {
      def apply(in: Input) = Parser.this(in).mapPartial(f, result => "Constructor function not defined at "+result)
      override def toString = Parser.this.toString+"^?"
    }       
    
       
    /** A parser combinator that parameterises a subsequent parser with the result of this one
     *
     *<p>
     * Use this combinator when a parser depends on the result of a previous parser. `p' should be
     * a function that takes the result from the first parser and returns the second parser.</p>
     *
     *<p> `p into fq' (with `fq' typically `{x => q}') first applies `p', and then, if `p' successfully  
     *    returned result `r', applies `fq(r)' to the rest of the input. </p>
     *
     *<p> From: G. Hutton. Higher-order functions for parsing. J. Funct. Program., 2(3):323--343, 1992. </p>
     *
     * @param fq a function that, given the result from this parser, returns the second parser to be applied
     * @return a parser that succeeds if this parser succeeds (with result `x') and if then `fq(x)' succeeds
     */
    def into[U](fq: T => Parser[U]): Parser[U] = new Parser[U] {
       def apply(in: Input) = Parser.this(in) match {
         case Success(result, next) => fq(result)(next)
         case ns: NoSuccess => ns
       }
     }
    
    // shortcuts for combinators:
    
    /** Returns into(fq) */
    def >>[U](fq: T => Parser[U])=into(fq)
    
    
    /** Returns a parser that repeatedly parses what this parser parses
     *
     * @return rep(this) 
     */
    def * = rep(this)
    
    /** Returns a parser that repeatedly parses what this parser parses, interleaved with the `sep' parser.
     *
     * @return repsep(this, sep)
     */
    def *[Q <% UnitParser](sep: => Q) = repsep(this, sep)
    
    /** Returns a parser that repeatedly parses what this parser parses, interleaved with the `sep' parser.
     * The `sep' parser specifies how the results parsed by this parser should be combined.
     *
     * @return chainl1(this, sep) 
     */    
    def *[U >: T](sep: => Parser[(U, U) => U]) = chainl1(this, sep)
    
    // TODO: improve precedence? a ~ b*(",") = a ~ (b*(","))  should be true 
    
    /** Returns a parser that repeatedly (at least once) parses what this parser parses.
     *
     * @return rep1(this) 
     */      
    def + = rep1(this)
    
    /** Returns a parser that optionally parses what this parser parses.
     *
     * @return opt(this) 
     */       
    def ? = opt(this)
  }

  /** The root class of special parsers returning the trivial result <code>Unit</code>
   *  These compose differently from normal parsers in that the <code>Unit</code>
   *  result in a sequential or function composition is dropped.
   */
  abstract class UnitParser extends (Input => ParseResult[Unit]) {
                                      
    /** An unspecified method that defines the behaviour of this parser.
     */
    def apply(in: Input): ParseResult[Unit]

    /** A parser combinator for sequential composition 
     *
     *<p>`p ~ q' succeeds if `p' succeeds and `q' succeeds on the input
     *          left over by `p'.</p>
     * 
     * @param q a parser that will be executed after `p' (this parser) succeeds
     * @return a `Parser' that -- on success -- returns the result of `q'. 
     *         The resulting parser fails if either `p' or `q' fails.
     */
    def ~ [U](q: => Parser[U]): Parser[U] = new Parser[U] {
      def apply(in: Input): ParseResult[U] = seq(UnitParser.this, q)((x, y) => y)(in)
      override def toString = "~"
    }
     
    /** A parser combinator for sequential composition with a unit-parser 
     *
     * <p>`p ~ q' succeeds if `p' succeeds and `q' succeeds on the input
     *          left over by `p'.</p>
     *
     * @param q a parser (convertible to a UnitParser) that will be executed after `p' (this parser)
     *          succeeds
     * @return a `UnitParser' that fails if either `p' or `q' fails. 
     */ 
    def ~ [A <% UnitParser](q: => A): UnitParser = new UnitParser {
      def apply(in: Input): ParseResult[Unit] = seq(UnitParser.this, q)((x, y) => y)(in)
      override def toString = "~"
    }
     
    /** A parser combinator for non-back-tracking sequential composition 
     *
     *  <p>`p ~! q' succeeds if `p' succeeds and `q' succeeds on the input
     *          left over by `p'. In case of failure, no back-tracking is performed 
     *          (in an earlier parser produced by the | combinator).</p>
     * 
     * @param q a parser that will be executed after `p' (this parser) succeeds
     * @return a `Parser' that -- on success -- returns the result of `q`. 
     *         The resulting parser fails if either `p' or `q' fails, this failure is fatal.
     */
    def ~! [U](q: => Parser[U]): Parser[U] = new Parser[U] with OnceParser[U] {
      def apply(in: Input) = seq(UnitParser.this, commit(q))((x, y) => y)(in)
      override def toString = "~!"
    }

    /** A parser combinator for non-back-tracking sequential composition with a unit-parser
     *
     *  <p>`p ~! q' succeeds if `p' succeeds and `q' succeeds on the input
     *          left over by `p'. In case of failure, no back-tracking is performed 
     *          (in an earlier parser produced by the | combinator).</p>
     * 
     * @param q a parser that will be executed after `p' (this parser) succeeds
     * @return a `UnitParser' that fails if either `p' or `q' fails, this failure is fatal.
     */
    def ~! [Q <% UnitParser](q: => Q): UnitParser = new UnitParser with UnitOnceParser {
      def apply(in: Input) = seq(UnitParser.this, commit(q))((x, y) => y)(in)
      override def toString = "~!"
    }

     /** A parser combinator for alternative composition 
     *
     *<p>`p | q' succeeds if `p' succeeds or `q' succeeds
     *          Note that `q' is only tried if `p's failure is non-fatal (i.e., back-tracking is
     *          allowed).</p>
     * 
     * @param q a parser that will be executed if `p' (this parser) fails (and allows back-tracking)
     * @return a `Parser' succeeds if (and only if) <ul>
     *           <li> `p' succeeds, <i>or</i>  </li>
     *           <li> if `p' fails allowing back-tracking and `q' succeeds. </li> </ul>
     */
    def | [Q <% UnitParser](q: => Q): UnitParser = new UnitParser {
      def apply(in: Input) = UnitParser.this(in) match {
        case s1 @ Success(_, _) => s1
        case e1 @ Error(_, _) => e1
        case f1 @ Failure(_, next1) => q(in) match {
              case s2 @ Success(_, _) => s2
              case f2 @ Failure(_, next2) => if (next2.pos < next1.pos) f1 else f2
              case e2 @ Error(_, next2) => if (next2.pos < next1.pos) f1 else e2
        }
      }
      override def toString = "|"
    }
    
    /** A parser combinator for alternative with longest match composition 
     *
     *<p>`p ||| q' succeeds if `p' succeeds or `q' succeeds
     *          If `p' and `q' both succeed, the parser that consumed the most
     *          characters accepts.</p>
     * 
     * @param q a parser that accepts if p consumes less characters.
     * @return a `Parser' that returns the result of the parser consuming the most characteres (out of `p' and `q').
     */
    def ||| [Q <% UnitParser](q: => Q): UnitParser = new UnitParser {
      def apply(in: Input) = {
        val res1 = UnitParser.this(in)
        val res2 = q(in)
        
        (res1, res2) match {
          case (s1 @ Success(_, next1), s2 @ Success(_, next2)) => if (next2.pos < next1.pos) s1 else s2
          case (s1 @ Success(_, _), _) => s1
          case (_, s2 @ Success(_, _)) => s2
          case (e1 @ Error(_, _), _) => e1
          case (f1 @ Failure(_, next1), f2 @ Failure(_, next2)) => if (next2.pos < next1.pos) f1 else f2
          case (f1 @ Failure(_, next1), e2 @ Error(_, next2)) => if (next2.pos < next1.pos) f1 else e2
        }
      }
      override def toString = "|||"
    }
     
     
    /** A parser combinator for function application 
     *
     *  <p>`p ^^ v' succeeds if `p' succeeds; it returns `v'.</p>
     *
     * @param v a value that's used as the result of the returned Parser (if it was successful).
     * @return a parser that has the same behaviour as the current parser, but whose result is
     *         consists of `v'.
     */
    def ^^ [U](v: U): Parser[U] = new Parser[U] {
      def apply(in: Input) = UnitParser.this(in).map(x => v)
      override def toString = UnitParser.this.toString+"^^"
    }
  }

  // TODO: can this implemented in ParseResult, like map?
  /** A helper method for sequential composition of (unit-)parsers
  */
  private def seq[T, U, V](p: => Input => ParseResult[T], q: => Input => ParseResult[U])
                          (compose: (T, U) => V)
                          (in: Input): ParseResult[V] 
    = p(in) match {
      case Success(x, next1) => q(next1) match {
          case Success(y, next2) => Success(compose(x, y), next2)
          case ns: NoSuccess => ns
        }
      case ns: NoSuccess => ns
    }
  
  /** Wrap a parser so that its failures become errors (the | combinator will give up as soon as 
   *  it encounters an error, on failure it simply tries the next alternative) 
   */
  def commit[T](p: => Parser[T]) = new Parser[T] {
    def apply(in: Input) = p(in) match{
      case s @ Success(_, _) => s
      case e @ Error(_, _) => e
      case f @ Failure(msg, next) => Error(msg, next)
    }
  }
  
  /** Wrap a parser so that its failures become errors (the | combinator will give up as soon as 
   *  it encounters an error, on failure it simply tries the next alternative) 
   */
  def commit[Q <% UnitParser](p: => Q) = new UnitParser {
    def apply(in: Input) = p(in) match{
      case s @ Success(_, _) => s
      case e @ Error(_, _) => e
      case f @ Failure(msg, next) => Error(msg, next)
    }
  }
  
  /** Wrap a parser so that its failures&errors become success and vice versa -- it never consumes any input 
   */
  def not[Q <% UnitParser](p: => Q) = new UnitParser {
    def apply(in: Input) = p(in) match{
      case s @ Success(_, _) => Failure("Expected failure", in)
      case e @ Error(_, _) => Success((), in)
      case f @ Failure(msg, next) => Success((), in)
    }
  }  
  
	/*trait ElemFun
  case class EFCons(hd: Elem => ElemFun, tl: ElemFun) extends ElemFun
  case class EFNil(res: Boolean) extends ElemFun*/

   
  /** A parser matching input elements that satisfy a given predicate
   *
   * <p>elem(kind, p) succeeds if the input starts with an element `e' for which p(e) is true.</p>
   *
   * @param  kind   The element kind, used for error messages
   * @param  p      A predicate that determines which elements match.
   * @return 
   */
  def elem(kind: String, p: Elem => Boolean) = new Parser[Elem] {
    def apply(in: Input) =
      if (p(in.first)) Success(in.first, in.rest)
      else Failure(kind+" expected", in)
  }  
   
  /** A parser that matches only the given element `e'
   *
   * <p>elem(e) succeeds if the input starts with an element `e'</p>
   *
   * @param e the `Elem' that must be the next piece of input for the returned parser to succeed
   * @return a `Parser' that succeeds if `e' is the next available input (and returns it).
   */
  def elem(e: Elem): Parser[Elem] = new Parser[Elem] {
    def apply(in: Input) =
      if (in.first == e) Success(e, in.rest)
      else Failure("`"+e+"' expected but " + in.first + " found", in)
  }
  
  
  /** A parser that matches only the given element `e' 
   *<p>
   * The method is implicit so that elements can automatically be lifted to their unit-parsers. 
   * For example, when parsing `Token's, Identifier("new") (which is a `Token') can be used directly,
   * instead of first creating a `UnitParser' using accept(Identifier("new")).</p>
   *
   * @param e the `Elem' that must be the next piece of input for the returned parser to succeed
   * @return a `UnitParser' that succeeds if `e' is the next available input.
   */
  implicit def accept(e: Elem): UnitParser = new UnitParser {
    def apply(in: Input) =
      if (in.first == e) Success((), in.rest)
      else Failure("`"+e+"' expected but " + in.first + " found", in)
  }
   
  /** A parser that matches only the given list of element `es'
   *
   * <p>accept(es) succeeds if the input subsequently provides the elements in the list `es'.</p>
   *
   * @param  es the list of expected elements
   * @return a UnitParser that recognizes a specified list of elements
   */
  def accept[ES <% List[Elem]](es: ES): UnitParser = new UnitParser {
    def apply(in0: Input) = {
      var res = new scala.collection.mutable.ListBuffer[Elem]
      var these: List[Elem] = es
      var in = in0
      
      while(!these.isEmpty && in.first == these.head) {
        these = these.tail
        in = in.rest
      }
       
      if (these.isEmpty) Success((), in)
      else Failure("Expected: '"+these.head+"', found: '"+in.first+"'", in0)
    }
  }    

  /** The parser that matches an element in the domain of the partial function `f'
   *<p>
   * If `f' is defined on the first element in the input, `f' is applied to it to produce 
   * this parser's result.</p>
   *<p>
   * Example: The parser <code>accept("name", {case Identifier(n) => Name(n)})</code> 
   *          accepts an <code>Identifier(n)</code> and returns a <code>Name(n)</code>.</p>
   *
   * @param expected a description of the kind of element this parser expects (for error messages)
   * @param f a partial function that determines when this parser is successful and what its output is
   * @return A parser that succeeds if `f' is applicable to the first element of the input, 
   *         applying `f' to it to produce the result.
   */
  def accept[U](expected: String, f: PartialFunction[Elem, U]): Parser[U] = new Parser[U] {
    def apply(in: Input) =
      if (f.isDefinedAt(in.first)) Success(f(in.first), in.rest)
      else Failure(expected+" expected", in)
  }

  /** A parser that always fails 
   *
   * @param msg The error message describing the failure. 
   * @return A parser that always fails with the specified error message.
   */
  def failure(msg: String) = new Parser[Nothing] {
    def apply(in: Input) = Failure(msg, in)
  }

  /** A unit-parser that always fails 
   *
   * @param msg The error message describing the failure. 
   * @return A parser that always fails with the specified error message.
   */  
  def fail(msg: String) = new UnitParser {
    def apply(in: Input) = Failure(msg, in)
  }

  /** A parser that always succeeds 
   *
   * @param v The result for the parser
   * @return A parser that always succeeds, with the given result `v'
   */
  def success[T](v: T) = new Parser[T] {
    def apply(in: Input) = Success(v, in)
  }

  /** A unit-parser that always succeeds  */
  def success = new UnitParser {
    def apply(in: Input) = Success((), in)
  }
  
  /** A unit-parser that always succeeds, discarding `p's result
  *
  * @param p The parser whose result is to be discarded
  * @return A parser that always succeeds, with the empty result
  */
  implicit def discard[T](p: => Parser[T]) = new UnitParser {
    def apply(in: Input) = p(in) map {(x) => ()}
  }
  
  
  def log[T](p: => Parser[T])(name: String): Parser[T] = new Parser[T] {
    def apply(in: Input) = {println("trying "+name+" at "+in.pos); val r = p(in); println(name+" --> "+r); r }
  }
  
  def log[Q <% UnitParser](p: => Q)(name: String): UnitParser = new UnitParser {
    def apply(in: Input) = {println("trying "+name+" at "+in.pos); val r = p(in); println(name+" --> "+r); r }
  }
    
    
  /** A parser generator for repetitions.
   *  
   * <p> rep(p)   repeatedly uses `p' to parse the input until `p' fails (the result is a List 
   *  of the consecutive results of `p') </p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @return A parser that returns a list of results produced by repeatedly applying `p' to the input.
   */
  def rep[T](p: => Parser[T]): Parser[List[T]] = rep1(p) | success(List())

  /** A parser generator for repetitions.
   *  
   * <p> rep(p)   repeatedly uses `p' to parse the input until `p' fails</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @return A parser that repeatedly applies `p' to the input.
   */  
  def rep[Q <% UnitParser](p: => Q): UnitParser = rep1(p) | success
    
  /** A parser generator for interleaved repetitions.
   *  
   * <p> repsep(p, q)   repeatedly uses `p' interleaved with `q' to parse the input, until `p' fails.
   *  (The result is a `List' of the results of `p'.) </p>
   *
   * <p>Example: <code>repsep(term, ",")</code> parses a comma-separated list of term's, 
   *          yielding a list of these terms</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @param q a `UnitParser' that parses the elements that separate the elements parsed by `p'
   * @return A parser that returns a list of results produced by repeatedly applying `p' (interleaved
   *         with `q') to the input.
   */
  def repsep[T, Q <% UnitParser](p: => Parser[T], q: => Q): Parser[List[T]] = 
    rep1sep(p, q) | success(List())
  
  /** A parser generator for non-empty repetitions.
   *  
   * <p> rep1(p) repeatedly uses `p' to parse the input until `p' fails -- `p' must succeed at least
   *             once (the result is a `List' of the consecutive results of `p')</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @return A parser that returns a list of results produced by repeatedly applying `p' to the input
   *        (and that only succeeds if `p' matches at least once).
   */
  def rep1[T](p: => Parser[T]): Parser[List[T]] = rep1(p, p)
  
  /** A parser generator for non-empty repetitions.
   *  
   * <p> rep1(f, p) first uses `f' (which must succeed) and then repeatedly uses `p' to 
   *     parse the input until `p' fails 
   *     (the result is a `List' of the consecutive results of `f' and `p')</p>
   *
   * @param first a `Parser' that parses the first piece of input
   * @param p a `Parser' that is to be applied successively to the rest of the input (if any)
   * @return A parser that returns a list of results produced by first applying `f' and then 
   *         repeatedly `p' to the input (it only succeeds if `f' matches).
   */
  def rep1[T](first: => Parser[T], p: => Parser[T]): Parser[List[T]] = first ~ rep(p) ^^ { case ~(x, xs) => x :: xs }
  
  /* new Parser[List[T]] {
    def apply(in0: Input) = {
      val xs = new scala.collection.mutable.ListBuffer[T]
      var in = in0
      
      var res = first(in)
      
      while(res.successful) {
        xs += res.get
        in = res.next
        res = p(in)
      }
      
      if (!xs.isEmpty) Success(xs.toList, res.next)
      else Failure("TODO", TODO)
    }
  }*/

  /** A parser generator for a specified number of repetitions.
   *  
   * <p> repN(n, p)  uses `p' exactly `n' time to parse the input 
   *       (the result is a `List' of the `n' consecutive results of `p')</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @param n the exact number of times `p' must succeed
   * @return A parser that returns a list of results produced by repeatedly applying `p' to the input
   *        (and that only succeeds if `p' matches exactly `n' times).
   */  
  def repN[T](n: Int, p: => Parser[T]): Parser[List[T]] = 
    if(n==0) success(Nil) else p ~ repN(n-1, p) ^^ { case ~(x, xs) => x :: xs }
  
  /** A parser generator for non-empty repetitions.
   *  
   * <p> rep1(p) repeatedly uses `p' to parse the input until `p' fails -- `p' must succeed at least
   *             once</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @return A unitparser that repeatedly applies `p' to the input
   *        (and that only succeeds if `p' matches at least once).
   */
  def rep1[Q <% UnitParser](p: => Q): UnitParser = 
    p ~ rep(p)
  
  /** A parser generator for a specified number of repetitions.
   *  
   * <p> repN(n, p)  uses `p' exactly `n' time to parse the input</p>
   *
   * @param p a `Parser' that is to be applied successively to the input
   * @param n the exact number of times `p' must succeed
   * @return A unitparser that repeatedly applies `p' to the input
   *        (and that only succeeds if `p' matches at exactly `n' times).
   */  
  def repN[Q <% UnitParser](n: Int, p: => Q): UnitParser = 
    if(n==0) success else p ~ repN(n-1, p) 
   
  /** A parser generator for non-empty repetitions.
   *  
   *  <p>rep1sep(first, p, q) starts by using `first', followed by repeatedly uses of `p' interleaved with `q' 
   *                to parse the input, until `p' fails. `first' must succeed (the result is a `List' of the 
   *                consecutive results of `first' and `p')</p>
   *
   * @param first a `Parser' that is to be applied to the first element of input
   * @param p a `Parser' that is to be applied successively to the input
   * @param q a `UnitParser' that parses the elements that separate the elements parsed by `p' 
   *          (interleaved with `q')   
   * @return A parser that returns a list of results produced by repeatedly applying `p' to the input
   *        (and that only succeeds if `p' matches at least once).
   */
  def rep1sep[T, Q <% UnitParser](first: => Parser[T], p: => Parser[T], q: => Q): Parser[List[T]] = 
    first ~ rep(q ~ p) ^^ { case x ~ y => x :: y }

  def rep1sep[T, Q <% UnitParser](p: => Parser[T], q: => Q): Parser[List[T]] = rep1sep(p, p, q)
   
  /** A parser generator that, roughly, generalises the rep1sep generator so that `q', which parses the separator,
   * produces a left-associative function that combines the elements it separates.
   *
   * <p> From: J. Fokker. Functional parsers. In J. Jeuring and E. Meijer, editors, Advanced Functional Programming, volume 925 of Lecture Notes in Computer Science, pages 1--23. Springer, 1995.</p>
   *
   * @param p a parser that parses the elements
   * @param q a parser that parses the token(s) separating the elements, yielding a left-associative function that 
   *          combines two elements into one 
   */
  def chainl1[T](p: => Parser[T], q: => Parser[(T, T) => T]): Parser[T] = 
    p ~ rep(q ~ p) ^^ {case x ~ xs => xs.foldLeft(x)((a, f_b) => f_b._1(a, f_b._2))}  // ((a, {f, b}) => f(a, b)) 

  /** A parser generator that, roughly, generalises the rep1sep generator so that `q', which parses the separator,
   * produces a left-associative function that combines the elements it separates.
   *
   * @param first a parser that parses the first element
   * @param p a parser that parses the subsequent elements
   * @param q a parser that parses the token(s) separating the elements, yielding a left-associative function that 
   *          combines two elements into one 
   */
  def chainl1[T, U](first: => Parser[T], p: => Parser[U], q: => Parser[(T, U) => T]): Parser[T] = 
    first ~ rep(q ~ p) ^^ {case x ~ xs => xs.foldLeft(x)((a, f_b) => f_b._1(a, f_b._2))}  // ((a, {f, b}) => f(a, b)) 
        
  /** A parser generator that generalises the rep1sep generator so that `q', which parses the separator,
   * produces a right-associative function that combines the elements it separates. Additionally,
   * The right-most (last) element and the left-most combinating function have to be supplied.
   * 
   * rep1sep(p: Parser[T], q) corresponds to chainr1(p, q ^^ cons, cons, Nil) (where val cons = (x: T, y: List[T]) => x :: y)
   *
   * @param p a parser that parses the elements
   * @param q a parser that parses the token(s) separating the elements, yielding a right-associative function that 
   *          combines two elements into one 
   * @param combine the "last" (left-most) combination function to be applied
   * @param first   the "first" (right-most) element to be combined
   */
  def chainr1[T, U](p: Parser[T], q: Parser[(T, U) => U], combine: (T, U) => U, first: U): Parser[U] = 
    p ~ rep(q ~ p) ^^ {case x ~ xs => 
                          (new ~(combine, x) :: xs).foldRight(first)((f_a, b) => f_a._1(f_a._2, b))}  // (({f, a}, b) => f(a, b))     
    
  /** A parser generator for optional sub-phrases.
   *  
   *  <p>opt(p) is a parser that returns `Some(x)' if `p' returns `x' and `None' if `p' fails</p>
   *
   * @param p A `Parser' that is tried on the input
   * @return a `Parser' that always succeeds: either with the result provided by `p' or 
   *         with the empty result
   */
  def opt[T](p: => Parser[T]): Parser[Option[T]] = 
    p ^^ (x => Some(x)) | success(None)


  /** Turns a unit-parser into a boolean-parser that denotes whether the unit-parser succeeded.
   */
  def opt[Q <% UnitParser](q: => Q): Parser[Boolean] = q ^^ true | success(false) 
    
    
  /** `positioned' decorates a parser's result with the start position of the input it consumed. 
   * 
   * @param p a `Parser' whose result conforms to `Positional'.
   * @return A parser that has the same behaviour as `p', but which marks its result with the 
   *         start position of the input it consumed, if it didn't already have a position.
   */
  def positioned[T <: Positional](p: => Parser[T]): Parser[T] = new Parser[T] {
    def apply(in: Input) = p(in) match {
      case Success(t, in1) => Success(if (t.pos == NoPosition) t setPos in.pos else t, in1)
      case ns: NoSuccess => ns
    }
  }

  /** <code>positioned</code> decorates a unit-parser so that it returns the
   *  start position of the input it consumed. 
   * 
   *  @param p a `UnitParser'.
   *  @return  A parser that has the same behaviour as `p', but which returns
   *           the start position of the input it consumed.
   */
  def positioned[Q <% UnitParser](p: => Q) = new Parser[Position] {
    def apply(in: Input) = p(in) match {
      case Success(_, in1) => Success(in.pos, in1) 
      case ns: NoSuccess => ns
    }
  }
  
  /** A parser whose ~ combinator disallows back-tracking.
   */
  trait OnceParser[+T] extends Parser[T] {
    override def ~ [U](q: => Parser[U]): Parser[~[T, U]] = new Parser[~[T, U]] with OnceParser[~[T, U]] {
      def apply(in: Input) = seq(OnceParser.this, commit(q))((x, y) => new ~(x, y))(in)
    }
    override def ~ [Q <% UnitParser](q: => Q): Parser[T] = new Parser[T] with OnceParser[T] {
      def apply(in: Input) = seq(OnceParser.this, commit(q))((x, y) => x)(in)
    }
  }
  
  /** A parser whose ~ combinator disallows back-tracking.
   */
  trait UnitOnceParser extends UnitParser {
    override def ~ [U](q: => Parser[U]): Parser[U] = new Parser[U] with OnceParser[U]{
      def apply(in: Input): ParseResult[U] = seq(UnitOnceParser.this, commit(q))((x, y) => y)(in)
    }
    override def ~ [A <% UnitParser](q: => A): UnitParser = new UnitOnceParser {
      def apply(in: Input): ParseResult[Unit] = seq(UnitOnceParser.this, commit(q))((x, y) => y)(in)
    }
  }
}
