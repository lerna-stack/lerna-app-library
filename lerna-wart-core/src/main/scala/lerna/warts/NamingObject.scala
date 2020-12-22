package lerna.warts

import org.wartremover.{ WartTraverser, WartUniverse }

import scala.reflect.NameTransformer

/** A default [[NamingObject]] rule
  *
  * An object name should match a regular expression `[A-Z][A-Za-z0-9]*`.
  */
object NamingObject extends NamingObject("""[A-Z][A-Za-z0-9]*""")

/** A `NamingObject` rule for [[http://www.wartremover.org/ WartRemover]]
  *
  * It would be better to lint an object name.
  *
  * @param regex A regular expression that an object name should match
  */
class NamingObject(regex: String) extends WartTraverser {

  def apply(u: WartUniverse): u.Traverser = {
    import u.universe._
    new Traverser {
      override def traverse(tree: Tree): Unit = {
        tree match {
          // Ignore trees marked by SuppressWarnings
          case t if hasWartAnnotation(u)(t) =>
          case ModuleDef(_, termName, _) if termName.encodedName.toString != "package" && !isSynthetic(u)(tree) =>
            val name = NameTransformer.decode(termName.decodedName.toString)
            if (!name.matches(regex)) {
              error(u)(tree.pos, s"object は [$regex] の形式で命名してください: $name")
            }
            super.traverse(tree)
          case _ =>
            super.traverse(tree)
        }
      }
    }
  }
}
