package lerna.util.akka

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
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
