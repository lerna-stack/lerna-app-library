package lerna.warts

import org.wartremover.{ WartTraverser, WartUniverse }

/** A default [[CyclomaticComplexity]] rule
  *
  * The rule defines maximum cyclomatic complexity that can be allowed is 10
  */
object CyclomaticComplexity extends CyclomaticComplexity(maxCyclomaticComplexity = 10)

/** A `CyclomaticComplexity` rule for [[http://www.wartremover.org/ WartRemover]]
  *
  * Cyclomatic Complexity is a software metric.
  * In general, the greater the Cyclomatic Complexity, the more difficult to maintain the software.
  * It would be better to keep Cyclomatic Complexity low.
  *
  * @param maxCyclomaticComplexity Maximum cyclomatic complexity that can be allowed
  * @see [[https://en.wikipedia.org/wiki/Cyclomatic_complexity CyclomaticComplexity]]
  */
class CyclomaticComplexity(maxCyclomaticComplexity: Int) extends WartTraverser {

  def apply(u: WartUniverse): u.Traverser = {
    import u.universe._

    class DefTraverser extends Traverser {

      private[this] val whileName       = TermName("while")
      private[this] val doName          = TermName("do")
      private[this] val andName         = TermName("&&").encodedName
      private[this] val orName          = TermName("||").encodedName
      private[this] val foreachName     = TermName("foreach")
      private[this] val mapName         = TermName("map")
      private[this] val applyOrElseName = TermName("applyOrElse")

      private[this] var _cyclomaticComplexity = 1

      def cyclomaticComplexity: Int = _cyclomaticComplexity

      override def traverse(tree: u.universe.Tree): Unit = {
        tree match {
          // PartialFunction
          // FIXME: PartialFunction の case 中で定義された関数などを個別に計測できない（PartialFunction のサマリーしか得られない）
          case ClassDef(_, _, _, Template((_, _, statements))) if isSyntheticPartialFunction(u)(tree) =>
            statements.foreach {
              case t @ DefDef(_, `applyOrElseName`, _, _, _, _) =>
                super.traverse(t)
                _cyclomaticComplexity -= 1 // いずれの case にも match しなかったケース分（defaultCase$）
              case _ =>
            }
          case If(_, _, Literal(Constant(()))) => // only if
            _cyclomaticComplexity += 1
            super.traverse(tree)
          case If(_, _, _) => // if and else
            _cyclomaticComplexity += 2
            super.traverse(tree)
          case CaseDef(_, _, _) =>
            _cyclomaticComplexity += 1
            super.traverse(tree)
          case LabelDef(`whileName`, _, _) =>
            _cyclomaticComplexity += 1
            super.traverse(tree)
          case LabelDef(`doName`, _, _) =>
            _cyclomaticComplexity += 1
            super.traverse(tree)
          case Select(_, `andName`) =>
            _cyclomaticComplexity += 1
            super.traverse(tree)
          case Select(_, `orName`) =>
            _cyclomaticComplexity += 1
            super.traverse(tree)
          case Select(_, `foreachName`) =>
            _cyclomaticComplexity += 1
            super.traverse(tree)
          case Select(_, `mapName`) =>
            _cyclomaticComplexity += 1
            super.traverse(tree)
          case _ =>
            super.traverse(tree)
        }
      }
    }

    new Traverser {
      override def traverse(tree: Tree): Unit = {

        val synthetic = isSynthetic(u)(tree) // 自動生成メソッドか？

        def checkCyclomaticComplexity(t: u.universe.Tree, identity: TermName): Unit = {
          val defTraverser = new DefTraverser()
          defTraverser.traverse(t)
          if (defTraverser.cyclomaticComplexity > maxCyclomaticComplexity) {
            val identityStr = identity.decodedName.toString.trim // val の末尾に空白が入る
            error(u)(
              tree.pos,
              s"$identityStr の循環的複雑度が ${defTraverser.cyclomaticComplexity} になっています。if や while を減らして循環的複雑度を $maxCyclomaticComplexity 以下にしてください",
            )
          }
        }

        tree match {
          // Ignore trees marked by SuppressWarnings
          case t if hasWartAnnotation(u)(t) =>
          // PartialFunction: def や val で検証済みなので無視
          case ClassDef(_, _, _, _) if isSyntheticPartialFunction(u)(tree) =>
          // コンストラクタ
          case ClassDef(_, classIdentify, _, Template(_, _, body)) =>
            // コンストラクタにある各制御フローの複雑度を集計
            // ただし、メソッド定義やプロパティ定義は除外
            val cyclomaticComplexity =
              body
                .filterNot(t => t.isDef)
                .map { tree =>
                  val defTraverser = new DefTraverser()
                  defTraverser.traverse(tree)
                  defTraverser.cyclomaticComplexity - 1
                }
                .sum + 1
            if (cyclomaticComplexity > maxCyclomaticComplexity) {
              error(u)(
                tree.pos,
                s"${classIdentify.decodedName.toString} のデフォルトコンストラクタの循環的複雑度が $cyclomaticComplexity になっています。if や while を減らして循環的複雑度を $maxCyclomaticComplexity 以下にしてください",
              )
            }
            super.traverse(tree)
          // def
          case t @ DefDef(_, methodName, _, _, _, _) if !synthetic =>
            checkCyclomaticComplexity(t, methodName)
            super.traverse(tree)
          // val
          case t @ ValDef(_, valName, _, _) if !synthetic =>
            checkCyclomaticComplexity(t, valName)
            super.traverse(tree)
          case _ =>
            super.traverse(tree)
        }
      }
    }
  }
}
