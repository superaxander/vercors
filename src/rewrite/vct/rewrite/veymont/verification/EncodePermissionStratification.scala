package vct.rewrite.veymont.verification

import com.typesafe.scalalogging.LazyLogging
import hre.util.ScopedStack
import vct.col.ast._
import vct.col.origin._
import vct.col.ref.Ref
import vct.col.rewrite.{Generation, Rewriter, RewriterBuilderArg}
import vct.col.util.AstBuildHelpers._
import vct.col.util.SuccessionMap
import vct.result.VerificationError.UserError
import vct.rewrite.veymont.{InferEndpointContexts, VeymontContext}
import vct.rewrite.veymont.verification.EncodePermissionStratification.{
  ForwardAssertFailedToDeref,
  ForwardExhaleFailedToChorRun,
  ForwardInvocationFailureToDeref,
  NoEndpointContext,
}

import scala.collection.{mutable => mut}

object EncodePermissionStratification extends RewriterBuilderArg[Boolean] {
  override def key: String = "encodePermissionStratification"
  override def desc: String =
    "Encodes stratification of permissions by wrapping each permission in an opaque predicate, guarding the permission using an endpoint reference."

  case class ForwardExhaleFailedToChorRun(run: ChorRun[_])
      extends Blame[ExhaleFailed] {
    override def blame(error: ExhaleFailed): Unit =
      run.blame.blame(ChorRunPreconditionFailed(None, error.failure, run))
  }

  case class ForwardInvocationFailureToDeref(deref: Deref[_])
      extends Blame[InvocationFailure] {
    override def blame(error: InvocationFailure): Unit =
      deref.blame.blame(InsufficientPermission(deref))
  }

  case class ForwardAssertFailedToDeref(deref: Deref[_])
      extends Blame[AssertFailed] {
    override def blame(error: AssertFailed): Unit =
      deref.blame.blame(InsufficientPermission(deref))
  }

  case class NoEndpointContext(node: Node[_]) extends UserError {
    override def code = "noEndpointContext"
    override def text =
      node.o.messageInContext("There is no endpoint context inferrable here")
  }
}

case class EncodePermissionStratification[Pre <: Generation](
    generatePermissions: Boolean
) extends Rewriter[Pre] with VeymontContext[Pre] with LazyLogging {

  val inChor = ScopedStack[Boolean]()

  lazy val specializedApplicables: Seq[Applicable[Pre]] =
    findInContext {
      case inv: AnyFunctionInvocation[Pre] => inv.ref.decl
      case inv: MethodInvocation[Pre] => inv.ref.decl
      case app: ApplyAnyPredicate[Pre] => app.ref.decl
    }.toSeq

  // Given a function f that finds Nodes inside nodes, findInContext keeps applying f
  // to nodes that f itself finds, until no more new nodes are found. The search is started
  // by first applying f to all endpointexprs and endpoint statements. In addition, the
  // endpoint in the endpoint expr that is at the start of the search, is kept, and attached to each
  // node found later.
  def findInContext[T <: Node[Pre]](
      f: PartialFunction[Node[Pre], T]
  ): mut.LinkedHashSet[T] = {
    val specializations: mut.LinkedHashSet[T] = mut.LinkedHashSet.from(
      mappings
        // For each endpoint expr
        .program.collect {
          // Get all T's from endpoint contexts
          case expr: EndpointExpr[Pre] => expr.collect(f)
          case stmt: EndpointStatement[Pre] => stmt.collect(f)
        }.flatten
    )

    // Do a fixpoint computation, expanding the set of Ts using the selector function f on the Ts we already have
    var changes = true
    while (changes) {
      changes = false
      specializations.flatMap { t => t.collect(f) }.foreach { newT =>
        // If newT is genuinely new, add will return true
        changes = changes || specializations.add(newT)
      }
    }

    specializations
  }

  val specializedApplicableSucc =
    SuccessionMap[Applicable[Pre], Applicable[Post]]()

  // Keeps track of the current anchoring/endpoint context identity expression for the current endpoint context.
  // E.g. within a choreograph, it is EndpointName(succ(endpoint)), within a specialized function it is a local
  // to the endpoint context argument.
  // This is especially important for specializeApplicable: there we specialize each function to be executed in the
  // context of some endpoin. This endpoint reference comes indirectly through one of the arguments of the function,
  // not a literal endpointname expression.
  val specializing = ScopedStack[Expr[Post]]()

  // TODO (RR): I think this key can be simplified - InstanceField always belongs to a particular class,
  //            so the Type could theoretically be omitted?
  //            However: if we ever want to support generics, probably the type should remain there!
  type WrapperPredicateKey = (Type[Pre], InstanceField[Pre])
  val wrapperPredicates = mut
    .LinkedHashMap[WrapperPredicateKey, Predicate[Post]]()

  // TODO (RR): It does not really wrap anymore, rename
  // marker predicate to allow marking field permissions with an endpoint owner
  def wrapperPredicate(objT: Type[Pre], field: InstanceField[Pre])(
      implicit o: Origin
  ): Ref[Post, Predicate[Post]] = {
    val k = (objT, field)
    wrapperPredicates.getOrElseUpdate(
      k, {
        logger.debug(s"Declaring wrapper predicate for $k")
        val endpointArg =
          new Variable[Post](TAnyValue())(o.where(name = "endpoint"))
        val objectArg = new Variable(dispatch(objT))(o.where(name = "obj"))
        new Predicate(Seq(endpointArg, objectArg), None)(o.where(indirect =
          Name
            .names(Name("ep"), Name("owner"), field.o.getPreferredNameOrElse())
        )).declare()
      },
    ).ref
  }

  val readFunctions = mut.LinkedHashMap[WrapperPredicateKey, Function[Post]]()
  def readFunction(obj: Expr[Pre], field: InstanceField[Pre])(
      implicit o: Origin
  ): Ref[Post, Function[Post]] = {
    val k = (obj.t, field)
    val pred = wrapperPredicate(obj.t, field)
    readFunctions.getOrElseUpdate(
      k, {
        logger.debug(s"Declaring read function for $k")
        val endpointArg =
          new Variable[Post](TAnyValue())(o.where(name = "endpoint"))
        val objArg = new Variable(dispatch(obj.t))(o.where(name = "obj"))
        function(
          requires =
            (Value[Post](FieldLocation(objArg.get, succ(field))) &*
              Value(PredicateLocation(
                PredicateApply(pred, Seq(endpointArg.get, objArg.get))
              ))).accounted,
          args = Seq(endpointArg, objArg),
          returnType = dispatch(field.t),
          body = Some(Deref[Post](objArg.get, succ(field))(PanicBlame(
            "Permission is guaranteed by the predicate"
          ))),
          blame = PanicBlame("Contract is guaranteed to hold"),
          contractBlame = PanicBlame("Contract is guaranteed to be satisfiable"),
        )(o.where(indirect =
          Name.names(Name("read"), field.o.getPreferredNameOrElse())
        )).declare()
      },
    ).ref
  }

  case class StripPermissionStratification() extends Rewriter[Pre] {
    override val allScopes: AllScopes[Pre, Post] =
      EncodePermissionStratification.this.allScopes

    override def dispatch(expr: Expr[Pre]): Expr[Post] =
      expr match {
        case ChorPerm(_, loc, perm) =>
          Perm(dispatch(loc), dispatch(perm))(expr.o)
        case ChorExpr(inner) => dispatch(inner)
        case EndpointExpr(_, inner) => dispatch(inner)
        case _ => expr.rewriteDefault()
      }
  }

  override def dispatch(p: Program[Pre]): Program[Post] = {
    mappings.program = p
    super.dispatch(p)
  }

  override def dispatch(decl: Declaration[Pre]): Unit =
    decl match {
      case chor: Choreography[Pre] =>
        implicit val o = chor.o
        val pre = foldStar(
          chor.run.contract.contextEverywhere +:
            unfoldPredicate(chor.run.contract.requires)
        )
        currentChoreography.having(chor) {
          chor.rewrite(preRun =
            Some(Block(
              chor.preRun.map(dispatch).toSeq ++
                Seq(Exhale[Post](StripPermissionStratification().dispatch(pre))(
                  ForwardExhaleFailedToChorRun(chor.run)
                )) :+ Inhale[Post](dispatch(pre))
            ))
          ).succeed(chor)
        }
      case f: Function[Pre] if specializedApplicables.contains(f) =>
        f.rewriteDefault().succeed(f)
        specializeApplicable(f)
      case f: InstanceFunction[Pre] if specializedApplicables.contains(f) =>
        f.rewriteDefault().succeed(f)
        specializeApplicable(f)
      case m: InstanceMethod[Pre] if specializedApplicables.contains(m) =>
        m.rewriteDefault().succeed(m)
        specializeApplicable(m)
      case p: Predicate[Pre] if specializedApplicables.contains(p) =>
        p.rewriteDefault().succeed(p)
        specializeApplicable(p)
      case p: InstancePredicate[Pre] if specializedApplicables.contains(p) =>
        p.rewriteDefault().succeed(p)
        specializeApplicable(p)
      case _ => super.dispatch(decl)
    }

  def specializeApplicable(app: Applicable[Pre]): Unit = {
    assert(app match {
      case _: InstanceMethod[Pre] | _: InstanceFunction[Pre] |
          _: Function[Pre] | _: Predicate[Pre] | _: InstancePredicate[Pre] =>
        true
      case _ => false
    })

    def nameOrigin(f: Applicable[Pre]): Origin =
      f.o.where(indirect =
        Name.names(f.o.getPreferredNameOrElse(), Name("Strat"))
      )

    def predicateNameOrigin(f: AbstractPredicate[Pre]): Origin =
      f.o.where(indirect =
        Name.names(Name("ep"), Name("owner"), f.o.getPreferredNameOrElse())
      )

    variables.scope {
      val endpointCtxVar =
        new Variable[Post](dispatch(TAnyValue()))(
          app.o.where(indirect = Name.strings("endpoint", "ctx"))
        )

      // Make sure plain perms are rewritten into wrapped perms
      specializing.having(endpointCtxVar.get(app.o)) {
        val newF: Applicable[Post] =
          app match {
            case f: InstanceFunction[Pre] =>
              val newF = f.rewrite(
                args = endpointCtxVar +: variables.dispatch(f.args),
                o = nameOrigin(f),
              )
              newF.declare()
            case f: Function[Pre] =>
              val newF = f.rewrite(
                args = endpointCtxVar +: variables.dispatch(f.args),
                o = nameOrigin(f),
              )
              newF.declare()
            case m: InstanceMethod[Pre] if m.pure =>
              val newM = m.rewrite(
                args = endpointCtxVar +: variables.dispatch(m.args),
                o = nameOrigin(m),
              )
              newM.declare()
            case m: InstanceMethod[Pre] if !m.pure =>
              val newM = m.rewrite(
                args = endpointCtxVar +: variables.dispatch(m.args),
                body = None,
                o = nameOrigin(m),
              )
              newM.declare()
            case p: InstancePredicate[Pre] =>
              val newP = p.rewrite(
                args = endpointCtxVar +: variables.dispatch(p.args),
                o = predicateNameOrigin(p),
              )
              newP.declare()
            case p: Predicate[Pre] =>
              val newP = p.rewrite(
                args = endpointCtxVar +: variables.dispatch(p.args),
                o = predicateNameOrigin(p),
              )
              newP.declare()
          }
        specializedApplicableSucc(app) = newF
      }
    }
  }

  // Warning: this does not integrate well with generics. To support generic endpoints and generic
  // methods fully, generic parameters need to be added to the predicates generated, such that stating
  // the actual type of the object dereferenced in the wrapper predicate can be delayed as long as possible.
  // This is because we need the type of the field (e.g. through loc.obj.t), but to actually instantiate
  // this possibly generic type you need a type environment that is lost at this point. As rewriting is nested (e.g.
  // endpoint x calls function a, that calls function b, that calls function c, which we are now specializing
  // for endpoint x), it is difficult to maintain the proper type environment.
  def makeWrappedPerm(
      loc: FieldLocation[Pre],
      perm: Expr[Pre],
      endpointExpr: Expr[Post],
  )(implicit o: Origin): Expr[Post] = {
    if (perm == ReadPerm[Pre]()) {
      (Value(dispatch(loc)) &* Value(PredicateLocation(PredicateApply(
        wrapperPredicate(loc.obj.t, loc.field.decl),
        Seq(endpointExpr, dispatch(loc.obj)),
      ))))
    } else {
      Perm(dispatch(loc), dispatch(perm)) &* Perm(
        PredicateLocation(PredicateApply(
          wrapperPredicate(loc.obj.t, loc.field.decl),
          Seq(endpointExpr, dispatch(loc.obj)),
        )),
        dispatch(perm),
      )
    }
  }

  def specializePredicateLocation(
      inv: ApplyAnyPredicate[Pre]
  ): ApplyAnyPredicate[Post] =
    inv match {
      case inv: PredicateApply[Pre] =>
        inv.rewrite(
          ref = specializedApplicableSucc.ref(inv.ref.decl),
          args = specializing.top +: inv.args.map(dispatch),
        )
      case inv: InstancePredicateApply[Pre] =>
        inv.rewrite(
          ref = specializedApplicableSucc.ref(inv.ref.decl),
          args = specializing.top +: inv.args.map(dispatch),
        )
      case inv: CoalesceInstancePredicateApply[Pre] =>
        inv.rewrite(
          ref = specializedApplicableSucc.ref(inv.ref.decl),
          args = specializing.top +: inv.args.map(dispatch),
        )
    }

  // Note that the encoding for stratified predicates is incomplete w.r.t. \chor. For example,
  // "assert (\chor perm(P()) == 1)" can never succeed, as the transparent part of the predicate can never
  // be bigger than 1/2. By putting half of the predicate inside the marker/specialized predicate,
  // and leaving half of the predicate untouched, we gain the ability to call functions with predicates in their
  // preconditions within \chor. If the predicates were instead fully wrapped, giving a sound encoding,
  // calling functions that require predicates within \chor would not be possible.
  //
  // This makes for easier prototyping. The downside is that if exact permission amounts
  // are important, the user has to fiddle with dividing amounts by two here and there. We could accomodate for that
  // in the transformation, but as this is a rare edge case, I chose to leave it kind of unsound, for the sake of
  // keeping this transformation a bit simpler.
  def specializePredicateLocation(target: FoldTarget[Pre]): FoldTarget[Post] =
    target match {
      case app: ScaledPredicateApply[Pre] =>
        app.rewrite(
          apply = specializePredicateLocation(app.apply),
          perm = RatDiv(dispatch(app.perm), const(2)(app.o))(NoZeroDiv)(app.o),
        )
      case app: ValuePredicateApply[Pre] =>
        app.rewrite(apply = specializePredicateLocation(app.apply))
      case AmbiguousFoldTarget(_) => ??? // Shouldn't occur at this stage
    }

  // Incomplete encoding; see specializePredicateLocation
  def makeStratifiedPredicate(loc: PredicateLocation[Pre], perm: Expr[Pre])(
      implicit o: Origin
  ): Expr[Post] = {
    Perm[Post](dispatch(loc), RatDiv(dispatch(perm), const(2))(NoZeroDiv)) &*
      Perm(
        PredicateLocation(specializePredicateLocation(loc.inv)),
        RatDiv(dispatch(perm), const(2))(NoZeroDiv),
      )
  }

  override def dispatch(expr: Expr[Pre]): Expr[Post] =
    expr match {
      // TODO (RR):
      //  Make a check in chorperm and endpointexpr to ensure that when nesting these types of nodes, they
      //  all agree on one endpoint. So no nesting endpointexprs and chorperms with different endpoints.
      //  For now we assume here that's all well and good.
      //  Or just get rid of chorperm anyway
      case cp @ ChorPerm(Ref(endpoint), loc: FieldLocation[Pre], perm) =>
        specializing.having(EndpointName[Post](succ(cp.endpoint.decl))(cp.o)) {
          makeWrappedPerm(
            loc,
            perm,
            EndpointName[Post](succ(endpoint))(expr.o),
          )(expr.o)
        }

      case cp @ ChorPerm(Ref(endpoint), loc: PredicateLocation[Pre], perm) =>
        specializing.having(EndpointName[Post](succ(endpoint))(cp.o)) {
          makeStratifiedPredicate(loc, perm)(expr.o)
        }

      case Perm(loc: FieldLocation[Pre], perm) if specializing.nonEmpty =>
        makeWrappedPerm(loc, perm, specializing.top)(expr.o)

      case Perm(loc: PredicateLocation[Pre], perm) if specializing.nonEmpty =>
        makeStratifiedPredicate(loc, perm)(expr.o)

      case Value(loc: FieldLocation[Pre]) if specializing.nonEmpty =>
        makeWrappedPerm(loc, ReadPerm()(expr.o), specializing.top)(expr.o)

      case unfolding @ Unfolding(res: FoldTarget[Pre], inner)
          if specializing.nonEmpty =>
        implicit val o = unfolding.o
        val halfRes =
          res match {
            case app: ScaledPredicateApply[Pre] =>
              // Incomplete encoding; see specializePredicateLocation
              app
                .rewrite(perm = RatDiv(dispatch(app.perm), const(2))(NoZeroDiv))
            case app => app.rewriteDefault()
          }

        unfolding.rewrite(
          res = halfRes,
          body =
            Unfolding[Post](
              res = specializePredicateLocation(res),
              body = dispatch(inner),
            )(unfolding.blame)(unfolding.o),
        )

      case EndpointExpr(Ref(endpoint), inner) =>
        specializing.having(EndpointName[Post](succ(endpoint))(expr.o)) {
          dispatch(inner)
        }

      case deref @ Deref(obj, Ref(field)) if specializing.nonEmpty =>
        implicit val o = expr.o
        functionInvocation(
          ref = readFunction(obj, field)(expr.o),
          args = Seq(specializing.top, dispatch(obj)),
          blame = ForwardInvocationFailureToDeref(deref),
        )

      case ChorExpr(inner) => inChor.having(true) { dispatch(inner) }

      // Generate an invocation to the unspecialized function version if we're inside a \chor
      // The natural successor of the function will be the unspecialized one
      case inv: FunctionInvocation[Pre] if inChor.topOption.contains(true) =>
        inv.rewriteDefault()
      case inv: InstanceFunctionInvocation[Pre]
          if inChor.topOption.contains(true) =>
        inv.rewriteDefault()

      case inv: FunctionInvocation[Pre] if specializing.nonEmpty =>
        inv.rewrite(
          ref = specializedApplicableSucc.ref(inv.ref.decl),
          args = specializing.top +: inv.args.map(dispatch),
        )
      case inv: InstanceFunctionInvocation[Pre] if specializing.nonEmpty =>
        inv.rewrite(
          ref = specializedApplicableSucc.ref(inv.ref.decl),
          args = specializing.top +: inv.args.map(dispatch),
        )
      case inv: MethodInvocation[Pre] if specializing.nonEmpty =>
        inv.rewrite(
          ref = specializedApplicableSucc.ref(inv.ref.decl),
          args = specializing.top +: inv.args.map(dispatch),
        )
      case _ => expr.rewriteDefault()
    }

  override def dispatch(statement: Statement[Pre]): Statement[Post] =
    statement match {
      case EndpointStatement(None, Assign(_, _)) =>
        throw NoEndpointContext(statement)
      case EndpointStatement(
            Some(Ref(endpoint)),
            assign @ Assign(deref @ Deref(obj, Ref(field)), _),
          ) =>
        implicit val o = statement.o
        val apply = {
          val newEndpoint: Ref[Post, Endpoint[Post]] = succ(endpoint)
          val ref = wrapperPredicate(obj.t, field)
          PredicateApply(
            ref,
            Seq(
              EndpointName(newEndpoint),
              specializing.having(EndpointName[Post](succ(endpoint))) {
                dispatch(obj)
              },
            ),
          )
        }
        val intermediate =
          new Variable(dispatch(assign.value.t))(
            assign.o.where(name = "intermediate")
          )
        specializing.having(EndpointName[Post](succ(endpoint))) {
          Scope(
            Seq(intermediate),
            Block(Seq(
              assignLocal(intermediate.get, dispatch(assign.value)),
              Assert(Perm(PredicateLocation(apply), WritePerm()))(
                ForwardAssertFailedToDeref(deref)
              ),
              assign.rewrite(
                // Use rewriteDefault to prevent triggering rewriting into a read function. We just want the
                // raw field access here.
                target = deref.rewriteDefault(),
                value = intermediate.get,
              ),
            )),
          )
        }
      case EndpointStatement(Some(Ref(endpoint)), assert: Assert[Pre]) =>
        specializing.having(EndpointName[Post](succ(endpoint))(statement.o)) {
          assert.rewriteDefault()
        }
      case EndpointStatement(Some(Ref(endpoint)), eval: Eval[Pre]) =>
        specializing.having(EndpointName[Post](succ(endpoint))(statement.o)) {
          eval.rewriteDefault()
        }
      case _ => statement.rewriteDefault()
    }
}
