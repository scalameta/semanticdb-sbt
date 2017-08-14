package scala.meta
package tests

import java.io.File
import scala.meta.sbthost.Sbthost
import org.scalatest.FunSuite
import org.scalatest.exceptions.TestFailedException

abstract class SbthostTest(sourcerootFile: File, targetroot: File) extends FunSuite {
  val sourceroot = AbsolutePath(sourcerootFile)

  val mirror: Database = {
    val broken = Database.load(Classpath(AbsolutePath(targetroot)))
    Sbthost.patchDatabase(broken, sourceroot)
  }

  private val sanitize = "_empty_\\.(\\$[^\\.]+)\\.".r
  def checkNames(path: String, expected: String): Unit = {
    test("names") {
      val attrs = mirror.entries.find(_.input.toString.contains(path)).getOrElse {
        throw new TestFailedException(s"No input matches $path!", 1)
      }
      val obtained = attrs.copy(
        messages = Nil,
        sugars = Nil,
        symbols = Nil
      )
      val sanitized = sanitize.replaceAllIn(obtained.syntax, "<>.")
      assert(sanitized.trim == expected.trim)
    }
  }

  test("names.nonEmpty") {
    assert(mirror.names.nonEmpty)
  }

}

class ScalaFileTest extends SbthostTest(BuildInfo.sourceroot, BuildInfo.targetroot) {
  checkNames(
    "CompiledWith",
    """Language:
      |Scala210
      |
      |Names:
      |[8..15): sbthost => _root_.sbthost.
      |[23..28): scala => _root_.scala.
      |[29..39): collection => _root_.scala.collection.
      |[40..49): immutable => _root_.scala.collection.immutable.
      |[62..81): CompiledWithSbthost => _root_.sbthost.CompiledWithSbthost.
      |[90..93): bar => _root_.sbthost.CompiledWithSbthost.bar(I)I.
      |[94..95): x => _root_.sbthost.CompiledWithSbthost.bar(I)I.(x)
      |[104..105): x => _root_.sbthost.CompiledWithSbthost.bar(I)I.(x)
      |[112..115): bar => _root_.sbthost.CompiledWithSbthost.bar(Ljava/lang/String;)Ljava/lang/String;.
      |[116..117): x => _root_.sbthost.CompiledWithSbthost.bar(Ljava/lang/String;)Ljava/lang/String;.(x)
      |[129..130): x => _root_.sbthost.CompiledWithSbthost.bar(Ljava/lang/String;)Ljava/lang/String;.(x)
      |[133..136): bar => _root_.sbthost.CompiledWithSbthost.bar(I)I.
      |[142..145): bar => _root_.sbthost.CompiledWithSbthost.bar(Ljava/lang/String;)Ljava/lang/String;.
    """.stripMargin
  )
}
class SbtFileTest extends SbthostTest(BuildInfo.sbtSourceroot, BuildInfo.sbtTargetroot) {
  checkNames(
    "build.sbt",
    """
      |Language:
      |Sbt0137
      |
      |Names:
      |[4..7): foo => _root_.<>.foo.
      |[31..35): name => _root_.sbt.Keys.name.
      |[47..48): + => _root_.java.lang.String#`+`(Ljava/lang/Object;)Ljava/lang/String;.
      |[49..52): foo => _root_.<>.foo.
      |[60..72): organization => _root_.sbt.Keys.organization.
      |[103..104): + => _root_.java.lang.String#`+`(Ljava/lang/Object;)Ljava/lang/String;.
      |[105..108): foo => _root_.<>.foo.
      |""".stripMargin
  )
}
