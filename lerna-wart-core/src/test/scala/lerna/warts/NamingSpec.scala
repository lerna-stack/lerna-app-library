package lerna.warts

import org.scalatest.{ DiagrammedAssertions, WordSpec }
import org.wartremover.test.WartTestTraverser

class NamingSpec extends WordSpec with DiagrammedAssertions {

  "NamingClass" should {

    "命名準拠" in {
      val result = WartTestTraverser(new NamingClass("""[A-Z][A-Za-z]*""")) {
        class Valid
      }
      assert(result.errors.isEmpty)
    }

    "命名違反" in {
      val result = WartTestTraverser(new NamingClass("""[A-Z][A-Za-z]*""")) {
        class invalid
      }
      assert(result.errors.contains("[wartremover:NamingClass] class は [[A-Z][A-Za-z]*] の形式で命名してください: invalid"))
    }

    "disable a `NamingClass` violation detection if the SuppressWarnings is used" in {
      val result = WartTestTraverser(new NamingClass("""[A-Z][A-Za-z]*""")) {
        @SuppressWarnings(Array("lerna.warts.NamingClass"))
        class invalid
      }
      assert(result.errors.isEmpty)
    }

    "特殊記号を含むクラス名がデコード後に評価されること" in {
      // 特殊記号は$プリフィックス付きでエンコードされるため命名規則で$を許可しておき、エラーが検出されるかどうか確認する
      // See:
      //  - https://www.ne.jp/asahi/hishidama/home/tech/scala/reflect.html
      //  - https://www.scala-lang.org/api/2.13.0/scala/reflect/NameTransformer%24.html
      val result = WartTestTraverser(new NamingClass("""[A-Z$][A-Za-z$]*""")) {
        class `name:abc`
      }
      assert(result.errors.contains("[wartremover:NamingClass] class は [[A-Z$][A-Za-z$]*] の形式で命名してください: name:abc"))
    }

    "命名準拠 - コンパニオンオブジェクト(クラス側)" in {
      // コンパニオンオブジェクトを使い、命名規則に$を含めなくても命名規則準拠になるか確認する
      val result = WartTestTraverser(new NamingClass("""[A-Z][A-Za-z]*""")) {
        class Valid
        object Valid
      }
      assert(result.errors.isEmpty)
    }

    "命名準拠 - ケースクラス(クラス側)" in {
      // ケースクラスを使い、命名規則に$を含めなくてもパスするか確認する
      val result = WartTestTraverser(new NamingClass("""[A-Z][A-Za-z]*""")) {
        case class Valid()
      }
      assert(result.errors.isEmpty)
    }

  }

  "NamingObject" should {

    "命名準拠" in {
      val result = WartTestTraverser(new NamingObject("""[A-Z][A-Za-z]*""")) {
        object Valid
      }
      assert(result.errors.isEmpty)
    }

    "命名違反" in {
      val result = WartTestTraverser(new NamingObject("""[A-Z][A-Za-z]*""")) {
        object invalid
      }
      assert(result.errors.contains("[wartremover:NamingObject] object は [[A-Z][A-Za-z]*] の形式で命名してください: invalid"))
    }

    "disable a `NamingObject` violation detection if the SuppressWarnings is used" in {
      val result = WartTestTraverser(new NamingObject("""[A-Z][A-Za-z]*""")) {
        @SuppressWarnings(Array("lerna.warts.NamingObject"))
        object invalid
      }
      assert(result.errors.isEmpty)
    }

    "特殊記号を含むオブジェクト名がデコード後に評価されること" in {
      // 特殊記号は$プリフィックス付きでエンコードされるため命名規則で$を許可しておき、エラーが検出されるかどうか確認する
      // See:
      //  - https://www.ne.jp/asahi/hishidama/home/tech/scala/reflect.html
      //  - https://www.scala-lang.org/api/2.13.0/scala/reflect/NameTransformer%24.html
      val result = WartTestTraverser(new NamingObject("""[A-Z$][A-Za-z$]*""")) {
        object `name:abc`
      }
      assert(result.errors.contains("[wartremover:NamingObject] object は [[A-Z$][A-Za-z$]*] の形式で命名してください: name:abc"))
    }

    "命名準拠 - コンパニオンオブジェクト(オブジェクト側)" in {
      // コンパニオンオブジェクトを使い、命名規則に$を含めなくても命名規則準拠になるか確認する
      val result = WartTestTraverser(new NamingObject("""[A-Z][A-Za-z]*""")) {
        class Valid
        object Valid
      }
      assert(result.errors.isEmpty)
    }

    "命名準拠 - ケースクラス(オブジェクト側)" in {
      // ケースクラスを使い、命名規則に$を含めなくてもパスするか確認する
      val result = WartTestTraverser(new NamingObject("""[A-Z][A-Za-z]*""")) {
        case class Valid()
      }
      assert(result.errors.isEmpty)
    }

  }

  "NamingDef" should {

    "命名準拠" in {
      val result = WartTestTraverser(new NamingDef("""[a-z][A-Za-z]*""")) {
        def valid = "1"
      }
      assert(result.errors.isEmpty)
    }

    "命名違反" in {
      val result = WartTestTraverser(new NamingDef("""[a-z][A-Za-z]*""")) {
        def Invalid = "1"
      }
      assert(result.errors.contains("[wartremover:NamingDef] def は [[a-z][A-Za-z]*] の形式で命名してください: Invalid"))
    }

    "disable a `NamingDef` violation detection if the SuppressWarnings is used" in {
      val result = WartTestTraverser(new NamingDef("""[a-z][A-Za-z]*""")) {
        @SuppressWarnings(Array("lerna.warts.NamingDef"))
        def Invalid = "1"
      }
      assert(result.errors.isEmpty)
    }

    "特殊記号を含むメソッド名がデコード後に評価されること" in {
      // 特殊記号は$プリフィックス付きでエンコードされるため命名規則で$を許可しておき、エラーが検出されるかどうか確認する
      // See:
      //  - https://www.ne.jp/asahi/hishidama/home/tech/scala/reflect.html
      //  - https://www.scala-lang.org/api/2.13.0/scala/reflect/NameTransformer%24.html
      val result = WartTestTraverser(new NamingDef("""[a-z$][A-Za-z$]*""")) {
        def `name:abc`(): String = "1"
      }
      assert(result.errors.contains("[wartremover:NamingDef] def は [[a-z$][A-Za-z$]*] の形式で命名してください: name:abc"))
    }

    "命名規則からコンストラクタを除外すること " in {
      val result = WartTestTraverser(new NamingDef("""abc""")) {
        class Test(value: Int) {
          def this() = this(0)
        }
      }
      assert(result.errors.isEmpty)
    }
  }

  "NamingVal" should {

    "命名準拠 - val" in {
      val result = WartTestTraverser(new NamingVal("""[a-z][A-Za-z]*""")) {
        val valid = "1"
      }
      assert(result.errors.isEmpty)
    }

    "命名違反 - val" in {
      val result = WartTestTraverser(new NamingVal("""[a-z][A-Za-z]*""")) {
        val Invalid = "1"
      }
      assert(result.errors.contains("[wartremover:NamingVal] val は [[a-z][A-Za-z]*] の形式で命名してください: Invalid"))
    }

    "disable a `NamingVal` violation detection if the SuppressWarnings is used" in {
      val result = WartTestTraverser(new NamingVal("""[a-z][A-Za-z]*""")) {
        @SuppressWarnings(Array("lerna.warts.NamingVal"))
        val Invalid = "1"
      }
      assert(result.errors.isEmpty)
    }

    "特殊記号を含む変数名がデコード後に評価されること - val" in {
      // 特殊記号は$プリフィックス付きでエンコードされるため命名規則で$を許可しておき、エラーが検出されるかどうか確認する
      // See:
      //  - https://www.ne.jp/asahi/hishidama/home/tech/scala/reflect.html
      //  - https://www.scala-lang.org/api/2.13.0/scala/reflect/NameTransformer%24.html
      val result = WartTestTraverser(new NamingVal("""[a-z$][A-Za-z$]*""")) {
        val `name:abc` = "1"
      }
      assert(result.errors.contains("[wartremover:NamingVal] val は [[a-z$][A-Za-z$]*] の形式で命名してください: name:abc"))
    }

    "命名準拠 - var" in {
      val result = WartTestTraverser(new NamingVar("""[a-z][A-Za-z]*""")) {
        var valid = "1"
      }
      assert(result.errors.isEmpty)
    }

    "命名違反 - var" in {
      val result = WartTestTraverser(new NamingVar("""[a-z][A-Za-z]*""")) {
        var Invalid = "1"
      }
      assert(result.errors.contains("[wartremover:NamingVar] var は [[a-z][A-Za-z]*] の形式で命名してください: Invalid"))
    }

    "diable a `NamingVar` violation detection if the SuppressWarnings is used" in {
      val result = WartTestTraverser(new NamingVar("""[a-z][A-Za-z]*""")) {
        @SuppressWarnings(Array("lerna.warts.NamingVar"))
        var Invalid = "1"
      }
      assert(result.errors.isEmpty)
    }

    "特殊記号を含む変数名がデコード後に評価されること - var" in {
      // 特殊記号は$プリフィックス付きでエンコードされるため命名規則で$を許可しておき、エラーが検出されるかどうか確認する
      // See:
      //  - https://www.ne.jp/asahi/hishidama/home/tech/scala/reflect.html
      //  - https://www.scala-lang.org/api/2.13.0/scala/reflect/NameTransformer%24.html
      val result = WartTestTraverser(new NamingVar("""[a-z$][A-Za-z$]*""")) {
        var `name:abc` = "1"
      }
      assert(result.errors.contains("[wartremover:NamingVar] var は [[a-z$][A-Za-z$]*] の形式で命名してください: name:abc"))
    }

  }

  "NamingPackage" ignore {}

  "NamingPackageObject" ignore {}
}
