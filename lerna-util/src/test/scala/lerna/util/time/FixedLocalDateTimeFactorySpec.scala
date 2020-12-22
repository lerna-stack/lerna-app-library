package lerna.util.time

import java.time.{ ZoneOffset, ZonedDateTime }
import java.time.format.DateTimeFormatter

import lerna.util.LernaUtilBaseSpec

// Airframe が生成するコードにより誤検知が発生する
@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
class FixedLocalDateTimeFactorySpec extends LernaUtilBaseSpec {

  "FixedDateTimeFactory" should {

    "固定の日付を発行できる" in {
      val factory  = FixedLocalDateTimeFactory("2019-05-01T00:00:00Z")
      val dateTime = factory.now()
      expect(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) === "2019-05-01T00:00:00")
    }

    "take the zoned time (both instant and zone) to use as fixed local date time" in {
      val zonedDateTime      = ZonedDateTime.of(2020, 11, 6, 14, 15, 12, 0, ZoneOffset.ofHours(-7))
      val factory            = FixedLocalDateTimeFactory(zonedDateTime.toInstant, zonedDateTime.getZone)
      val fixedLocalDateTime = factory.now()
      expect(fixedLocalDateTime === zonedDateTime.toLocalDateTime)
    }

  }

}
