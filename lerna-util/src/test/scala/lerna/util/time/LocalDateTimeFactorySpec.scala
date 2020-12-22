package lerna.util.time

import lerna.util.LernaUtilBaseSpec

final class LocalDateTimeFactorySpec extends LernaUtilBaseSpec {

  classOf[LocalDateTimeFactory].getSimpleName + ".apply" should {
    "create a LocalDateTimeFactoryImpl instance" in {
      val factory = LocalDateTimeFactory()
      factory shouldBe a[LocalDateTimeFactoryImpl]
    }
  }

}
