package scala.meta
package tests

import java.io.File
import scala.meta.sbthost.Sbthost
import org.scalatest.FunSuite
import org.scalatest.exceptions.TestFailedException
import scala.meta.testkit.DiffAssertions

abstract class SbthostTest(sourcerootFile: File, targetroot: File)
    extends FunSuite
    with DiffAssertions {
  val sourceroot = AbsolutePath(sourcerootFile)

  val mirror: Database = {
    val broken = Database.load(Classpath(AbsolutePath(targetroot)))
    Sbthost.patchDatabase(broken, sourceroot)
  }

  private val sanitize = "_empty_\\.(\\$[^\\.]+)\\.".r
  def checkNames(path: String, expected: String): Unit = {
    test("names") {
      val attrs = mirror.documents.find(_.input.toString.contains(path)).getOrElse {
        throw new TestFailedException(s"No input matches $path!", 1)
      }
      val obtained = attrs.copy(
        messages = Nil,
        synthetics = Nil,
        symbols = Nil
      )
      val sanitized = sanitize.replaceAllIn(obtained.syntax, "<>.")
      assertNoDiff(sanitized, expected)
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
      |[62..65): sbt => _root_.sbt.
      |[75..78): sbt => _root_.sbt.
      |[79..83): Keys => _root_.sbt.Keys.
      |[94..113): CompiledWithSbthost => _root_.sbthost.CompiledWithSbthost.
      |[122..125): bar => _root_.sbthost.CompiledWithSbthost.bar(I)I.
      |[126..127): x => _root_.sbthost.CompiledWithSbthost.bar(I)I.(x)
      |[129..132): Int => _root_.scala.Int#
      |[136..137): x => _root_.sbthost.CompiledWithSbthost.bar(I)I.(x)
      |[144..147): bar => _root_.sbthost.CompiledWithSbthost.bar(Ljava/lang/String;)Ljava/lang/String;.
      |[148..149): x => _root_.sbthost.CompiledWithSbthost.bar(Ljava/lang/String;)Ljava/lang/String;.(x)
      |[151..157): String => _root_.scala.Predef.String#
      |[161..162): x => _root_.sbthost.CompiledWithSbthost.bar(Ljava/lang/String;)Ljava/lang/String;.(x)
      |[165..168): bar => _root_.sbthost.CompiledWithSbthost.bar(I)I.
      |[174..177): bar => _root_.sbthost.CompiledWithSbthost.bar(Ljava/lang/String;)Ljava/lang/String;.
      |[195..196): x => _root_.sbthost.CompiledWithSbthost.x.
      |[199..206): taskKey => _root_.sbt.package.taskKey(Ljava/lang/String;)Lsbt/TaskKey;.
      |[207..210): Seq => _root_.scala.collection.immutable.Seq#
      |[211..215): File => _root_.sbt.package.File#
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
      |[4..7): foo => <>.foo.
      |[31..35): name => _root_.sbt.Keys.name.
      |[36..38): := => _root_.sbt.SettingKey#`:=`(Ljava/lang/Object;)Lsbt/Init/Setting;.
      |[47..48): + => _root_.java.lang.String#`+`(Ljava/lang/Object;)Ljava/lang/String;.
      |[49..52): foo => <>.foo.
      |[60..72): organization => _root_.sbt.Keys.organization.
      |[73..75): := => _root_.sbt.SettingKey#`:=`(Ljava/lang/Object;)Lsbt/Init/Setting;.
      |[103..104): + => _root_.java.lang.String#`+`(Ljava/lang/Object;)Ljava/lang/String;.
      |[105..108): foo => <>.foo.
      |[110..114): test => _root_.sbt.Keys.test.
      |[115..117): := => _root_.sbt.Scoped.DefinableTask#`:=`(Ljava/lang/Object;)Lsbt/Init/Setting;.
      |[118..121): Def => _root_.sbt.Def.
      |[122..126): task => _root_.sbt.Def.task(Ljava/lang/Object;)Lsbt/Init/Initialize;.
      |[136..141): value => _root_.sbt.std.MacroValue#value()Ljava/lang/Object;.
      |""".stripMargin
  )
}
