package scala.meta.internal.sbthost

trait TypeOps { self: DatabaseOps =>
  implicit class XtensionGType(gtpe: g.Type) {
    def descriptor: String = {
      def unsupported = sys.error(s"unsupported type $gtpe")
      def paramDescriptors = gtpe.paramss.flatten.map(_.info.descriptor)
      gtpe match {
        case ByNameType(gtpe) => "=>" + gtpe.descriptor
        case RepeatedType(gtpe) => gtpe.descriptor + "*"
        case g.TypeRef(_, gsym, _) => gsym.name.toEncodedSemantic
        case g.SingleType(_, _) => ".type"
        case g.ThisType(_) => ".type"
        case g.ConstantType(g.Constant(_: g.Type)) => "Class"
        case g.ConstantType(_) => ".type"
        case g.RefinedType(_, _) => "{}"
        case g.AnnotatedType(_, gtpe, _) => gtpe.descriptor
        case g.ExistentialType(_, gtpe) => gtpe.descriptor
        case g.ClassInfoType(_, _, _) => unsupported
        case _: g.NullaryMethodType | _: g.MethodType => paramDescriptors.mkString(",")
        case g.TypeBounds(_, _) => unsupported
        case g.PolyType(_, gtpe) => gtpe.descriptor
        case g.NoType => unsupported
        case g.NoPrefix => unsupported
        case other => unsupported
      }
    }
  }

  object ByNameType {
    def unapply(gtpe: g.Type): Option[g.Type] = gtpe match {
      case g.TypeRef(_, g.definitions.ByNameParamClass, garg :: Nil) => Some(garg)
      case _ => None
    }
  }

  object RepeatedType {
    def unapply(gtpe: g.Type): Option[g.Type] = gtpe match {
      case g.TypeRef(_, g.definitions.RepeatedParamClass, garg :: Nil) => Some(garg)
      case g.TypeRef(_, g.definitions.JavaRepeatedParamClass, garg :: Nil) => Some(garg)
      case _ => None
    }
  }
}
