package lerna.http.json

import java.time.{ DateTimeException, LocalDateTime }

import lerna.http.LernaHttpBaseSpec
import spray.json.{ enrichAny, DeserializationException, JsNumber, JsString, RootJsonFormat }

final class LocalDateTimeJsonFormatSpec extends LernaHttpBaseSpec {
  import spray.json.DefaultJsonProtocol._
  val format: RootJsonFormat[LocalDateTime] = LocalDateTimeJsonFormat("yyyy/MM/dd_HH:mm:ss")

  classOf[LocalDateTimeJsonFormat].getSimpleName should {

    "marshal a LocalDateTime" in {
      val dateTime = LocalDateTime.of(2020, 10, 28, 9, 30, 15)
      val jsObject = format.write(dateTime)
      inside(jsObject) {
        case JsString(value) =>
          value shouldBe "2020/10/28_09:30:15"
      }
    }

    "unmarshal a well formatted LocalDateTime string" in {
      val validDateTime = "2020/10/26_15:56:19".toJson
      val dateTime      = format.read(validDateTime)
      dateTime shouldBe LocalDateTime.of(2020, 10, 26, 15, 56, 19)
    }

    "not unmarshal a invalid formatted LocalDateTime" in {
      val invalidDateTimeJson = "2020/10/27___09:30:15".toJson
      a[DateTimeException] shouldBe thrownBy {
        format.read(invalidDateTimeJson)
      }
    }

    "not unmarshal a invalid Json value" in {
      val invalidJson = JsNumber(1234)
      a[DeserializationException] shouldBe thrownBy {
        format.read(invalidJson)
      }
    }

  }

}
