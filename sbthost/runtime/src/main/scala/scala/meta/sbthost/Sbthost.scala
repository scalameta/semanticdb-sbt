package scala.meta
package sbthost

import scala.collection.mutable
import scala.meta.tokens.Token.Ident

object Sbthost {
  private def label(input: (Input, Attributes)) = input._1 match {
    case Input.LabeledString(label, _) => label
  }
  def patchMirror(mirror: Mirror, sourceroot: AbsolutePath): Database = {
    val inputs = mirror.database.entries.map(_._1)
    val entries = mirror.database.entries.groupBy(label).map {
      case (label, attrs) if label.endsWith(".sbt") =>
        sbtFile(Input.File(sourceroot.resolve(label)), attrs.toVector)
      case (_, (a, b) +: Nil) => scalaFile(a, b)
    }
    Database(entries.toList)
  }

  private def sbtFile(input: Input, attrs: Vector[(Input, Attributes)]): (Input, Attributes) = {
    val source = dialects.Sbt0137(input).parse[Source].get
    val (definitions, exprs) = source.stats.partition(_.is[Defn])
    val tokenMap: Map[Token, (Int, Token)] = {
      val builder = Map.newBuilder[Token, (Int, Token)]
      val definitionTokens = definitions.mkString("\n\n").tokenize.get
      val reader = definitionTokens.toIterator.drop(1) // drop BOF
      var stack = definitions
      while (stack.nonEmpty && reader.hasNext) {
        val definition = stack.head
        definition.tokens.foreach { from =>
          val to = reader.next()
          assert(from.syntax == to.syntax, s"$from == $to")
          builder += (from -> (0, to))
        }
        stack = stack.tail
        if (stack.nonEmpty) {
          reader.next() // \n
          reader.next() // \n
        }
      }
      exprs.zipWithIndex.foreach {
        case (expr, i) =>
          val tokens = expr.tokens.mkString.tokenize.get.drop(1) // drop BOF
          expr.tokens.zip(tokens).foreach {
            case (a, b) =>
              builder += (a -> (i + 1, b))
          }
      }
      builder.result()
    }
    val lookup = attrs.zipWithIndex
      .flatMap {
        case ((_, as), i) =>
          as.names.map {
            case (pos, sym) =>
              (i -> pos.start.offset, sym)
          }
      }
      .groupBy(_._1)
      .mapValues(_.map(_._2))
    def label(symbol: Symbol): String = symbol match {
      case Symbol.Global(_, Signature.Type(name)) => name
      case Symbol.Global(_, Signature.Term(name)) => name
      case Symbol.Global(_, Signature.Method(name, _)) => name
      case _ => symbol.syntax
    }
    val buffer = Vector.newBuilder[(Position, Symbol)]
    val taken = mutable.Set.empty[Position]
    source.tokens.foreach {
      case t @ Ident(value) =>
        tokenMap.get(t).foreach {
          case (idx, tok) =>
            lookup.get(idx -> tok.pos.start.offset).foreach {
              case syms =>
                syms.foreach { sym =>
                  val l = label(sym)
                  if (l == value && !taken(t.pos)) {
                    buffer += (t.pos -> sym)
                    taken += t.pos
                  }
                }
            }
        }
      case _ =>
    }
    val attributes = Attributes(
      dialects.Sbt0137,
      names = buffer.result(),
      messages = attrs.flatMap(_._2.messages),
      denotations = attrs.flatMap(_._2.denotations),
      sugars = attrs.flatMap(_._2.sugars)
    )
    (input, attributes)
  }
  private def scalaFile(input: Input, attrs: Attributes): (Input, Attributes) = {
    val endByStart = input.tokenize.get.collect {
      case t: Ident => t.pos.start -> (t -> t.pos.end)
    }.toMap
    val isTaken = mutable.Set.empty[Point]
    val namesS = attrs.names.collect {
      case (_, Symbol.Global(_, Signature.Term(name))) => name
    }
    val names = attrs.names.collect {
      case (pos @ Position.Range(_, start, end), symbol)
          if !isTaken(start) && endByStart.contains(start) =>
        isTaken += start
        (pos.copy(end = endByStart(start)._2), symbol)
    }
    input -> attrs.copy(names = names)
  }
}
