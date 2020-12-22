package lerna.util.akka

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

import com.typesafe.config.{ Config, ConfigFactory }
import lerna.util.time.{ FixedLocalDateTimeFactory, LocalDateTimeFactory }
import lerna.util.trace.TraceId

import scala.concurrent.duration.FiniteDuration

class ProcessingTimeoutSpec extends LernaAkkaBaseSpec {
  implicit val traceId: TraceId = TraceId.unknown

  "deadline" should {
    "(Eventの時刻 + askTimeout + fail-safe-margin) の時刻になる" in {
      val acceptedDateTime: LocalDateTime = LocalDateTime.now()
      val askTimeout: FiniteDuration      = FiniteDuration(1234, TimeUnit.MILLISECONDS)
      val config: Config =
        ConfigFactory.parseString("lerna.util.akka.processing-timeout.fail-safe-margin = 3 s")

      val processingTimeout: ProcessingTimeout = ProcessingTimeout(acceptedDateTime, askTimeout, config)

      expect(processingTimeout.deadline === acceptedDateTime.plusNanos(1234 * 1000 * 1000).plusSeconds(3))
    }
  }

  "timeLeft()" should {
    "(Eventの時刻 + askTimeout + fail-safe-margin - 現在時刻) の時間(Duration)になる" in {
      val config: Config =
        ConfigFactory.parseString("lerna.util.akka.processing-timeout.fail-safe-margin = 3 s")
      implicit val dateTimeFactory: LocalDateTimeFactory = FixedLocalDateTimeFactory("2019-05-01T00:00:00Z")

      // 500ms 前
      val acceptedDateTime: LocalDateTime = dateTimeFactory.now().minusNanos(500 * 1000 * 1000)
      val askTimeout: FiniteDuration      = FiniteDuration(5000, TimeUnit.MILLISECONDS)

      val processingTimeout: ProcessingTimeout = ProcessingTimeout(acceptedDateTime, askTimeout, config)
      val result                               = processingTimeout.timeLeft

      // 5000ms(askTimeout) + 3s(fail-safe-margin) - 500ms(時刻の差)
      val expectedResult = FiniteDuration(7500, TimeUnit.MILLISECONDS)
      expect { expectedResult === result }
    }
  }
}
