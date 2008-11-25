/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ArrayStack.scala 16056 2008-09-08 12:45:27Z lorch $


package scala.collection.mutable;

private object Utils{
  def growArray(x : Array[AnyRef]) = {
    val y = new Array[AnyRef](x.length * 2);
    Array.copy(x, 0, y, 0, x.length);
    y; 
  }

  def clone(x : Array[AnyRef]) = {
    val y = new Array[AnyRef](x.length);
    Array.copy(x, 0, y, 0, x.length);
    y; 
  }
}

/**
 * Simple stack class backed by an array. Should be significantly faster
 * than the standard mutable stack. 
 *
 * @author David MacIver
 */
class ArrayStack[T] private(private var table : Array[AnyRef],
                            private var index : Int) extends Collection[T]{
  def this() = this(new Array[AnyRef](1), 0);   

  /** 
   * Push an element onto the stack.
   *
   * @param x The element to push
   */ 
  def push(x : T) = {
    if (index == table.length) table = Utils.growArray(table);
    table(index) = x.asInstanceOf[AnyRef];
    index += 1;
  }

  /**
   * Pop the top element off the stack.
   */
  def pop = {
    if (index == 0) error("Stack empty");
    index -= 1;
    val x = table(index).asInstanceOf[T];
    table(index) = null;
    x;
  } 

  /**
   * View the top element of the stack.
   */
  def peek = table(index - 1).asInstanceOf[T]

  /**
   * Duplicate the top element of the stack.
   */
  def dup = push(peek);

  /**
   * Empties the stack.
   */
  def clear = {
    index = 0;
    table = new Array(1);
  }

  /**
   * Empties the stack, passing all elements on it in FIFO order to the 
   * provided function.
   *
   * @param f The function to drain to.
   */
  def drain(f : T => Unit) = while(!isEmpty) f(pop);

  /**
   * Pushes all the provided elements onto the stack.
   *
   * @param x The source of elements to push
   */
  def ++=(x : Iterable[T]) = x.foreach(this +=(_))


  /**
   * Pushes all the provided elements onto the stack.
   *
   * @param x The source of elements to push
   */
  def ++=(x : Iterator[T]) = x.foreach(this +=(_))

  /**
   *  Alias for push.
   * 
   *  @param x The element to push
   */
  def +=(x : T) = push(x);
 


  /**
   * Pop the top two elements off the stack, apply f to them and push the result
   * back on to the stack.
   * 
   * @param f The combining function
   */
  def combine(f : (T, T) => T) = push(f(pop, pop));
  
  /**
   * Repeatedly combine the top elements of the stack until the stack contains only
   * one element.
   */
  def reduceWith(f : (T, T) => T) = while(size > 1) combine(f)     

  def size = index;

  /**
   * Evaluates the expression, preserving the contents of the stack so that 
   * any changes the evaluation makes to the stack contents will be undone after
   * it completes.
   *
   * @param action The action to run.
   */
  def preserving[T](action : => T) = {
    val oldIndex = index;
    val oldTable = Utils.clone(table);

    try{
      action;
    } finally {
      index = oldIndex;
      table = oldTable;
    }
  }

  override def isEmpty = index == 0;

  /**
   * Iterates over the stack in fifo order.
   */
  def elements = new Iterator[T]{
    var currentIndex = index;
    def hasNext = currentIndex > 0;
    def next = {
      currentIndex -= 1;
      table(currentIndex).asInstanceOf[T];
    }
  }

  override def foreach(f : T => Unit){
    var currentIndex = index;
    while(currentIndex > 0){
      currentIndex -= 1;
      f(table(currentIndex).asInstanceOf[T]);
    }
  }

  override def clone = new ArrayStack[T](Utils.clone(table), index);
}
