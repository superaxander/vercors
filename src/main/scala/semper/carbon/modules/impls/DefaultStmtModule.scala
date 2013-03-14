package semper.carbon.modules.impls

import semper.carbon.modules.StmtModule
import semper.sil.{ast => sil}
import semper.carbon.boogie._
import semper.carbon.verifier.Verifier

/**
 * The default implementation of a [[semper.carbon.modules.StmtModule]].
 *
 * @author Stefan Heule
 */
class DefaultStmtModule(val verifier: Verifier) extends StmtModule {

  import verifier.expModule._

  def name = "Statement module"
  override def translateStmt(stmt: sil.Stmt): Stmt = {
    val translation = stmt match {
      case sil.LocalVarAssign(lhs, rhs) =>
        Assign(translateExp(lhs).asInstanceOf[Lhs], translateExp(rhs))
      case sil.FieldAssign(lhs, rhs) =>
        ???
      case sil.Fold(e) =>
        ???
      case sil.Unfold(e) =>
        ???
      case sil.Inhale(e) =>
        ???
      case sil.Exhale(e) =>
        // TODO: use the exhale module
        Assert(translateExp(e))
      case sil.MethodCall(m, rcv, args, targets) =>
        ???
      case sil.Seqn(ss) =>
        Seqn(ss map translateStmt)
      case sil.While(cond, invs, locals, body) =>
        ???
      case sil.If(cond, thn, els) =>
        ???
      case sil.Label(name) =>
        ???
      case sil.Goto(target) =>
        ???
      case sil.FreshReadPerm(vars, body) =>
        ???
      case sil.NewStmt(target) =>
        ???
    }
    CommentBlock("-- Translation of statement: " + stmt.toString, translation)
  }
}
