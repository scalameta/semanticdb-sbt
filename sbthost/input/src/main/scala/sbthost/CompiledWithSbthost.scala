package sbthost
import scala.collection.immutable.Seq

object CompiledWithSbthost {
  def bar(x: Int) = x
  def bar(x: String) = x
  1 + 2
  bar(1)
  bar("string")

}
