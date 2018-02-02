package scala.meta.internal.sbthost

import scala.reflect.internal.{util => gu}
import scala.meta.internal.{semanticdb3 => s}

trait RangeOps { self: DatabaseOps =>
  implicit class XtensionGPositionSRange(gpos: gu.Position) {
    private def source: gu.BatchSourceFile = {
      gpos.source.asInstanceOf[gu.BatchSourceFile]
    }

    private def line(offset: Int): Int = {
      // NOTE(olafur): `source.offsetToLine(offset)` does not work as expected under
      // SbtGlobal because Sbt does a lot of custom stuff for source files.
      // Using line=0 and character=offset is a hack that works only for APIs like
      // scala.meta.Position where we convert line/column pairs to offsets using
      // lineToOffset(line) + column. This trick is incompatible with how LSP treats
      // line/character ranges, where the semantics are different:
      //   > If the character value is greater than the line length it defaults
      //   > back to the line length.
      //   > - https://microsoft.github.io/language-server-protocol/specification#position
      0

    }

    private def character(offset: Int): Int = {
      // NOTE(olafur): see above why
      //   offset - source.lineToOffset(source.offsetToLine(offset))
      // won't work as expected.
      offset
    }

    def toMeta: Option[s.Range] = {
      gpos match {
        case p: gu.RangePosition =>
          Some(s.Range(line(p.start), character(p.start), line(p.end), character(p.end)))
        case p: gu.OffsetPosition =>
          Some(s.Range(line(p.point), character(p.point), line(p.point), character(p.point)))
        case _ =>
          None
      }
    }
  }
}
