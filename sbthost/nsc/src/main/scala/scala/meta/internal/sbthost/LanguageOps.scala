package scala.meta.internal.sbthost

trait LanguageOps { self: DatabaseOps =>
  def isSbt = g.getClass.getName.contains("sbt.compiler.Eval")

  lazy val language: String = {
    // Select Sbt0137 dialect for scala sources extracted from sbt files
    if (isSbt) "Sbt0137" else "Scala210"
  }
}
