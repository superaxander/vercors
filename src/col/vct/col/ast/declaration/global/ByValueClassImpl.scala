package vct.col.ast.unsorted

import vct.col.ast.{ByValueClass, Expr, TByValueClass, Type}
import vct.col.ast.ops.ByValueClassOps
import vct.col.util.AstBuildHelpers.tt

trait ByValueClassImpl[G] extends ByValueClassOps[G] { this: ByValueClass[G] =>
  override def intrinsicLockInvariant: Expr[G] = tt
  override def classType(typeArgs: Seq[Type[G]]): TByValueClass[G] = TByValueClass[G](this.ref, typeArgs)
}
