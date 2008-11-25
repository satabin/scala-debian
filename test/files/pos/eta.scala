// $Id: eta.scala 5552 2006-01-17 15:29:35Z michelou $

object test {

def sum(f: Int => Int)(x: Int, y: Int): Int = 0;
def g: (Int => Int) => (Int, Int) => Int = sum;
}
