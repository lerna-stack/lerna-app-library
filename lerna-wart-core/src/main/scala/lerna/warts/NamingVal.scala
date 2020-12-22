package lerna.warts

import org.wartremover.{ WartTraverser, WartUniverse }

import scala.reflect.NameTransformer

/** A default [[NamingVal]] rule
  *
  * A value name should match a regular expression `[A-Za-z_$][A-Za-z0-9_$]*`.
  *
  * @note
  *   Some library that is using a macro generates a value whose name contains `$`.
  *   It is difficult to exclude such values from targets of WartRemover.
  *   Therefore, we permit `$` to be a part of a value name.
  */
object NamingVal extends NamingVal("""[A-Za-z_$][A-Za-z0-9_$]*""")

/** A `NamingVal` rule for [[http://www.wartremover.org/ WartRemover]]
  *
  * It would be better to lint a value name.
  *
  * @param regex A regular expression that a value name should match
  */
class NamingVal(regex: String) extends WartTraverser {

  def apply(u: WartUniverse): u.Traverser = {
    import u.universe._
    new Traverser {
      override def traverse(tree: Tree): Unit = {
        tree match {
          // Ignore trees marked by SuppressWarnings
          case t if hasWartAnnotation(u)(t) =>
          case ValDef(modifiers, TermName(name), _, _) if !modifiers.hasFlag(Flag.MUTABLE) && !isSynthetic(u)(tree) =>
            val normalizedName = NameTransformer.decode(name).trim()
            if (!normalizedName.matches(regex)) {
              error(u)(tree.pos, s"val は [$regex] の形式で命名してください: $normalizedName")
            }
            super.traverse(tree)
          case _ =>
            super.traverse(tree)
        }
      }
    }
  }
}
