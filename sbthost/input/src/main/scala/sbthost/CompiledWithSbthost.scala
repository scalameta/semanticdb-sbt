package sbthost
import scala.collection.immutable.Seq

import sbt._
import sbt.Keys._

object CompiledWithSbthost {
  def bar(x: Int) = x
  def bar(x: String) = x
  bar(1)
  bar("string")

  val x = taskKey[Seq[File]]("")
}
