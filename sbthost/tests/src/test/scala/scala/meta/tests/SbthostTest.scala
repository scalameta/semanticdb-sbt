package scala.meta
package tests

import java.io.File
import scala.meta.internal.io.FileIO
import scala.meta.internal.semantic.{schema => s}
import scala.meta.internal.semantic.{vfs => v}
import scala.meta.sbthost.Sbthost
import com.google.protobuf.CodedInputStream
import com.trueaccord.scalapb.LiteParser
import org.scalameta.logger
import org.scalatest.FunSuite
import org.scalatest.exceptions.TestFailedException

abstract class SbthostTest(sourcerootFile: File, targetroot: File) extends FunSuite {
  val sourceroot = AbsolutePath(sourcerootFile)

  val mirror = {
    val files = FileIO.listAllFilesRecursively(
      AbsolutePath(targetroot).resolve("META-INF").resolve("semanticdb"))
    val entries = files.flatMap { entry =>
      // semanticdb.proto currently doesn't have a s.Database message, see
      // https://github.com/scalameta/scalameta/issues/943
      // We roll our own serializer until that is fixed.
      var done = false
      val stream = CodedInputStream.newInstance(entry.readAllBytes)
      val attributes = Vector.newBuilder[s.Attributes]
      while (!done) {
        val tag = stream.readTag()
        tag match {
          case 0 => done = true
          case _ => attributes += LiteParser.readMessage(stream, s.Attributes.defaultInstance)
        }
      }
      attributes.result()
    }
    val db = s.Database(entries.toList)
    val sp = Sourcepath(sourceroot)
    val broken = db.toMeta(Some(sp))
    Sbthost.patchMirror(broken, sourceroot)
  }

  private val sanitize = "_empty_\\.(\\$[^\\.]+)\\.".r
  def checkNames(path: String, expected: String): Unit = {
    test("names") {
      val (_, attrs) = mirror.entries.find(_._1.toString.contains(path)).getOrElse {
        throw new TestFailedException(s"No input matches $path!", 1)
      }
      val obtained = attrs.copy(
        messages = Nil,
        sugars = Nil,
        denotations = Nil
      )
      val sanitized = sanitize.replaceAllIn(obtained.syntax, "<>.")
      logger.elem(sanitized)
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
    """Dialect:
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
      |Dialect:
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
