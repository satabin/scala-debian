// $Id: scoping3.scala 15104 2008-05-20 10:32:59Z michelou $

object CI {
  trait TreeDisplay {
    type TreeNode <: ITreeNode
    trait ITreeNode {
      def display(): Unit
    }
  }

  trait TreeDisplayExp {
    def getRoot(): TreeNode
    type TreeNode <: ITreeNodeExp
    trait ITreeNodeExp {}
  }

  trait TreeDisplayFinal extends TreeDisplay with TreeDisplayExp {
    type TreeNode <: ITreeNode with ITreeNodeExp
  }
  abstract class SimpleTreeDisplay extends TreeDisplay { self: TreeDisplayFinal =>
    def display() { this.getRoot().display() }
  }
}
