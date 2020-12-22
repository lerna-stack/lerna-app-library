package lerna.tests

final class LernaBaseSpecSpec extends LernaBaseSpec {
  "LernaBaseSpec" should {

    "1項目だけチェックする場合" in {
      expect("1" === "1")

      /** エラー出力の例：
        * java.lang.AssertionError:
        *
        * expect("2" === "1")
        *        |   |
        *       2   false
        */
    }

    "複数項目チェックする場合" in {
      expect {
        "1" === "1"
        "2" === "2"
      }
    }

    "条件を満たさない場合はAssertionErrorになる" in {
      assertThrows[AssertionError] {
        expect("1" === "2")
      }
    }
  }
}
