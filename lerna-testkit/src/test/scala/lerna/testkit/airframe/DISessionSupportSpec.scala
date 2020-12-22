package lerna.testkit.airframe

import lerna.testkit.LernaTestKitBaseSpec
import wvlet.airframe._

// ① Wartremover のエラーを抑制するアノテーションを定義
// Airframe が生成するコードにより誤検知が発生する
@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
class DISessionSupportSpec extends LernaTestKitBaseSpec with DISessionSupport {

  // ② Design を定義します。
  // 既存の Design を利用できます。
  // 必要であれば、追加で bind を定義することで既存のコンポーネントを上書きできます。
  override val diDesign: Design = newDesign
    .bind[ExampleComponent].toSingleton

  "DISessionSupport" should {

    "DIコンポーネントが session から取得できる" in {
      // ③ Session の build を呼び出して登録されたコンポーネントを取得します
      diSession.build[ExampleComponent] shouldBe a[ExampleComponent]
    }
  }

  class ExampleComponent()
}
