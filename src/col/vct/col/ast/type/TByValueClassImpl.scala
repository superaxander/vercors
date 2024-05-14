package vct.col.ast.unsorted

import vct.col.ast.TByValueClass
import vct.col.ast.ops.TByValueClassOps
import vct.col.print._

trait TByValueClassImpl[G] extends TByValueClassOps[G] { this: TByValueClass[G] =>
  // override def layout(implicit ctx: Ctx): Doc = ???
}
