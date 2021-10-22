package vct.col.ast

import vct.col.print.Printer
import vct.result.VerificationResult.SystemError

case class Program(declarations: Seq[GlobalDeclaration])(implicit val o: Origin) extends NodeFamily with Declarator {
  def check: Seq[CheckError] =
    checkTrans(CheckContext())

  override def check(context: CheckContext): Seq[CheckError] = Nil
}

trait Node {
  def check(context: CheckContext): Seq[CheckError]
  def o: Origin

  def enterCheckContext(context: CheckContext): CheckContext =
    context

  /* Check children first, so that the check of nodes higher in the tree may depend on the type and correctness of
    subnodes */
  def checkTrans(context: CheckContext): Seq[CheckError] = {
    val innerContext = enterCheckContext(context)
    val childrenErrors = subnodes.flatMap(_.check(innerContext))

    if(childrenErrors.nonEmpty) {
      childrenErrors
    } else {
      check(context)
    }
  }

  def transSubnodes: Stream[Node] =
    this #:: subnodes.toStream.flatMap(_.transSubnodes)

  def subnodes: Seq[Node] = Subnodes.subnodes(this)

  override def toString: String = {
    try {
      val sb = new java.lang.StringBuilder
      val printer = Printer(sb)
      printer.print(this)
      sb.toString
    } catch {
      case _: Throwable => super.toString
    }
  }
}

/*
  Marker trait to indicate this node, or this hierarchy of nodes, always rewrites to itself. This is for example for
  Expr (which always rewrites to an Expr), but also single-purpose nodes, such as a catch clause.
 */
trait NodeFamily extends Node

trait Declarator extends Node {
  def declarations: Seq[Declaration]
  override def enterCheckContext(context: CheckContext): CheckContext =
    context.withScope(declarations.toSet)
}

abstract class ASTStateError extends SystemError