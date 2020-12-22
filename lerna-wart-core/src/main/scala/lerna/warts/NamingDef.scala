package lerna.warts

import org.wartremover.{ WartTraverser, WartUniverse }

import scala.reflect.NameTransformer

/** A default [[NamingDef]] rule
  *
  * A method name should match a regular expression `[a-z_$][A-Za-z0-9$]*`.
  *
  * @note
  *   Some library that is using a macro generates a method whose name contains `$`.
  *   It is difficult to exclude such methods from targets of WartRemover.
  *   Therefore, we permit `$` to be a part of a method name.
  */
object NamingDef extends NamingDef("""[a-z_$][A-Za-z0-9$]*""")

/** A `NamingDef` rule for [[http://www.wartremover.org/ WartRemover]]
  *
  * It would be better to lint a method name.
  *
  * @param regex A regular expression that a method name should match
  */
class NamingDef(regex: String) extends WartTraverser {

  def apply(u: WartUniverse): u.Traverser = {
    import u.universe._

    def isConstructorName(name: String) = name == u.universe.termNames.CONSTRUCTOR.decodedName.toString

    new Traverser {
      override def traverse(tree: Tree): Unit = {
        tree match {
          // Ignore trees marked by SuppressWarnings
          case t if hasWartAnnotation(u)(t) =>
          case DefDef(_, TermName(name), _, _, _, _) if !isSynthetic(u)(tree) && !isConstructorName(name) =>
            val decodedName = NameTransformer.decode(name)
            if (!decodedName.matches(regex)) {
              error(u)(tree.pos, s"def は [$regex] の形式で命名してください: $decodedName")
            }
            super.traverse(tree)
          case _ =>
            super.traverse(tree)
        }
      }
    }
  }
}
