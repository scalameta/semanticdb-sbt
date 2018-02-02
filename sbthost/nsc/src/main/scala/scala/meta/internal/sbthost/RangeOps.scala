package scala.meta.internal.sbthost

import scala.reflect.internal.{util => gu}
import scala.meta.internal.{semanticdb3 => s}

trait RangeOps { self: DatabaseOps =>
  implicit class XtensionGPositionSRange(gpos: gu.Position) {
    private def source: gu.BatchSourceFile = {
      gpos.source.asInstanceOf[gu.BatchSourceFile]
    }

    private def line(offset: Int): Int = {
      // NOTE: Looks like some seriously crazy stuff is going on here.
      // source.offsetToLine(offset)
      0
    }

    private def character(offset: Int): Int = {
      // NOTE: Looks like some seriously crazy stuff is going on here.
      // offset - source.lineToOffset(source.offsetToLine(offset))
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
