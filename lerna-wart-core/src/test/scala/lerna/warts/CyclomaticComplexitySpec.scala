package lerna.warts

import org.scalatest.{ DiagrammedAssertions, WordSpec }
import org.wartremover.test.WartTestTraverser

class CyclomaticComplexitySpec extends WordSpec with DiagrammedAssertions {

  "CyclomaticComplexity" should {

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（match）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          def complexMethod(): Unit = {
            1 match {
              case 1 =>
              case 2 =>
            }
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 3 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（PartialFunction）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {

          def complexMethod(): Unit = {
            Seq("a").collect {
              case "a" => "a"
              case "b" => "b"
            }
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 3 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（if）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          def complexMethod(): Unit = {
            if ("1" == 1.toString) println(1) else println(2)
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 3 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（if with and）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          def complexMethod(): Unit = {
            if ("1" == 1.toString && "2" == 2.toString) println(1) else println(2)
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 4 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（if with or）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          def complexMethod(): Unit = {
            if ("1" == 1.toString || "2" == 2.toString) println(1) else println(2)
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 4 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（while）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          def complexMethod(): Unit = {
            while (false) {
              while (false) {
                println("test")
              }
            }
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 3 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（do-while）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          def complexMethod(): Unit = {
            do {
              do {
                println("test")
              } while (false)
            } while (false)
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 3 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（for）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          def complexMethod(): Unit = {
            for (_ <- 1 to 2) {
              for (_ <- 1 to 2) {
                println("test")
              }
            }
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 3 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "しきい値を超えない場合はエラーにならない（simple for）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class SimpleClass {
          def simpleMethod(): Unit = {
            for {
              _ <- 1 to 2
              _ <- 1 to 2
            } yield 1
          }
        }
        new SimpleClass().simpleMethod() // suppress never used warning
      }
      assert(result.errors.isEmpty)
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（複合）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          def complexMethod(): Unit = {
            for (_ <- 1 to 2) { // 1
              while ("1" == 2.toString) { // 1
                println("test")
              }
            }
            do {        // 1
              1 match { // 2
                case 1 =>
                  if ("1" == 1.toString && "2" == 2.toString) println(1) else println(2) // 3
                case 2 =>
                  if ("1" == 1.toString || "2" == 2.toString) println(1) else println(2) // 3
              }
            } while ("1" == 2.toString)
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 12 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（入れ子）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          def complexMethod(): Unit = {
            def sub(): Unit = {
              do { // 1
                print("test")
              } while ("1" == 2.toString)
            }
            for (_ <- 1 to 2) { // 1
              while ("1" == 2.toString) { // 1
                sub()
                1 match { // 2
                  case 1 =>
                    if ("1" == 1.toString && "2" == 2.toString) println(1) else println(2) // 3
                  case 2 =>
                    if ("1" == 1.toString || "2" == 2.toString) println(1) else println(2) // 3
                }
              }
            }
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexMethod の循環的複雑度が 12 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（val）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          val complexVal: Int = {
            for (_ <- 1 to 2) {
              for (_ <- 1 to 2) {
                println("test")
              }
            }
            1
          }
        }
        new ComplexClass().complexVal // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] complexVal の循環的複雑度が 3 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "メソッドの循環的複雑度がしきい値を超えた場合はエラー（コンストラクタ）" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        class ComplexClass {
          1 match {
            case 1 =>
            case 2 =>
          }
        }
        new ComplexClass() // suppress never used warning
      }
      val expectedError =
        "[wartremover:CyclomaticComplexity] ComplexClass のデフォルトコンストラクタの循環的複雑度が 3 になっています。if や while を減らして循環的複雑度を 2 以下にしてください"
      assert(result.errors.size === 1)
      assert(result.errors.contains(expectedError))
    }

    "disable a `CyclomaticComplexity` violation detection if the SuppressWarnings i used" in {
      val result = WartTestTraverser(new CyclomaticComplexity(maxCyclomaticComplexity = 2)) {
        @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
        class ComplexClass {
          def complexMethod(): Unit = {
            1 match {
              case 1 =>
              case 2 =>
            }
          }
        }
        new ComplexClass().complexMethod() // suppress never used warning
      }
      assert(result.errors.isEmpty)
    }

  }
}
