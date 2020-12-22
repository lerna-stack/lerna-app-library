package lerna.warts

import org.wartremover.{ WartTraverser, WartUniverse }

import scala.reflect.NameTransformer

/** A default [[NamingClass]] rule
  *
  * A class name should match a regular expression `[A-Z$][A-Za-z0-9$]*`.
  *
  * @note
  *   Some library that is using a macro generates a class whose name contains `$`.
  *   It is difficult to exclude such classes from targets of WartRemover.
  *   Therefore, we permit `$` to be a part of a class name.
  */
object NamingClass extends NamingClass("""[A-Z$][A-Za-z0-9$]*""")

/** A `NamingClass` rule for [[http://www.wartremover.org/ WartRemover]]
  *
  * It would be better to lint a class name.
  *
  * @param regex A regular expression that a class name should match
  */
class NamingClass(regex: String) extends WartTraverser {

  def apply(u: WartUniverse): u.Traverser = {
    import u.universe._
    new Traverser {
      override def traverse(tree: Tree): Unit = {
        tree match {
          // Ignore trees marked by SuppressWarnings
          case t if hasWartAnnotation(u)(t) =>
          case ClassDef(_, TypeName(name), _, _) if !isSynthetic(u)(tree) =>
            val decodedName = NameTransformer.decode(name)
            if (!decodedName.matches(regex)) {
              error(u)(tree.pos, s"class は [$regex] の形式で命名してください: $decodedName")
            }
            super.traverse(tree)
          case _ =>
            super.traverse(tree)
        }
      }
    }
  }
}
