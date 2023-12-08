package vct.col.ast.helpers.defn

import vct.col.ast.helpers.defn.Proto._
import vct.col.ast.structure

import scala.annotation.tailrec
import scala.collection.mutable

object ProtoNaming {
  val auxBase = Seq("vct", "col", "ast", "serialize")

  def explode(name: String): Seq[String] =
    name.split("_").toIndexedSeq.flatMap(explodeCamel(_))

  @tailrec
  def explodeCamel(name: String, acc: Seq[String] = Nil): Seq[String] = {
    if (name.isEmpty) return acc

    val (leftUpper, rem0) = name.span(_.isUpper)

    if (leftUpper.length <= 1) {
      // FunctionInvocation -> Function +: explodeCamel("Invocation")
      val (leftLower, rem1) = rem0.span(!_.isUpper)
      explodeCamel(rem1, acc :+ (leftUpper + leftLower).toLowerCase)
    } else {
      // ADTFunctionInvocation -> ADT +: explodeCamel("FunctionInvocation")
      explodeCamel(s"${leftUpper.last}${rem0}", acc :+ leftUpper.init.toLowerCase)
    }
  }

  private val bannedParts = Set(
    Seq("instance", "of"),
    Seq("class"),
    Seq("empty"),
    Seq("assert"),
  )

  def parts(name: String): Seq[String] = {
    val parts = explode(name)
    if (bannedParts.contains(parts))
      "vct" +: parts
    else parts
  }

  def snake(name: String): String =
    parts(name).mkString("_")

  def ucamel(name: String): String =
    parts(name).map(_.capitalize).mkString("")

  def getType(t: structure.Name): MessageType =
    MessageType(Seq(ucamel(t.parts.last)))

  case class PrimitiveTypeResult(t: PrimitiveType, auxs: Seq[Message] = Nil, imports: Seq[Seq[String]] = Nil) {
    def result(trafo: PrimitiveType => Type): TypeResult =
      TypeResult(trafo(t), auxs, imports)
  }

  case class TypeResult(t: Type, auxs: Seq[Message] = Nil, imports: Seq[Seq[String]] = Nil)

  def typeText(t: structure.Type): String = t match {
    case structure.Type.Node(name) => ucamel(name.base)
    case structure.Type.Declaration(name) => typeText(structure.Type.Node(name))
    case structure.Type.DeclarationSeq(name) => typeText(structure.Type.Seq(structure.Type.Node(name)))
    case structure.Type.Ref(node) => s"Ref_${typeText(node)}"
    case structure.Type.MultiRef(node) => s"Ref_${typeText(node)}"
    case structure.Type.Tuple(args) => s"Tuple${args.size}_${args.map(typeText).mkString("_")}"
    case structure.Type.Option(arg) => s"Option_${typeText(arg)}"
    case structure.Type.Seq(arg) => s"Seq_${typeText(arg)}"
    case structure.Type.Either(left, right) => s"Either_${typeText(left)}_${typeText(right)}"
    case other: structure.Type.PrimitiveType => other.getClass.getSimpleName.replace("$", "")
  }

  private val _getTupleAux = mutable.Map[Seq[structure.Type], PrimitiveTypeResult]()

  def getTupleAux(ts: Seq[structure.Type]): PrimitiveTypeResult =
    _getTupleAux.getOrElseUpdate(ts, {
      val fieldTypeResults = ts.map(getType)
      val auxs = fieldTypeResults.flatMap(_.auxs)
      val imports = fieldTypeResults.flatMap(_.imports)
      val fieldTypes = fieldTypeResults.map(_.t)
      val fields = MessageFields(fieldTypes.zipWithIndex.map {
        case (t, i) => Field(s"v${i + 1}", i + 1, t)
      })
      val message = Message(Seq(typeText(structure.Type.Tuple(ts))), fields)
      PrimitiveTypeResult(MessageType(message.name), auxs :+ message, imports)
    })

  private val _getOptionAux = mutable.Map[structure.Type, PrimitiveTypeResult]()

  def getOptionAux(t: structure.Type): PrimitiveTypeResult =
    _getOptionAux.getOrElseUpdate(t, {
      val typeResult = getPrimitiveType(t)
      val field = Field("value", 1, Option(typeResult.t))
      val message = Message(Seq("Option_" + typeText(t)), MessageFields(Seq(field)))
      PrimitiveTypeResult(MessageType(message.name), typeResult.auxs :+ message, typeResult.imports)
    })

  private val _getSeqAux = mutable.Map[structure.Type, PrimitiveTypeResult]()

  def getSeqAux(t: structure.Type): PrimitiveTypeResult =
    _getSeqAux.getOrElseUpdate(t, {
      val typeResult = getPrimitiveType(t)
      val field = Field("value", 1, Option(typeResult.t))
      val message = Message(Seq("Seq_" + typeText(t)), MessageFields(Seq(field)))
      PrimitiveTypeResult(MessageType(message.name), typeResult.auxs :+ message, typeResult.imports)
    })

  private val _getEitherAux = mutable.Map[(structure.Type, structure.Type), PrimitiveTypeResult]()

  def getEitherAux(left: structure.Type, right: structure.Type): PrimitiveTypeResult =
    _getEitherAux.getOrElseUpdate((left, right), {
      val leftTypeResult = getPrimitiveType(left)
      val rightTypeResult = getPrimitiveType(right)
      val leftField = Field("left", 1, Required(leftTypeResult.t))
      val rightField = Field("right", 2, Required(rightTypeResult.t))
      val fields = MessageOneOf("either", Seq(leftField, rightField))
      val message = Message(Seq(s"Either_${typeText(left)}_${typeText(right)}"), fields)
      PrimitiveTypeResult(
        MessageType(message.name),
        (leftTypeResult.auxs ++ rightTypeResult.auxs) :+ message,
        leftTypeResult.imports ++ rightTypeResult.imports
      )
    })

  def getStandardType(name: String): PrimitiveTypeResult =
    PrimitiveTypeResult(MessageType(auxBase :+ name), imports = Seq(auxBase :+ name))

  private val _getPrimitiveType = mutable.Map[structure.Type, PrimitiveTypeResult]()

  def getPrimitiveType(t: structure.Type): PrimitiveTypeResult =
    _getPrimitiveType.getOrElseUpdate(t, {
      t match {
        case structure.Type.Node(name) =>
          PrimitiveTypeResult(getType(name.tailName), imports = Seq(name.tailName.parts))
        case structure.Type.Declaration(name) =>
          PrimitiveTypeResult(getType(name.tailName), imports = Seq(name.tailName.parts))
        case structure.Type.Ref(_) | structure.Type.MultiRef(_) => getStandardType("Ref")
        case structure.Type.Tuple(args) => getTupleAux(args)

        case structure.Type.Nothing => PrimitiveTypeResult(Bool)
        case structure.Type.Unit => PrimitiveTypeResult(Bool)
        case structure.Type.String => PrimitiveTypeResult(String)
        case structure.Type.BigInt => getStandardType("BigInt")
        case structure.Type.BigDecimal => getStandardType("BigDecimal")
        case structure.Type.BitString => getStandardType("BitString")
        case structure.Type.ExpectedError => getStandardType("ExpectedError")
        case structure.Type.Boolean => PrimitiveTypeResult(Bool)
        case structure.Type.Byte => PrimitiveTypeResult(Int)
        case structure.Type.Short => PrimitiveTypeResult(Int)
        case structure.Type.Int => PrimitiveTypeResult(Int)
        case structure.Type.Long => PrimitiveTypeResult(Long)
        case structure.Type.Float => PrimitiveTypeResult(Float)
        case structure.Type.Double => PrimitiveTypeResult(Double)
        case structure.Type.Char => PrimitiveTypeResult(Int)

        case structure.Type.Seq(t) => getSeqAux(t)
        case structure.Type.DeclarationSeq(name) => getSeqAux(structure.Type.Declaration(name))
        case structure.Type.Option(t) => getOptionAux(t)
        case structure.Type.Either(left, right) => getEitherAux(left, right)
      }
    })

  private val _getType = mutable.Map[structure.Type, TypeResult]()

  def getType(t: structure.Type): TypeResult =
    _getType.getOrElseUpdate(t, t match {
      case structure.Type.Seq(t) => getPrimitiveType(t).result(Repeated(_))
      case structure.Type.Option(t) => getPrimitiveType(t).result(Option(_))
      case _ => getPrimitiveType(t).result(Required(_))
    })
}
