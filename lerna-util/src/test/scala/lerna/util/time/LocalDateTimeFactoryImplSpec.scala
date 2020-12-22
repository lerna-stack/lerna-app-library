package lerna.util.time

import lerna.util.LernaUtilBaseSpec

final class LocalDateTimeFactoryImplSpec extends LernaUtilBaseSpec {

  (classOf[LocalDateTimeFactoryImpl].getSimpleName + ".now") should {
    "not throw any exception" in {
      val factory = new LocalDateTimeFactoryImpl()
      noException should be thrownBy {
        factory.now()
      }
    }
  }

}
