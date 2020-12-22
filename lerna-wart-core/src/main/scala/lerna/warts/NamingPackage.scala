package lerna.warts

import org.wartremover.{ WartTraverser, WartUniverse }

/** A default [[NamingPackage]] rule
  *
  * A package name should match a regular expression `[a-z\.]*`.
  */
object NamingPackage extends NamingPackage("""[a-z\.]*""")

/** A `NamingPackage` rule for [[http://www.wartremover.org/ WartRemover]]
  *
  * It would be better to lint a package name.
  *
  * @param regex A regular expression that a package name should match
  */
class NamingPackage(regex: String) extends WartTraverser {

  def apply(u: WartUniverse): u.Traverser = {

    val StdPackages = Set(
      u.universe.termNames.EMPTY_PACKAGE_NAME,
      u.universe.termNames.ROOTPKG,
    ).map(_.decodedName.toString)

    def isStandardPackageName(name: String): Boolean = {
      StdPackages.contains(name)
    }

    import u.universe._
    new Traverser {
      override def traverse(tree: Tree): Unit = {
        tree match {
          // Ignore trees marked by SuppressWarnings
          case t if hasWartAnnotation(u)(t) =>
          case PackageDef(symbol, _) if !isSynthetic(u)(tree) && !isStandardPackageName(symbol.toString) =>
            val name = symbol.toString
            if (!name.matches(regex)) {
              error(u)(tree.pos, s"package は [$regex] の形式で命名してください: $name")
            }
            super.traverse(tree)
          case _ =>
            super.traverse(tree)
        }
      }
    }
  }
}
