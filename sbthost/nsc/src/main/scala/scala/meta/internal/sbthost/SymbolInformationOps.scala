package scala.meta.internal.sbthost

import scala.reflect.internal.{Flags => gf}
import scala.meta.internal.{semanticdb3 => s}
import scala.meta.internal.semanticdb3.SymbolInformation.{Kind => k}
import scala.meta.internal.semanticdb3.SymbolInformation.{Property => p}
import scala.meta.internal.semanticdb3.Accessibility.{Tag => a}

trait SymbolInformationOps { self: DatabaseOps =>
  import g._

  implicit class XtensionGSymbolSSymbolInformation(gsym0: g.Symbol) {
    private val gsym: g.Symbol = {
      if (gsym0.isModuleClass) gsym0.asClass.module
      else if (gsym0.isTypeSkolem) gsym0.deSkolemize
      else gsym0
    }

    private val isObject = gsym.isModule && !gsym.hasFlag(gf.PACKAGE) && gsym.name != nme.PACKAGE

    private def symbol: String = gsym.toSemantic.syntax

    private def kind: s.SymbolInformation.Kind = {
      gsym match {
        case gsym: MethodSymbol =>
          if (gsym.isConstructor) {
            if (gsym.isPrimaryConstructor) k.PRIMARY_CONSTRUCTOR
            else k.SECONDARY_CONSTRUCTOR
          } else {
            if (gsym.isSetter) k.SETTER
            else if (gsym.isGetter && gsym.isLazy && !gsym.isClass) k.VAL
            else if (gsym.isGetter) k.GETTER
            else if (gsym.isMacro) k.MACRO
            else k.DEF
          }
        case gsym: ModuleSymbol =>
          if (gsym.hasPackageFlag) k.PACKAGE
          else if (gsym.isPackageObject) k.PACKAGE_OBJECT
          else k.OBJECT
        case gsym: TermSymbol =>
          if (gsym.isSelfParameter) k.SELF_PARAMETER
          else if (gsym.isParameter) k.PARAMETER
          else if (gsym.isMutable) k.VAR
          else k.VAL
        case gsym: ClassSymbol =>
          if (gsym.isTrait) k.TRAIT
          else k.CLASS
        case gsym: TypeSymbol =>
          if (gsym.isParameter) k.TYPE_PARAMETER
          else k.TYPE
        case NoSymbol =>
          k.UNKNOWN_KIND
        case _ =>
          sys.error(s"unsupported symbol $gsym")
      }
    }

    private def properties: Int = {
      var props = 0
      def isAbstractClass = gsym.isClass && gsym.isAbstractClass && !gsym.isTrait
      def isAbstractMethod = gsym.isMethod && gsym.isDeferred
      def isAbstractType = gsym.isType && !gsym.isParameter && gsym.isDeferred
      if (isAbstractClass || isAbstractMethod || isAbstractType) props |= p.ABSTRACT.value
      if ((gsym.hasFlag(gf.FINAL) && !gsym.hasFlag(gf.PACKAGE)) || isObject) props |= p.FINAL.value
      if (gsym.hasFlag(gf.SEALED)) props |= p.SEALED.value
      if (gsym.hasFlag(gf.IMPLICIT)) props |= p.IMPLICIT.value
      if (gsym.hasFlag(gf.LAZY)) props |= p.LAZY.value
      if (gsym.hasFlag(gf.CASE)) props |= p.CASE.value
      if (gsym.isType && gsym.hasFlag(gf.CONTRAVARIANT)) props |= p.CONTRAVARIANT.value
      if (gsym.isType && gsym.hasFlag(gf.COVARIANT)) props |= p.COVARIANT.value
      if (gsym.isParameter && gsym.owner.isPrimaryConstructor) {
        val ggetter = gsym.getter(gsym.owner.owner)
        if (ggetter != g.NoSymbol && !ggetter.isStable) props |= p.VARPARAM.value
        else if (ggetter != g.NoSymbol) props |= p.VALPARAM.value
        else ()
      }
      props
    }

    private def name: String = {
      gsym.name.toSemantic
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

    private def tpe: Option[s.Type] = None

    private def anns: List[s.Annotation] = Nil

    // TODO: I'm not completely happy with the implementation of this method.
    // See https://github.com/scalameta/scalameta/issues/1325 for details.
    private def acc: Option[s.Accessibility] = {
      if (gsym.hasFlag(gf.SYNTHETIC) && gsym.hasFlag(gf.ARTIFACT)) {
        // NOTE: some sick artifact vals produced by mkPatDef can be
        // private to method (whatever that means), so here we just ignore them.
        Some(s.Accessibility(a.PUBLIC))
      } else {
        if (gsym.privateWithin == NoSymbol) {
          if (gsym.isPrivateLocal) Some(s.Accessibility(a.PRIVATE_THIS))
          else if (gsym.isPrivate) Some(s.Accessibility(a.PRIVATE))
          else if (gsym.isProtectedLocal) Some(s.Accessibility(a.PROTECTED_THIS))
          else if (gsym.isProtected) Some(s.Accessibility(a.PROTECTED))
          else Some(s.Accessibility(a.PUBLIC))
        } else {
          val ssym = gsym.privateWithin.toSemantic.syntax
          if (gsym.isProtected) Some(s.Accessibility(a.PROTECTED_WITHIN, ssym))
          else Some(s.Accessibility(a.PRIVATE_WITHIN, ssym))
        }
      }
    }

    private def owner: String = {
      gsym.owner.toSemantic.syntax
    }

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
        overrides,
        tpe,
        anns,
        acc,
        owner)
    }
  }
}
