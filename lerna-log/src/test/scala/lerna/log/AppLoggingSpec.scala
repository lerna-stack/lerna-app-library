package lerna.log

import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{ Actor, Props }
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec

final class AppLoggingSpec extends ScalaTestWithTypedActorTestKit() with LernaBaseSpec {

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

  "AppLogging" should {
    object LogTest extends AppLogging

    "LogContext の MDC をセットする" in {
      implicit val logContext: LogContext = new LogContext {
        override protected[log] def mdc: Map[String, String] = Map("dummy_mdc_key" -> "dummy_mdc_value")
      }
      LoggingTestKit
        .custom { event =>
          event.mdc.get("dummy_mdc_key") === Option("dummy_mdc_value") &&
          event.mdc.get("actorPath") === None
        }
        .expect {
          LogTest.logger.info("dummy_log")
        }
    }
  }

  "AppActorLogging" should {
    "LogContext の MDC & actorPath をセットする" in {
      implicit val logContext: LogContext = new LogContext {
        override protected[log] def mdc: Map[String, String] = Map("dummy_mdc_key" -> "dummy_mdc_value")
      }
      LoggingTestKit
        .custom { event =>
          event.mdc.get("dummy_mdc_key") === Option("dummy_mdc_value") &&
          event.mdc.contains("actorPath")
        }
        .expect {
          spawn(Behaviors.setup[Unit] { context => // typed ActorSystem から classic Actor を直接作れないため typed Actor で wrap
            import akka.actor.typed.scaladsl.adapter._
            context.actorOf(Props(new Actor with AppActorLogging {
              logger.info("dummy_log")
              override def receive: Receive = Actor.emptyBehavior
            }))
            Behaviors.empty
          })
        }
    }
  }

  "AppTypedActorLogging" should {
    "LogContext の MDC & actorPath をセットする" in {
      implicit val logContext: LogContext = new LogContext {
        override protected[log] def mdc: Map[String, String] = Map("dummy_mdc_key" -> "dummy_mdc_value")
      }
      object LogTestActor extends AppTypedActorLogging {
        def apply(): Behavior[Unit] = withLogger { logger =>
          logger.info("dummy_log")
          Behaviors.empty
        }
      }
      LoggingTestKit
        .custom { event =>
          event.mdc.get("dummy_mdc_key") === Option("dummy_mdc_value") &&
          event.mdc.contains("actorPath")
        }
        .expect {
          spawn(LogTestActor())
        }
    }
  }
}
