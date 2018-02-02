package scala.meta.internal.sbthost

import scala.tools.nsc.Global

trait DatabaseOps
    extends SymbolOps
    with ConfigOps
    with InputOps
    with LanguageOps
    with RangeOps
    with SymbolInformationOps
    with HijackReporterOps
    with ReflectionToolkit {
  val global: Global
}
