package vct.main.stages

import hre.progress.Progress
import vct.col.ast.{Program, SimplificationRule}
import vct.col.check.CheckError
import vct.col.feature
import vct.col.newrewrite._
import vct.col.newrewrite.exc._
import vct.col.newrewrite.lang.NoSupportSelfLoop
import vct.col.print.Printer
import vct.col.rewrite.{Generation, InitialGeneration, RewriterBuilder}
import vct.col.util.ExpectedError
import vct.main.Main.TemporarilyUnsupported
import vct.main.stages.Transformation.TransformationCheckError
import vct.main.util.Util
import vct.options.{Backend, Options, PathOrStd}
import vct.parsers.PathAdtImporter
import vct.parsers.transform.BlameProvider
import vct.resources.Resources
import vct.result.VerificationError.SystemError

object Transformation {
  case class TransformationCheckError(errors: Seq[CheckError]) extends SystemError {
    override def text: String =
      "A rewrite caused the AST to no longer typecheck:\n" + errors.map(_.toString).mkString("\n")
  }

  private def writeOutFunctions(m: Map[String, PathOrStd]): Seq[(String, Program[_ <: Generation] => Unit)] =
    m.toSeq.map {
      case (key, out) => (key, (program: Program[_ <: Generation]) => out.write { writer =>
        Printer(writer).print(program)
      })
    }

  private def simplifierFor(path: PathOrStd): RewriterBuilder =
    ApplyTermRewriter.BuilderFor(Util.loadPVLLibraryFile[InitialGeneration](path).declarations.collect {
      case rule: SimplificationRule[InitialGeneration] => rule
    })

  def ofOptions(options: Options): Transformation =
    options.backend match {
      case Backend.Silicon | Backend.Carbon =>
        SilverTransformation(
          adtImporter = PathAdtImporter(options.adtPath),
          onBeforePassKey = writeOutFunctions(options.outputBeforePass),
          onAfterPassKey = writeOutFunctions(options.outputAfterPass),
          simplifyBeforeRelations = options.simplifyPaths.map(simplifierFor),
          simplifyAfterRelations = options.simplifyPathsAfterRelations.map(simplifierFor),
        )
    }
}

class Transformation
(
  val onBeforePassKey: Seq[(String, Program[_ <: Generation] => Unit)],
  val onAfterPassKey: Seq[(String, Program[_ <: Generation] => Unit)],
  val passes: Seq[RewriterBuilder]
) extends ContextStage[Program[_ <: Generation], Seq[ExpectedError], Program[_ <: Generation]] {
  override def friendlyName: String = "Transformation"
  override def progressWeight: Int = 10

  override def runWithoutContext(input: Program[_ <: Generation]): Program[_ <: Generation] = {
    val tempUnsupported = Set[feature.Feature](
      feature.JavaThreads,
      feature.MatrixVector,
      feature.NumericReductionOperator,
      feature.MagicWand,
      feature.Models,
    )

    feature.Feature.examples(input).foreach {
      case (feature, examples) if tempUnsupported.contains(feature) =>
        throw TemporarilyUnsupported(feature.getClass.getSimpleName.stripSuffix("$"), examples.toSeq)
      case (_, _) =>
    }

    var result = input

    Progress.foreach(passes, (pass: RewriterBuilder) => pass.key) { pass =>
      onBeforePassKey.foreach {
        case (key, action) => if(pass.key == key) action(result)
      }

      result = pass().dispatch(result)
      println("\n" + pass.key + " Done")
      result.check match {
        case Nil => // ok
        case errors => throw TransformationCheckError(errors)
      }

      onAfterPassKey.foreach {
        case (key, action) => if(pass.key == key) action(result)
      }
      val resultX = result
      result = PrettifyBlocks().dispatch(result)
    }

    result
  }
}

case class SilverTransformation
(
  adtImporter: ImportADTImporter = PathAdtImporter(Resources.getAdtPath),
  override val onBeforePassKey: Seq[(String, Program[_ <: Generation] => Unit)] = Nil,
  override val onAfterPassKey: Seq[(String, Program[_ <: Generation] => Unit)] = Nil,
  simplifyBeforeRelations: Seq[RewriterBuilder] = Nil,
  simplifyAfterRelations: Seq[RewriterBuilder] = Nil,
) extends Transformation(onBeforePassKey, onAfterPassKey, Seq(
    EncodeBipPermissions,
    EncodeBip,

    // Remove the java.lang.Object -> java.lang.Object inheritance loop
    NoSupportSelfLoop,

    // Delete stuff that may be declared unsupported at a later stage
    FilterSpecIgnore,

    // Normalize AST
    Disambiguate, // Resolve overloaded operators (+, subscript, etc.)

    // Q (RR): Should the next two passes be more in the center of the pass list? Like the exception passes?
    EncodeJavaLangString, // Encode java strings as string objects and interning functions
    EncodeString, // Encode spec string as seq<int>

    CollectLocalDeclarations, // all decls in Scope
    DesugarPermissionOperators, // no PointsTo, \pointer, etc.
    PinCollectionTypes, // no anonymous sequences, sets, etc.
    QuantifySubscriptAny, // no arr[*]
    IterationContractToParBlock,
    PropagateContextEverywhere, // inline context_everywhere into loop invariants
    EncodeArrayValues, // maybe don't target shift lemmas on generated function for \values
    GivenYieldsToArgs,

    CheckProcessAlgebra,

    EncodeCurrentThread,
    EncodeIntrinsicLock,
    InlineApplicables,
    PureMethodsToFunctions,

    // Encode parallel blocks
    EncodeSendRecv,
    EncodeParAtomic,
    ParBlockEncoder,

    // Encode exceptional behaviour (no more continue/break/return/try/throw)
    SpecifyImplicitLabels,
    SwitchToGoto,
    ContinueToBreak,
    EncodeBreakReturn,
    // Resolve side effects including method invocations, for encodetrythrowsignals.
    ResolveExpressionSideEffects,
    EncodeTryThrowSignals,

    // No more classes
    ConstantifyFinalFields,
    ClassToRef,
  ) ++ simplifyBeforeRelations ++ Seq(
    SimplifyQuantifiedRelations,
  ) ++ simplifyAfterRelations ++ Seq(
    // Translate internal types to domains
    ImportADT.withArg(adtImporter),

    ExtractInlineQuantifierPatterns,
    MonomorphizeContractApplicables,

    // Silver compat (basically no new nodes)
    ResolveScale,
    ExplicitADTTypeArgs,
    ForLoopToWhileLoop,
    BranchToIfElse,
    DesugarCollectionOperators,
    EvaluationTargetDummy,

    // Final translation to rigid silver nodes
    SilverIntRatCoercion,
    // PB TODO: PinSilverNodes has now become a collection of Silver oddities, it should be more structured / split out.
    PinSilverNodes,
  ))