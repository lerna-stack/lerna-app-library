package lerna.log

import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{ Actor, Props }
import akka.event.Logging
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import org.slf4j.MDC

@SuppressWarnings(Array("org.wartremover.warts.Null"))
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

    "既存の MDC を保持する" in {
      implicit val logContext: LogContext = new LogContext {
        override protected[log] def mdc: Map[String, String] = Map("logContext_mdc_key" -> "logContext_mdc_value")
      }
      LoggingTestKit
        .custom { event =>
          event.mdc.get("default_mdc_key") === Option("default_mdc_value") &&
          event.mdc.get("logContext_mdc_key") === Option("logContext_mdc_value")
        }
        .expect {
          MDC.put("default_mdc_key", "default_mdc_value")
          LogTest.logger.info("dummy_log")
        }

      expect(MDC.get("default_mdc_key") === "default_mdc_value")
      expect(MDC.get("logContext_mdc_key") === null)
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

    "override def mdc でセットされたMDCを保持する" in {
      implicit val logContext: LogContext = new LogContext {
        override protected[log] def mdc: Map[String, String] = Map("logContext_mdc_key" -> "logContext_mdc_value")
      }
      val probe = createTestProbe[Logging.MDC]()

      LoggingTestKit
        .custom { event =>
          event.mdc.get("static_mdc_key") === Option("static_mdc_value") &&
          event.mdc.get("received_message_key") === Option("dummy_message") &&
          event.mdc.get("logContext_mdc_key") === Option("logContext_mdc_value")
        }
        .expect {
          spawn(Behaviors.setup[Unit] { context => // typed ActorSystem から classic Actor を直接作れないため typed Actor で wrap
            import akka.actor.typed.scaladsl.adapter._
            val actor = context.actorOf(Props(new Actor with AppActorLogging {
              override def mdc(currentMessage: Any): Logging.MDC = {
                val staticMdc  = Map("static_mdc_key" -> "static_mdc_value")
                val perMessage = Map("received_message_key" -> currentMessage.toString)
                staticMdc ++ perMessage
              }
              override def receive: Receive = {
                case _ =>
                  logger.info("dummy_log")
                  probe.ref ! log.mdc
              }
            }))
            actor ! "dummy_message"
            Behaviors.empty
          })
        }

      val mdc = probe.receiveMessage()
      expect(mdc.get("static_mdc_key") === Option("static_mdc_value"))
      expect(mdc.get("received_message_key") === Option("dummy_message"))
      expect(mdc.get("logContext_mdc_key") === None)
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

    "Behaviors.withMdc でセットされたMDCを保持する" in {
      implicit val logContext: LogContext = new LogContext {
        override protected[log] def mdc: Map[String, String] = Map("logContext_mdc_key" -> "logContext_mdc_value")
      }
      object LogTestActor extends AppTypedActorLogging {
        private val staticMdc = Map("static_mdc_key" -> "static_mdc_value")

        def apply(): Behavior[String] = Behaviors.withMdc(
          staticMdc,
          mdcForMessage = (msg: String) => Map("received_message_key" -> msg),
        ) {
          withLogger { logger =>
            Behaviors.receiveMessage { _ =>
              logger.info("dummy_log")
              Behaviors.stopped
            }
          }
        }
      }
      LoggingTestKit
        .custom { event =>
          event.mdc.get("static_mdc_key") === Option("static_mdc_value") &&
          event.mdc.get("received_message_key") === Option("dummy_message") &&
          event.mdc.get("logContext_mdc_key") === Option("logContext_mdc_value")
        }
        .expect {
          val actor = spawn(LogTestActor())
          actor ! "dummy_message"
        }
    }
  }
}
