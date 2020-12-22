package lerna.util.time

import java.sql.Timestamp
import java.time.LocalDateTime

import lerna.util.LernaUtilBaseSpec

final class DateTimeConvertersSpec extends LernaUtilBaseSpec {
  import DateTimeConverters._

  "DateTimeConverters" should {
    "convert java.time.LocalDateTime to java.sql.Timestamp" in {
      val localDateTime = LocalDateTime.of(2020, 10, 27, 10, 57, 45, 123456789)
      val timestamp     = localDateTime.asTimestamp
      timestamp shouldBe Timestamp.valueOf("2020-10-27 10:57:45.123456789")
    }
  }

}
