package lerna.http.json

import lerna.http.LernaHttpBaseSpec

final case class TestString(testString: String)

class SnakifiedSprayJsonSupportSpec extends LernaHttpBaseSpec with SnakifiedSprayJsonSupport {
  import spray.json._
  implicit val testStringJsonFormat: RootJsonFormat[TestString] = jsonFormat1(TestString)

  "キャメルケースのクラスのメンバをスネークケースのjsonのパラメータ名に変換する" in {
    val jsonString = TestString("test1").toJson

    val jsonObject = """{"test_string": "test2" }""".parseJson.convertTo[TestString]

    expect {
      jsonString.toString === """{"test_string":"test1"}"""
      jsonObject.testString === "test2"
    }

  }

}
