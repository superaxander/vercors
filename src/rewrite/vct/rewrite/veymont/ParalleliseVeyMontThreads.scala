package vct.rewrite.veymont

import hre.util.ScopedStack
import vct.col.ast.RewriteHelpers.{RewriteDeref, RewriteJavaClass}
import vct.col.ast.{Assert, Assign, Block, BooleanValue, Branch, Class, ClassDeclaration, Declaration, Deref, DerefVeyMontThread, Eval, Expr, InstanceField, InstanceMethod, JavaClass, JavaTClass, Loop, Node, Program, RunMethod, Scope, Statement, TClass, ThisObject, Type, VeyMontAssignExpression, VeyMontCommExpression, VeyMontSeqProg, VeyMontThread}
import vct.col.origin.{Origin, PreferredNameOrigin}
import vct.col.ref.Ref
import vct.col.rewrite.{Generation, Rewriter, RewriterBuilderArg}
import vct.col.util.SuccessionMap
import vct.result.VerificationError.UserError
import vct.rewrite.veymont.ParalleliseVeyMontThreads.{ChannelClassOrigin, ParalliseVeyMontThreadsError, ThreadClassOrigin}

object ParalleliseVeyMontThreads extends RewriterBuilderArg[JavaClass[_]] {
  override def key: String = "ParalleliseVeyMontThreads"

  override def desc: String = "Generate classes for VeyMont threads in parallel program"

  case class ParalliseVeyMontThreadsError(node : Node[_], msg: String) extends UserError {
    override def code: String = "ParalleliseVeyMontThreadsError"

    override def text: String = node.o.messageInContext(msg)
  }

  case class ThreadClassOrigin(thread: VeyMontThread[_]) extends Origin {
    override def preferredName: String = thread.o.preferredName.toUpperCase() + "Thread"

    override def context: String = thread.o.context

    override def inlineContext: String = thread.o.inlineContext

    override def shortPosition: String = thread.o.shortPosition
  }

  case class ChannelClassOrigin(channelName: String, assign: Statement[_]) extends Origin {
    override def preferredName: String = channelName

    override def context: String = assign.o.context

    override def inlineContext: String = assign.o.inlineContext

    override def shortPosition: String = assign.o.shortPosition
  }
}

case class ParalleliseVeyMontThreads[Pre <: Generation](channelClass: JavaClass[_]) extends Rewriter[Pre] {

  val inSeqProg: ScopedStack[Map[VeyMontThread[Pre],InstanceField[Post]]] = ScopedStack()
  val threadBuildingBlocks: ScopedStack[ThreadBuildingBlocks[Pre]] = ScopedStack()
  val seqProgSucc: SuccessionMap[VeyMontThread[Pre],Class[Post]] = SuccessionMap()

  override def dispatch(decl : Declaration[Pre]) : Unit = {
    decl match {
      case seqProg: VeyMontSeqProg[Pre] =>
        val threadFieldMap = seqProg.threads.map(t => (t,new InstanceField[Post](dispatch(t.threadType), Set.empty)(t.o))).toMap
        val channelInfo = collectChannelsFromRun(seqProg) ++ collectChannelsFromMethods(seqProg)
        val channelClasses = generateChannelClasses(channelInfo)
        val channelFields = getChannelFields(channelInfo, channelClasses)
        threadBuildingBlocks.having(new ThreadBuildingBlocks(seqProg.runMethod, seqProg.methods,threadFieldMap,channelFields)) {
          seqProg.threads.foreach(dispatch)
        }
      case thread: VeyMontThread[Pre] => {
        if(threadBuildingBlocks.nonEmpty) {
          val threadRes: ThreadBuildingBlocks[Pre] = threadBuildingBlocks.top
          val threadField = threadRes.threadFieldMap(thread)
          val channelFieldsForThread = threadRes.channelFields.view.filterKeys {
            _.decl == thread
          }.values.toSeq
          val threadRun = getThreadRunFromDecl(thread, threadRes.runMethod)
          val threadMethods: Seq[ClassDeclaration[Post]] = threadRes.methods.map(getThreadMethodFromDecl(thread))
          classDeclarations.scope {
            //classDeclarations.collect {
            val threadClass = new Class[Post](
              (threadField +: channelFieldsForThread) ++ (threadRun +: threadMethods),
              Seq(),
              BooleanValue(true)(thread.o))(ThreadClassOrigin(thread))
            globalDeclarations.declare(threadClass)
            seqProgSucc.update(thread,threadClass)
          }
        } else rewriteDefault(thread)
      }
      //case m: InstanceMethod[Pre] => getThreadMethod(m)
      //case r: RunMethod[Pre] => getThreadRunMethod(r)
      case other => rewriteDefault(other)
    }
  }

  private def getThreadMethodFromDecl(thread: VeyMontThread[Pre])(decl: ClassDeclaration[Pre]): InstanceMethod[Post]  = decl match {
    case m: InstanceMethod[Pre] => getThreadMethod(m)
    case _ => throw ParalliseVeyMontThreadsError(thread, "Methods of seq_program need to be of type InstanceMethod")
  }

  private def getThreadRunFromDecl(thread: VeyMontThread[Pre], decl: ClassDeclaration[Pre]): RunMethod[Post] = decl match {
    case m: RunMethod[Pre] => getThreadRunMethod(m)
    case _ => throw ParalliseVeyMontThreadsError(thread, "RunMethod expected in seq_program")
  }

  private def getChannelFields(channelInfo: Seq[ChannelInfo[Pre]], channelClasses: Map[Type[Pre],JavaClass[Post]]): Map[Ref[Pre, VeyMontThread[Pre]],InstanceField[Post]] = {
    channelInfo.flatMap { chanInfo =>
      val chanField = new InstanceField[Post](JavaTClass(channelClasses(chanInfo.channelType).ref,Seq.empty), Set.empty)(ChannelClassOrigin(chanInfo.channelName,chanInfo.comExpr.assign))
      Set((chanInfo.comExpr.receiver, chanField), (chanInfo.comExpr.sender, chanField)) }.toMap
  }

  private def generateChannelClasses(channelInfo: Seq[ChannelInfo[Pre]]) : Map[Type[Pre],JavaClass[Post]] = {
    val channelTypes = channelInfo.map(_.channelType).toSet
    channelTypes.map(channelType =>
      channelType -> {
        val chanClassPre = channelClass.asInstanceOf[JavaClass[Pre]]
        new RewriteJavaClass[Pre, Post](chanClassPre)(new ChannelClassGenerator[Pre](channelType)).rewrite(decls = classDeclarations.collect {
          chanClassPre.decls.foreach(d => dispatch(d))
        }._1)
      }
    ).toMap
  }//new ChannelClassGenerator(channelType).globalDeclarations.dispatch(channelClass.unsafeTransmuteGeneration[JavaClass, Pre])

  private def collectChannelsFromRun(seqProg: VeyMontSeqProg[Pre]) =
    seqProg.runMethod match {
      case r: RunMethod[Pre] => getChannelsFromBody(r.body, r)
      case other => throw ParalliseVeyMontThreadsError(other, "seq_program run method expected")
    }

  private def collectChannelsFromMethods(seqProg: VeyMontSeqProg[Pre]) =
    seqProg.methods.flatMap {
      case m: InstanceMethod[Pre] => getChannelsFromBody(m.body, m)
      case other => throw ParalliseVeyMontThreadsError(other, "seq_program method expected")
    }

  private def getChannelsFromBody(body: Option[Statement[Pre]], method: ClassDeclaration[Pre]) = {
    body match {
      case None => throw ParalliseVeyMontThreadsError(method, "Method in seq_program needs to have non-empty body")
      case Some(b) => getChannelNamesAndTypes(b)
    }
  }

  private def getChannelNamesAndTypes(s: Statement[Pre]): Seq[ChannelInfo[Pre]] = {
    s.collect { case e@VeyMontCommExpression(recv, sender, assign) =>
      new ChannelInfo(e,recv.decl.threadType, recv.decl.o.preferredName + sender.decl.o.preferredName + "Channel")
    }
  }

  private def getThreadMethod(method : InstanceMethod[Pre]): InstanceMethod[Post] = {
    new InstanceMethod[Post](
        dispatch(method.returnType),
        variables.dispatch(method.args),
        variables.dispatch(method.outArgs),
        variables.dispatch(method.typeArgs),
        method.body.map(dispatch),
        dispatch(method.contract))(method.blame)(method.o)
  }

  private def getThreadRunMethod(run: RunMethod[Pre]): RunMethod[Post] = {
    new RunMethod[Post](
      run.body.map(dispatch),
      dispatch(run.contract))(run.blame)(run.o)
  }

  override def dispatch(node: Expr[Pre]): Expr[Post] = {
    if(threadBuildingBlocks.nonEmpty) {
      val threadFieldMap = threadBuildingBlocks.top.threadFieldMap
      node match {
        case d: Deref[Pre] => d.obj match {
          case t: DerefVeyMontThread[Pre] =>
            threadFieldMap.find{case (thread,_) => t.ref.decl == thread} match {
              case Some((thread,threadField)) =>
                d.rewrite(
                  obj = Deref(ThisObject(seqProgSucc.ref[Post, Class[Post]](thread))(thread.o), threadField.ref[InstanceField[Post]])(null)(d.o)
                )
              case _ => rewriteDefault(node)
            }
        }
        case _ => rewriteDefault(node)
      }
    } else rewriteDefault(node)
  }


    override def dispatch(st : Statement[Pre]) : Statement[Post] = {
      if (inSeqProg.nonEmpty) {
        st match {
          case VeyMontCommExpression(recv,sender,assign) => rewriteDefault(assign)
          case VeyMontAssignExpression(_, _) => rewriteDefault(st)
          case Assign(_, _) => rewriteDefault(st)
          case Branch(_) => rewriteDefault(st)
          case Loop(_, _, _, _, _) => rewriteDefault(st)
          case Scope(_, _) => rewriteDefault(st)
          case Block(_) => rewriteDefault(st)
          case Eval(expr) => Eval(rewriteDefault(expr))(st.o)
          case Assert(_) => rewriteDefault(st)
          case _ => throw ParalliseVeyMontThreadsError(st, "Statement not allowed in seq_program")
        }
      } else rewriteDefault(st)
    }

}
