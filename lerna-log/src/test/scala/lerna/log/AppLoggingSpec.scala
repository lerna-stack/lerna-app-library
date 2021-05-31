package lerna.log

final class AppLoggingSpec extends LernaLogBaseSpec {

  "AppLogging.info should throw no exceptions when it takes standard types" in {
    import SystemComponentLogContext.logContext
    object MyAppLogging extends AppLogging
    val logger = MyAppLogging.logger

    // 次の URL を参考に、 AnyVal, AnyRef型のものを ピックアップ している
    // https://docs.scala-lang.org/tour/unified-types.html

    // AnyVals
    logger.info("Double: {}", 1.0)
    logger.info("Float: {}", 1.0f)
    logger.info("Long: {}", 1L)
    logger.info("Int: {}", 1)
    logger.info("Short: {}", 1.toShort)
    logger.info("Byte: {}", 1.toByte)
    logger.info("Unit: {}", ())
    logger.info("Boolean: {}", true)
    logger.info("Char: {}", 'a')

    // AnyRefs
    logger.info("List: {}", List(1, 2, 3))
    logger.info("Option: {}", Option(2))

    // ScalaNumber
    logger.info("BigInt: {}", BigInt(123))
    logger.info("BigDecimal: {}", BigDecimal(1.23))

  }

}
