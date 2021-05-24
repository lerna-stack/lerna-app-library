package lerna.testkit

import com.eed3si9n.expecty.Expecty
import org.scalatest.exceptions.TestFailedException

@SuppressWarnings(
  Array("org.wartremover.warts.Null"),
)
final class EqualsSupportSpec extends LernaTestKitBaseSpec {
  // NOTE: EqualsSupport is already mixed in to LernaTestKitBaseSpec

  "EqualsSupport" should {

    "provide type checked triple equals" in {

      assert("123" === "123")
      a[TestFailedException] shouldBe thrownBy {
        assert("123" === "124")
      }

      assert("123" !== "124")
      a[TestFailedException] shouldBe thrownBy {
        assert("123" !== "123")
      }

      assertDoesNotCompile(""" "123" === 123 """)
      assertDoesNotCompile(""" 123 === 123.0 """)

    }

    "provide type checked triple equals that can handle null on the left-hand side" in {

      val nullValue: String = null

      assert(nullValue === null)
      a[TestFailedException] shouldBe thrownBy {
        assert(nullValue === "abc")
      }

      assert(nullValue !== "")
      a[TestFailedException] shouldBe thrownBy {
        assert(nullValue !== null)
      }

    }

    "provide great support for expecty" in {
      val expect = new Expecty {
        override val failEarly: Boolean = false
      }

      val value1: String = "abc"
      val e1 = intercept[AssertionError] {
        expect { value1 === "def" }
      }
      val message1 = e1.getMessage.linesIterator.toSeq
      message1 shouldBe Seq(
        "assertion failed ",
        """""",
        """expect { value1 === "def" }""",
        """         |      |""",
        """         abc    false""",
      )

      val nullValue: String = null
      val e2 = intercept[AssertionError] {
        expect { nullValue === "def" }
      }
      val message2 = e2.getMessage.linesIterator.toSeq
      message2 shouldBe Seq(
        """assertion failed """,
        "",
        """expect { nullValue === "def" }""",
        """         |         |""",
        """         null      false""",
      )
    }

  }
}
