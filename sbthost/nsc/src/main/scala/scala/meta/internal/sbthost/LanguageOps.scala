package scala.meta.internal.sbthost

import scala.meta.internal.{semanticdb3 => s}

trait LanguageOps { self: DatabaseOps =>
  def isSbt = g.getClass.getName.contains("sbt.compiler.Eval")

  lazy val language: Option[s.Language] = {
    // Select Sbt0137 dialect for scala sources extracted from sbt files
    if (isSbt) Some(s.Language("Sbt0137"))
    else Some(s.Language("Scala210"))
  }
}
