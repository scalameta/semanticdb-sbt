package scala.meta.internal.sbthost

import scala.reflect.internal.{Flags => gf}
import scala.meta.internal.{semanticdb3 => s}
import scala.meta.internal.semanticdb3.SymbolInformation.{Kind => k}
import scala.meta.internal.semanticdb3.SymbolInformation.{Property => p}

trait SymbolInformationOps { self: DatabaseOps =>
  import g._

  implicit class XtensionGSymbolSSymbolInformation(gsym0: g.Symbol) {
    private val gsym: g.Symbol = {
      if (gsym0.isModuleClass) gsym0.asClass.module
      else if (gsym0.isTypeSkolem) gsym0.deSkolemize
      else gsym0.setter(gsym0.owner).orElse(gsym0.getter(gsym0.owner).orElse(gsym0))
    }

    private val isObject = gsym.isModule && !gsym.hasFlag(gf.PACKAGE) && gsym.name != nme.PACKAGE

    private def symbol: String = gsym.toSemantic.syntax

    private def kind: s.SymbolInformation.Kind = {
      if (gsym.isPrimaryConstructor) k.PRIMARY_CONSTRUCTOR
      else if (gsym.isConstructor) k.SECONDARY_CONSTRUCTOR
      else if (gsym.hasFlag(gf.MACRO)) k.MACRO
      else if (gsym.isMethod && !gsym.hasFlag(gf.ACCESSOR) && !gsym.hasFlag(gf.PARAMACCESSOR))
        k.DEF
      else if (gsym.isType && !gsym.isClass && !gsym.hasFlag(gf.PARAM)) k.TYPE
      else if (gsym.isTerm && (gsym.hasFlag(gf.PARAM) || gsym.hasFlag(gf.PARAMACCESSOR)))
        k.PARAMETER
      else if (gsym.isType && gsym.hasFlag(gf.PARAM)) k.TYPE_PARAMETER
      else if (isObject) k.OBJECT
      else if (gsym.hasFlag(gf.PACKAGE)) k.PACKAGE
      else if (gsym.isModule && gsym.name == nme.PACKAGE) k.PACKAGE_OBJECT
      else if (gsym.isClass && !gsym.hasFlag(gf.TRAIT)) k.CLASS
      else if (gsym.isClass && gsym.hasFlag(gf.TRAIT)) k.TRAIT
      else if (gsym.isTerm && (gsym.hasFlag(gf.MUTABLE) || nme.isSetterName(gsym.name))) k.VAR
      else if (gsym.isTerm && !(gsym.hasFlag(gf.LOCAL) && gsym.hasFlag(gf.PARAMACCESSOR))) k.VAL
      else k.UNKNOWN_KIND
    }

    private def accessQualifierProperties: Int = {
      var flags = 0
      val gpriv = gsym.privateWithin.orElse(gsym.owner)
      if (gsym.hasFlag(gf.SYNTHETIC) && gsym.hasFlag(gf.ARTIFACT)) {
        // NOTE: some sick artifact vals produced by mkPatDef can be private to method (whatever that means)
        // so here we just ignore them
      } else {
        if (gsym.hasFlag(gf.PROTECTED)) flags |= p.PROTECTED.value
        if (gsym.hasFlag(gf.PRIVATE) && !gsym.hasFlag(gf.PARAMACCESSOR)) flags |= p.PRIVATE.value
        // TODO: `private[pkg] class C` doesn't have PRIVATE in its flags
        // so we need to account for that!
        if (gsym.hasAccessBoundary && gpriv != g.NoSymbol && !gsym.hasFlag(gf.PROTECTED))
          flags |= p.PRIVATE.value
      }
      flags
    }

    private def otherProperties: Int = {
      var flags = 0
      val isDeclaredDeferred = gsym.hasFlag(gf.DEFERRED) && !gsym.hasFlag(gf.PARAM)
      val isDeclaredAbstract = (gsym.hasFlag(gf.ABSTRACT) && !gsym.hasFlag(gf.TRAIT)) || gsym
        .hasFlag(gf.ABSOVERRIDE)
      if (isDeclaredDeferred || isDeclaredAbstract) flags |= p.ABSTRACT.value
      if ((gsym.hasFlag(gf.FINAL) && !gsym.hasFlag(gf.PACKAGE)) || isObject) flags |= p.FINAL.value
      if (gsym.hasFlag(gf.SEALED)) flags |= p.SEALED.value
      if (gsym.hasFlag(gf.IMPLICIT)) flags |= p.IMPLICIT.value
      if (gsym.hasFlag(gf.LAZY)) flags |= p.LAZY.value
      if (gsym.hasFlag(gf.CASE)) flags |= p.CASE.value
      if (gsym.isType && gsym.hasFlag(gf.CONTRAVARIANT)) flags |= p.CONTRAVARIANT.value
      if (gsym.isType && gsym.hasFlag(gf.COVARIANT)) flags |= p.COVARIANT.value
      // TODO: p.VALPARAM and p.VARPARAM
      flags
    }

    private def properties: Int = {
      accessQualifierProperties | otherProperties
    }

    private def name: String = {
      gsym.decodedName.toString
    }

    private def range: Option[s.Location] = None

    private def signature: Option[s.TextDocument] = {
      val text = {
        if (gsym.isClass || gsym.isModule) ""
        else gsym.info.toString.stripPrefix("=> ")
      }
      Some(s.TextDocument(text = text))
    }

    private def members: List[String] = Nil

    private def overrides: List[String] = Nil

    def toSymbolInformation: s.SymbolInformation = {
      s.SymbolInformation(
        symbol,
        language,
        kind,
        properties,
        name,
        range,
        signature,
        members,
        overrides)
    }
  }
}
