package lerna.util.akka

import java.util.concurrent.atomic.AtomicInteger
import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.FishingOutcomes
import akka.actor.typed.ActorRef
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.akka.AtLeastOnceDelivery.AtLeastOnceDeliveryRequest
import lerna.util.trace.TraceId

import scala.concurrent.duration._

object AtLeastOnceDeliverySpec {
  private val redeliverInterval = 100.milliseconds
  private val retryTimeout      = 1000.milliseconds

  private val config: Config = ConfigFactory
    .parseString(s"""
         | akka.actor {
         |   provider = local
         |   serialize-messages = on
         |   serialization-bindings {
         |     "lerna.util.akka.AtLeastOnceDeliverySpec$$RequestMessage" = jackson-json
         |     "lerna.util.akka.AtLeastOnceDeliverySpec$$ResponseMessage" = jackson-json
         |   }
         | }
         | lerna.util.akka.at-least-once-delivery {
         |   redeliver-interval = ${redeliverInterval.toMillis.toString} ms
         |   retry-timeout = ${retryTimeout.toMillis.toString} ms
         | }
       """.stripMargin)
    .withFallback(ConfigFactory.load())

  final case class RequestMessage(message: String)
  final case class ResponseMessage(message: String)
}

class AtLeastOnceDeliverySpec
    extends LernaAkkaActorBaseSpec(ActorSystem("AtLeastOnceDeliverySpec", AtLeastOnceDeliverySpec.config)) {

  import AtLeastOnceDeliverySpec._

  implicit val askTimeout: Timeout = 5.seconds
  implicit val traceId: TraceId    = TraceId.unknown

  "askTo()" should {
    "宛先に到達保証用メッセージを送信できる" in {
      val destinationProbe = TestProbe()

      val requestMessage  = RequestMessage(s"request-${generateUniqueNumber().toString}")
      val responseMessage = ResponseMessage(s"response-${generateUniqueNumber().toString}")

      val resultFuture = AtLeastOnceDelivery.askTo(destinationProbe.ref, requestMessage).mapTo[ResponseMessage]

      {
        // destination側
        val request = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request.originalMessage === requestMessage)

        request.accept()
        destinationProbe.reply(responseMessage)
      }

      whenReady(resultFuture) { result =>
        expect(result === responseMessage)
      }
    }

    "accept()されない場合は再送する" in {
      val destinationProbe = TestProbe()
      val requestMessage   = RequestMessage(s"request-${generateUniqueNumber().toString}")

      AtLeastOnceDelivery.askTo(destinationProbe.ref, requestMessage)

      {
        // destination側
        val request1 = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request1.originalMessage === requestMessage)

        val request2 = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request2.originalMessage === requestMessage)

        val request3 = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request3.originalMessage === requestMessage)

        val request4 = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request4.originalMessage === requestMessage)

        request4.accept()
        destinationProbe.expectNoMessage()
      }
    }

    "retry-timeout時間経過しても accept()されなかった場合再送を中止する" in {
      val destinationProbe = TestProbe()

      val requestMessage = RequestMessage(s"request-${generateUniqueNumber().toString}")

      AtLeastOnceDelivery.askTo(destinationProbe.ref, requestMessage)

      {
        // destination側
        destinationProbe.receiveWhile(max = retryTimeout * 1.1) {
          case request: AtLeastOnceDeliveryRequest =>
            expect(request.originalMessage === requestMessage)
        }

        destinationProbe.expectNoMessage()
      }
    }

    "ask-timeout時間経過しても 結果が帰ってこなかった場合 Future.failed(AskTimeoutException) となる" in {
      implicit val askTimeout: Timeout = 100.milliseconds

      val destinationProbe = TestProbe()
      val requestMessage   = RequestMessage(s"request-${generateUniqueNumber().toString}")

      val result = AtLeastOnceDelivery.askTo(destinationProbe.ref, requestMessage)

      whenReady(result.failed) { throwable =>
        throwable shouldBe a[akka.pattern.AskTimeoutException]
      }
    }
  }

  "tellTo()" should {
    "宛先に到達保証用メッセージを送信できる" in {
      val destinationProbe = TestProbe()

      val requestMessage  = RequestMessage(s"request-${generateUniqueNumber().toString}")
      val responseMessage = ResponseMessage(s"response-${generateUniqueNumber().toString}")

      AtLeastOnceDelivery.tellTo(destinationProbe.ref, requestMessage)

      {
        // destination側
        val request = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request.originalMessage === requestMessage)

        request.accept()
        destinationProbe.reply(responseMessage)
      }

      expectMsg(responseMessage)
    }

    "accept()されない場合は再送する" in {
      val destinationProbe = TestProbe()

      val requestMessage = RequestMessage(s"request-${generateUniqueNumber().toString}")

      AtLeastOnceDelivery.tellTo(destinationProbe.ref, requestMessage)

      {
        // destination側
        val request1 = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request1.originalMessage === requestMessage)

        val request2 = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request2.originalMessage === requestMessage)

        val request3 = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request3.originalMessage === requestMessage)

        val request4 = destinationProbe.expectMsgType[AtLeastOnceDeliveryRequest]
        expect(request4.originalMessage === requestMessage)

        request4.accept()
        destinationProbe.expectNoMessage()
      }
    }

    "retry-timeout時間経過しても accept()されなかった場合再送を中止する" in {
      val destinationProbe = TestProbe()

      val requestMessage = RequestMessage(s"request-${generateUniqueNumber().toString}")

      AtLeastOnceDelivery.askTo(destinationProbe.ref, requestMessage)

      {
        // destination側
        destinationProbe.receiveWhile(max = retryTimeout * 1.1) {
          case request: AtLeastOnceDeliveryRequest =>
            expect(request.originalMessage === requestMessage)
        }

        destinationProbe.expectNoMessage()
      }
    }
  }

  private val generateUniqueNumber: () => Int = {
    val counter = new AtomicInteger()
    () => counter.getAndIncrement()
  }
}

object AtLeastOnceDeliveryTypedSpec {
  private val redeliverInterval = 100.milliseconds
  private val retryTimeout      = 1000.milliseconds

  private val config: Config = ConfigFactory
    .parseString(s"""
                    | akka.actor {
                    |   provider = local
                    |   serialize-messages = on
                    |   serialization-bindings {
                    |     "lerna.util.akka.AtLeastOnceDeliveryTypedSpec$$RequestMessage" = jackson-json
                    |     "lerna.util.akka.AtLeastOnceDeliveryTypedSpec$$ResponseMessage" = jackson-json
                    |   }
                    | }
                    | lerna.util.akka.at-least-once-delivery {
                    |   redeliver-interval = ${redeliverInterval.toMillis.toString} ms
                    |   retry-timeout = ${retryTimeout.toMillis.toString} ms
                    | }
       """.stripMargin)
    .withFallback(ConfigFactory.load())

  final case class RequestMessage(
      message: String,
      replyTo: ActorRef[ResponseMessage],
      confirmTo: ActorRef[AtLeastOnceDelivery.Confirm],
  )
  final case class ResponseMessage(message: String)
}

class AtLeastOnceDeliveryTypedSpec
    extends ScalaTestWithTypedActorTestKit(AtLeastOnceDeliveryTypedSpec.config)
    with LernaBaseSpec {

  import AtLeastOnceDeliveryTypedSpec._

  implicit val askTimeout: Timeout = 5.seconds
  implicit val traceId: TraceId    = TraceId.unknown

  "askTo()" should {
    "宛先に到達保証用メッセージを送信できる" in {
      val destinationProbe = testKit.createTestProbe[RequestMessage]()

      val requestMessage  = s"request-${generateUniqueNumber().toString}"
      val responseMessage = ResponseMessage(s"response-${generateUniqueNumber().toString}")

      val resultFuture = AtLeastOnceDelivery.askTo(destinationProbe.ref, RequestMessage(requestMessage, _, _))

      {
        // destination側
        val request = destinationProbe.receiveMessage()
        expect(request.message === requestMessage)

        request.confirmTo ! AtLeastOnceDelivery.Confirm
        request.replyTo ! responseMessage
      }

      whenReady(resultFuture) { result =>
        expect(result === responseMessage)
      }
    }

    "confirm されない場合は再送する" in {
      val destinationProbe = testKit.createTestProbe[RequestMessage]()
      val requestMessage   = s"request-${generateUniqueNumber().toString}"

      AtLeastOnceDelivery.askTo(destinationProbe.ref, RequestMessage(requestMessage, _, _))

      {
        // destination側
        val request1 = destinationProbe.receiveMessage()
        expect(request1.message === requestMessage)

        val request2 = destinationProbe.receiveMessage()
        expect(request2.message === requestMessage)

        val request3 = destinationProbe.receiveMessage()
        expect(request3.message === requestMessage)

        val request4 = destinationProbe.receiveMessage()
        expect(request4.message === requestMessage)

        request4.confirmTo ! AtLeastOnceDelivery.Confirm
        destinationProbe.expectNoMessage()
      }
    }

    "retry-timeout時間経過しても confirm されなかった場合再送を中止する" in {
      val destinationProbe = testKit.createTestProbe[RequestMessage]()

      val requestMessage = s"request-${generateUniqueNumber().toString}"

      AtLeastOnceDelivery.askTo(destinationProbe.ref, RequestMessage(requestMessage, _, _))

      {
        // destination側
        val assertionError = intercept[AssertionError] { // wait for a timeout
          destinationProbe.fishForMessage(max = retryTimeout * 2) {
            case request if request.message === requestMessage => FishingOutcomes.continueAndIgnore
            case _                                             => FishingOutcomes.fail("unexpected message")
          }
        }
        expect(assertionError.getMessage.startsWith("timeout"))

        destinationProbe.expectNoMessage()
      }
    }

    "ask-timeout時間経過しても 結果が帰ってこなかった場合 Future.failed(TimeoutException) となる" in {
      val askTimeout: Timeout = 100.milliseconds

      val destinationProbe = testKit.createTestProbe[RequestMessage]()
      val requestMessage   = s"request-${generateUniqueNumber().toString}"

      val result = AtLeastOnceDelivery.askTo(destinationProbe.ref, RequestMessage(requestMessage, _, _))(
        requestContext,
        system,
        askTimeout,
      )

      whenReady(result.failed) { throwable =>
        throwable shouldBe a[java.util.concurrent.TimeoutException]
      }
    }
  }

  "tellTo()" should {
    "宛先に到達保証用メッセージを送信できる" in {
      val destinationProbe = testKit.createTestProbe[RequestMessage]()
      val replyToProbe     = testKit.createTestProbe[ResponseMessage]()

      val requestMessage  = s"request-${generateUniqueNumber().toString}"
      val responseMessage = ResponseMessage(s"response-${generateUniqueNumber().toString}")

      AtLeastOnceDelivery.tellTo(destinationProbe.ref, RequestMessage(requestMessage, replyToProbe.ref, _))

      {
        // destination側
        val request = destinationProbe.receiveMessage()
        expect(request.message === requestMessage)

        request.confirmTo ! AtLeastOnceDelivery.Confirm
        request.replyTo ! responseMessage
      }

      replyToProbe.expectMessage(responseMessage)
    }

    "confirm されない場合は再送する" in {
      val destinationProbe = testKit.createTestProbe[RequestMessage]()
      val replyToProbe     = testKit.createTestProbe[ResponseMessage]()

      val requestMessage = s"request-${generateUniqueNumber().toString}"

      AtLeastOnceDelivery.tellTo(destinationProbe.ref, RequestMessage(requestMessage, replyToProbe.ref, _))

      {
        // destination側
        val request1 = destinationProbe.receiveMessage()
        expect(request1.message === requestMessage)

        val request2 = destinationProbe.receiveMessage()
        expect(request2.message === requestMessage)

        val request3 = destinationProbe.receiveMessage()
        expect(request3.message === requestMessage)

        val request4 = destinationProbe.receiveMessage()
        expect(request4.message === requestMessage)

        request4.confirmTo ! AtLeastOnceDelivery.Confirm
        destinationProbe.expectNoMessage()
        replyToProbe.expectNoMessage()
      }
    }

    "retry-timeout時間経過しても confirm されなかった場合再送を中止する" in {
      val destinationProbe = testKit.createTestProbe[RequestMessage]()

      val requestMessage = s"request-${generateUniqueNumber().toString}"

      AtLeastOnceDelivery.askTo(destinationProbe.ref, RequestMessage(requestMessage, _, _))

      {
        // destination側
        val assertionError = intercept[AssertionError] { // wait for a timeout
          destinationProbe.fishForMessage(max = retryTimeout * 2) {
            case request if request.message === requestMessage => FishingOutcomes.continueAndIgnore
            case _                                             => FishingOutcomes.fail("unexpected message")
          }
        }
        expect(assertionError.getMessage.startsWith("timeout"))

        destinationProbe.expectNoMessage()
      }
    }
  }

  private val generateUniqueNumber: () => Int = {
    val counter = new AtomicInteger()
    () => counter.getAndIncrement()
  }
}
