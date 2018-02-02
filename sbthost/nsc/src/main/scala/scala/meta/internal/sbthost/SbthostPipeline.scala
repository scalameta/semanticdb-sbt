package scala.meta.internal.sbthost

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.internal.util.OffsetPosition
import scala.reflect.internal.util.RangePosition
import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.Phase
import scala.tools.nsc.io.VirtualFile
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.reporters.StoreReporter
import scala.meta.internal.{semanticdb3 => s}

trait SbthostPipeline extends DatabaseOps { self: SbthostPlugin =>
  object SbthostComponent extends PluginComponent {
    private lazy val pathCount = mutable.Map.empty[Path, Int].withDefaultValue(0)
    val global: SbthostPipeline.this.global.type = SbthostPipeline.this.global
    override val runsAfter = List("typer")
    override val runsRightAfter = Some("typer")
    val phaseName = "semanticdb-sbt"
    def getDiagnostics(source: SourceFile): mutable.LinkedHashSet[s.Diagnostic] =
      g.reporter match {
        case reporter: StoreReporter =>
          reporter.infos.withFilter(_.pos.source == source).flatMap { info =>
            info.pos.toMeta.map { range =>
              val severity = info.severity.id match {
                case 0 => s.Diagnostic.Severity.INFORMATION
                case 1 => s.Diagnostic.Severity.WARNING
                case 2 => s.Diagnostic.Severity.ERROR
                case els => s.Diagnostic.Severity.UNKNOWN_SEVERITY
              }
              s.Diagnostic(Some(range), severity, info.msg)
            }
          }
        case els =>
          mutable.LinkedHashSet.empty
      }
    val isVisitedTree = mutable.Set.empty[g.Tree]
    override def newPhase(prev: Phase) = new StdPhase(prev) {
      isVisitedTree.clear()
      if (!isSbt) pathCount.clear()
      // sbt creates a new phase for each synthetic compilation unit,
      // even if they origin from the same source.
      else ()
      def apply(unit: g.CompilationUnit): Unit = {
        val symbols = mutable.Map.empty[String, s.SymbolInformation]
        val occs = ListBuffer.newBuilder[s.SymbolOccurrence]
        def isValidSymbol(symbol: g.Symbol) =
          symbol.ne(null) && symbol != g.NoSymbol
        def computeNames(): Unit = {
          object traverser extends g.Traverser {
            override def traverse(tree: g.Tree): Unit = {
              // Macro expandees can have cycles,
              if (isVisitedTree(tree)) return else isVisitedTree += tree

              def traverseMacroExpandees(): Unit = {
                tree.attachments.all.collect {
                  case att: g.MacroExpansionAttachment =>
                    traverse(att.original)
                }
              }
              def traverseTypeTree(): Unit = {
                tree match {
                  case gtree: g.TypeTree if gtree.original != null =>
                    traverse(gtree.original)
                  case _ =>
                }
              }
              def emitResolvedNames(): Unit = {
                if (tree.pos.isDefined &&
                    tree.hasSymbol &&
                    isValidSymbol(tree.symbol) &&
                    isValidSymbol(tree.symbol.owner)) {
                  val symbol = tree.symbol.toSemantic
                  val symbolSyntax = symbol.syntax
                  tree.pos.focus.toMeta match {
                    case Some(range) =>
                      val role = s.SymbolOccurrence.Role.REFERENCE
                      occs += s.SymbolOccurrence(Some(range), symbolSyntax, role)
                      if (!symbols.contains(symbolSyntax)) {
                        symbols(symbolSyntax) = tree.symbol.toSymbolInformation
                      }
                    case _ =>
                      ()
                  }
                }
              }

              traverseMacroExpandees()
              traverseTypeTree()
              emitResolvedNames()
              super.traverse(tree)
            }
          }
          traverser(unit.body)
        }
        computeNames()
        val sourcePath = unit.source.file match {
          case f: VirtualFile =>
            Paths.get(f.path)
          case els =>
            Paths.get(els.file.getAbsoluteFile.toURI)
        }
        val counter = {
          val n = pathCount(sourcePath)
          pathCount(sourcePath) = n + 1
          n
        }
        val filename = config.relativePath(sourcePath)
        val document = s.TextDocument(
          schema = s.Schema.SEMANTICDB3,
          uri = filename.toString,
          text = unit.source.content.mkString,
          language = language,
          symbols = symbols.result().values.toSeq,
          occurrences = occs.result(),
          diagnostics = getDiagnostics(unit.source).toSeq
        )
        val semanticdbOutFile = config.semanticdbPath(filename)
        semanticdbOutFile.toFile.getParentFile.mkdirs()
        // If this is not the first compilation unit for this .sbt file, append.
        val options =
          if (counter > 0 && isSbt) Array(StandardOpenOption.APPEND)
          else Array(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        val documents = s.TextDocuments(List(document))
        Files.write(semanticdbOutFile.normalize(), documents.toByteArray, options: _*)
      }
    }
  }
}
