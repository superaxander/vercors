package vct.col.ast.unsorted

import vct.col.ast.TByReferenceClass
import vct.col.ast.ops.TByReferenceClassOps
import vct.col.print._

trait TByReferenceClassImpl[G] extends TByReferenceClassOps[G] { this: TByReferenceClass[G] =>
  // override def layout(implicit ctx: Ctx): Doc = ???
}
