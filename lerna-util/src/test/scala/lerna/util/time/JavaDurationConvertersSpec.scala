package lerna.util.time

import java.time.{ Duration => JavaDuration }
import java.util.concurrent.TimeUnit

import lerna.util.LernaUtilBaseSpec

import scala.concurrent.duration.FiniteDuration

class JavaDurationConvertersSpec extends LernaUtilBaseSpec {

  "JavaDurationConverters" should {
    import JavaDurationConverters._

    "Java -> Scala の変換" in {
      val javaDuration = JavaDuration.ofNanos(1)
      expect(javaDuration.asScala === FiniteDuration(1, TimeUnit.NANOSECONDS))
    }

    "Scala -> Java の変換" in {
      val scalaDuration = FiniteDuration(1, TimeUnit.NANOSECONDS)
      expect(scalaDuration.asJava === JavaDuration.ofNanos(1))
    }

  }
}
