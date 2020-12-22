package lerna.warts

import org.wartremover.{ WartTraverser, WartUniverse }

/** A `Awaits` rule for [[http://www.wartremover.org/ WartRemover]]
  *
  * [[scala.concurrent.Await.result]] and [[scala.concurrent.Await.ready]] can be used to wait for the completion of [[scala.concurrent.Future]].
  * These methods block a current thread, and it may cause a performance problem.
  * It would be better to avoid using these methods and to use other functions of [[scala.concurrent.Future]] such as [[scala.concurrent.Future.map]]
  */
object Awaits extends WartTraverser {
  def apply(u: WartUniverse): u.Traverser = {
    import u.universe._

    val awaitResult = typeOf[scala.concurrent.Await.type].member(TermName("result"))
    val awaitReady  = typeOf[scala.concurrent.Await.type].member(TermName("ready"))
    new Traverser {
      override def traverse(tree: Tree): Unit = {
        tree match {
          // Ignore trees marked by SuppressWarnings
          case t if hasWartAnnotation(u)(t) =>
          case rt: RefTree if rt.symbol == awaitResult =>
            error(u)(tree.pos, "AwaitResult#result の代わりに for 式や Future#map を使ってください")
          case rt: RefTree if rt.symbol == awaitReady =>
            error(u)(tree.pos, "AwaitResult#ready の代わりに for 式や Future#map を使ってください")
          case _ =>
            super.traverse(tree)
        }
      }
    }
  }
}
