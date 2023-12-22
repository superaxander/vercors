package vct.col.ast.statement.terminal

import vct.col.ast.Fork
import vct.col.print.{Ctx, Doc, Show, Text}
import vct.col.ast.ops.ForkOps

trait ForkImpl[G] extends ForkOps[G] { this: Fork[G] =>
  def layoutSpec(implicit ctx: Ctx): Doc =
    Text("fork") <+> obj <> ";"

  override def layout(implicit ctx: Ctx): Doc = Doc.inlineSpec(Show.lazily(layoutSpec(_)))
}