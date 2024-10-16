package vct.col.ast.declaration.global

import vct.col.ast.{ByReferenceClass, TByReferenceClass, Type}
import vct.col.ast.ops.ByReferenceClassOps
import vct.col.print._
import vct.col.util.AstBuildHelpers.tt

trait ByReferenceClassImpl[G] extends ByReferenceClassOps[G] {
  this: ByReferenceClass[G] =>
  override def classType(typeArgs: Seq[Type[G]]): TByReferenceClass[G] =
    TByReferenceClass[G](this.ref, typeArgs)

  override def layoutLockInvariant(implicit ctx: Ctx): Doc =
    if (intrinsicLockInvariant == tt) { Empty }
    else {
      Doc.spec(Show.lazily { c: Ctx =>
        implicit val ctx: Ctx = c
        Text("lock_invariant") <+>
          Nest(intrinsicLockInvariant.show <> ";" <+/> Empty)
      })
    }
}
