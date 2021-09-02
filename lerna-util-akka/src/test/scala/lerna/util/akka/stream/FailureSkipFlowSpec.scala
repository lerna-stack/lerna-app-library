package lerna.util.akka.stream

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import lerna.util.akka.LernaAkkaActorBaseSpec

import scala.concurrent.Future

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class FailureSkipFlowSpec extends LernaAkkaActorBaseSpec(ActorSystem("FailureSkipFlowSpec")) {

  import system.dispatcher

  "FailureSkipFlow" should {

    "入力された要素が指定された Flow 処理される" in {
      val elements = 1 to 4

      val originalFlow = Flow[Int].map(identity)
      val flow = FailureSkipFlow(originalFlow) { (_, _) =>
        // do nothing
      }
      val result = Source(elements).via(flow).runWith(Sink.seq[Int])
      whenReady(result) { seq =>
        expect {
          seq === elements
        }
      }
    }

    "失敗時にコールバックが呼ばれる" in {
      val elements                    = 1 to 4
      val failure                     = new RuntimeException("bang!")
      var failureElement: Option[Int] = None
      var cause: Option[Throwable]    = None
      val originalFlow = Flow[Int].map { i =>
        if (i === 3) throw failure
        i
      }
      val flow = FailureSkipFlow(originalFlow) { (element, ex) =>
        failureElement = Option(element)
        cause = Option(ex)
      }
      val result = Source(elements).via(flow).runWith(Sink.seq[Int])

      whenReady(result) { seq =>
        expect(seq === Seq(1, 2, 4))
        expect(failureElement === Option(3))
        expect(cause === Option(failure))
      }
    }

    "並列処理を書いても逐次処理として処理され、原因となった要素が取得できる(throw)" in {
      val elements                    = 1 to 8
      var failureElement: Option[Int] = None

      val originalFlow = Flow[Int].mapAsyncUnordered(8) {
        case 3 =>
          throw new RuntimeException("bang!")
        case i =>
          Future {
            scala.concurrent.blocking {
              Thread.sleep(10)
              i
            }
          }
      }
      val flow = FailureSkipFlow(originalFlow) { (element, _) =>
        failureElement = Option(element)
      }
      val result = Source(elements).via(flow).runWith(Sink.seq[Int])

      whenReady(result) { seq =>
        seq === Seq(1, 2, 4, 5, 6, 7, 8)
        failureElement === Option(3)
      }
    }

    "並列処理を書いても逐次処理として処理され、原因となった要素が取得できる(Future.failed)" in {
      val elements                    = 1 to 8
      var failureElement: Option[Int] = None

      val originalFlow = Flow[Int].mapAsyncUnordered(8) {
        case 3 =>
          Future {
            scala.concurrent.blocking {
              Thread.sleep(10)
              throw new RuntimeException("bang!")
            }
          }
        case i =>
          Future {
            scala.concurrent.blocking {
              Thread.sleep(10)
              i
            }
          }
      }
      val flow = FailureSkipFlow(originalFlow) { (element, _) =>
        failureElement = Option(element)
      }
      val result = Source(elements).via(flow).runWith(Sink.seq[Int])

      whenReady(result) { seq =>
        seq === Seq(1, 2, 4, 5, 6, 7, 8)
        failureElement === Option(3)
      }
    }

    "並列処理が1並列なら意図どおりの振る舞いになる" in {
      val elements = 1 to 10

      val originalFlow = Flow[Int].mapAsync(parallelism = 1) { i =>
        Future {
          scala.concurrent.blocking {
            Thread.sleep(10)
            i
          }
        }
      }
      val flow = FailureSkipFlow(originalFlow) { (_, _) =>
        // do nothing
      }
      val result = Source(elements).via(flow).runWith(Sink.seq[Int])
      whenReady(result) { seq =>
        expect {
          seq === elements
        }
      }
    }

    "最後の要素で失敗した場合も正常終了する" in {
      val elements = 1 to 4

      val originalFlow = Flow[Int].map {
        case 4 => throw new RuntimeException("bang!")
        case i => i
      }
      val flow = FailureSkipFlow(originalFlow) { (_, _) =>
        // do nothing
      }
      Source(elements)
        .via(flow)
        .runWith(TestSink.probe)
        .request(4)
        .expectNextN(1 to 3)
        .expectComplete()
    }

    "上流が失敗した場合は異常終了する" in {
      val failure      = new RuntimeException("bang!")
      val originalFlow = Flow[Int].map(identity)
      val flow = FailureSkipFlow(originalFlow) { (_, _) =>
        // do nothing
      }
      Source(1 to 4)
        .map {
          case 4 => throw failure
          case i => i
        }
        .via(flow)
        .runWith(TestSink.probe)
        .request(4)
        .expectNextN(1 to 3)
        .expectError(failure)
    }

    "下流が終了した場合は上流も終了する" in {
      val originalFlow = Flow[Int].map(identity)
      val flow = FailureSkipFlow(originalFlow) { (_, _) =>
        // do nothing
      }
      val probe =
        TestSource
          .probe[Int]
          .viaMat(flow)(Keep.left)
          .to(Sink.head)
          .run()
      probe
        .sendNext(1)
        .expectCancellation()
    }
  }

}
