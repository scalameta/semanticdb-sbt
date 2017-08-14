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
import scala.meta.internal.semantic.{schema => s}

trait SbthostPipeline extends DatabaseOps { self: SbthostPlugin =>
  object SbthostComponent extends PluginComponent {
    private lazy val pathCount = mutable.Map.empty[Path, Int].withDefaultValue(0)
    val global: SbthostPipeline.this.global.type = SbthostPipeline.this.global
    // Select Sbt0137 dialect for scala sources extracted from sbt files
    private val isSbt = g.getClass.getName.contains("sbt.compiler.Eval")
    private val detectedDialect =
      if (isSbt) "Sbt0137" else "Scala210"
    override val runsAfter = List("typer")
    override val runsRightAfter = Some("typer")
    val phaseName = "sbthost"
    def getMessages(source: SourceFile): mutable.LinkedHashSet[s.Message] =
      g.reporter match {
        case reporter: StoreReporter =>
          reporter.infos.withFilter(_.pos.source == source).map { info =>
            val range = Option(info.pos).collect {
              case p: RangePosition => s.Position(p.start, p.end)
              case p: OffsetPosition => s.Position(p.point, p.point)
            }
            val severity = info.severity.id match {
              case 0 => s.Message.Severity.INFO
              case 1 => s.Message.Severity.WARNING
              case 2 => s.Message.Severity.ERROR
              case els => s.Message.Severity.UNKNOWN
            }
            s.Message(range, severity, info.msg)
          }
        case els =>
          mutable.LinkedHashSet.empty
      }
    override def newPhase(prev: Phase) = new StdPhase(prev) {
      if (!isSbt) pathCount.clear()
      // sbt creates a new phase for each synthetic compilation unit,
      // even if they origin from the same source.
      else ()
      def apply(unit: g.CompilationUnit): Unit = {
        val names = ListBuffer.newBuilder[s.ResolvedName]
        val denots = mutable.Map.empty[String, s.ResolvedSymbol]
        def getNames(): Unit = {
          object traverser extends g.Traverser {
            def isValidSymbol(symbol: g.Symbol) =
              symbol.ne(null) && symbol != g.NoSymbol
            override def traverse(tree: g.Tree): Unit = {
              if (tree.pos.isDefined &&
                  tree.hasSymbol &&
                  isValidSymbol(tree.symbol) &&
                  isValidSymbol(tree.symbol.owner)) {
                val symbol = tree.symbol.toSemantic
                val symbolSyntax = symbol.syntax
                val range = s.Position(tree.pos.point, tree.pos.point)
                names += s.ResolvedName(Some(range), symbolSyntax)
                if (!denots.contains(symbolSyntax)) {
                  val denot = tree.symbol.toDenotation
                  denots(symbolSyntax) = s.ResolvedSymbol(symbol.syntax, Some(denot))
                }
              }
              super.traverse(tree)
            }
          }
          traverser(unit.body)
        }
        getNames()
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
        val attributes = s.Attributes(
          filename = filename.toString,
          language = detectedDialect,
          contents = unit.source.content.mkString,
          names = names.result(),
          symbols = denots.result().values.toSeq,
          messages = getMessages(unit.source).toSeq
        )
        val semanticdbOutFile = config.semanticdbPath(filename)
        semanticdbOutFile.toFile.getParentFile.mkdirs()
        // If this is not the first compilation unit for this .sbt file, append.
        val options =
          if (counter > 0 && isSbt) Array(StandardOpenOption.APPEND)
          else Array(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        val db = s.Database(List(attributes))
        Files.write(semanticdbOutFile.normalize(), db.toByteArray, options: _*)
      }
    }
  }
}
