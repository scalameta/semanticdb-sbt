package scala.meta.internal.sbthost

import java.util._
import org.{langmeta => m}
import scala.reflect.internal.util.NoPosition

trait SymbolOps { self: DatabaseOps =>

  private var nextId = 0
  lazy val symbolCache = new HashMap[g.Symbol, m.Symbol]
  implicit class XtensionGSymbolMSymbol(sym: g.Symbol) {
    def toSemantic: m.Symbol = {
      def uncached(sym: g.Symbol): m.Symbol = {
        if (sym == null || sym == g.NoSymbol) return m.Symbol.None
        if (sym.isOverloaded) return m.Symbol.Multi(sym.alternatives.map(_.toSemantic))
        // NOTE: In Scala 2.10, RootClass.module is NoSymbol.
        // This is weird, but what can we do.
        if (sym.isRoot) return g.definitions.RootPackage.toSemantic
        if (sym.isModuleClass) return sym.asClass.module.toSemantic
        if (sym.isTypeSkolem) return sym.deSkolemize.toSemantic
        if (sym.isRootPackage) return m.Symbol.Global(m.Symbol.None, m.Signature.Term("_root_"))
        if (sym.isEmptyPackage) return m.Symbol.Global(m.Symbol.None, m.Signature.Term("_empty_"))

        if (sym.isSemanticdbLocal) {
          return {
            // + semanticdb-scalac deviation
            if (sym.pos == g.NoPosition) m.Symbol.None
            else {
              val id = nextId
              nextId += 1
              m.Symbol.Local("local" + id.toString)
            }
            // - semanticdb-scalac deviation
          }
        }

        val owner = sym.owner.toSemantic
        val signature = {
          if (sym.isMethod) {
            m.Signature.Method(sym.name.toSemantic, sym.disambiguator)
          } else if (sym.isTypeParameter) {
            m.Signature.TypeParameter(sym.name.toSemantic)
          } else if (sym.isValueParameter) {
            m.Signature.TermParameter(sym.name.toSemantic)
          } else if (sym.isType) {
            m.Signature.Type(sym.name.toSemantic)
          } else {
            m.Signature.Term(sym.name.toSemantic)
          }
        }
        m.Symbol.Global(owner, signature)
      }
      val msym = symbolCache.get(sym)
      if (msym != null) {
        msym
      } else {
        val msym = uncached(sym)
        symbolCache.put(sym, msym)
        msym
      }
    }
  }

  implicit class XtensionGSymbolMSpec(sym: g.Symbol) {
    def isSemanticdbGlobal: Boolean = !isSemanticdbLocal
    def isSemanticdbLocal: Boolean = {
      def definitelyGlobal = sym.hasPackageFlag
      def definitelyLocal =
        sym == g.NoSymbol ||
          sym.name.decoded.startsWith(g.nme.LOCALDUMMY_PREFIX) ||
          (sym.owner.isMethod && !sym.isParameter) ||
          ((sym.owner.isAliasType || sym.owner.isAbstractType) && !sym.isParameter) ||
          sym.isSelfParameter
      !definitelyGlobal && (definitelyLocal || sym.owner.isSemanticdbLocal)
    }
    def isSemanticdbMulti: Boolean = sym.isOverloaded
    def descriptor: String = {
      sym.info.descriptor
    }
    def disambiguator: String = {
      val siblings = sym.owner.info.decls.sorted.filter(_.name == sym.name)
      val synonyms = siblings.filter(_.descriptor == sym.descriptor)
      val suffix = {
        if (synonyms.length == 1) ""
        else "+" + (synonyms.indexOf(sym) + 1)
      }
      "(" + descriptor + suffix + ")"
    }
    def isSelfParameter: Boolean = {
      sym != g.NoSymbol && sym.owner.thisSym == sym
    }
  }
}
