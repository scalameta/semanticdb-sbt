package scala.meta.internal.sbthost

import org.{langmeta => m}

trait InputOps { self: DatabaseOps =>
  implicit class XtensionGPosition(pos: g.Position) {
    def toInput: m.Input = {
      if ((pos.source.file ne null) &&
          (pos.source.file.file ne null)) {
        val relativePath = config.sourceroot.relativize(pos.source.file.file.toPath)
        m.Input.File(relativePath)
      } else {
        m.Input.None
      }
    }
  }
}
