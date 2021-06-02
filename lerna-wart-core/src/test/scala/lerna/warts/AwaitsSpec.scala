package lerna.warts

import org.wartremover.test.WartTestTraverser

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class AwaitsSpec extends AnyWordSpec with Diagrams {

  "Awaits" should {

    "Await.result を検出した場合はエラー" in {
      val f = Future.successful(42)
      val result = WartTestTraverser(Awaits) {
        Await.result(f, 1.second)
      }
      assert(result.errors.contains("[wartremover:Awaits] AwaitResult#result の代わりに for 式や Future#map を使ってください"))
    }

    "Await.ready を検出した場合はエラー" in {
      val f = Future.successful(42)
      val result = WartTestTraverser(Awaits) {
        Await.ready(f, 1.second)
      }
      assert(result.errors.contains("[wartremover:Awaits] AwaitResult#ready の代わりに for 式や Future#map を使ってください"))
    }

    "disable a `Await.result` violation detection if the SuppressWarnings is used" in {
      val f = Future.successful(42)
      val result = WartTestTraverser(Awaits) {
        @SuppressWarnings(Array("lerna.warts.Awaits"))
        object App extends App {
          Await.result(f, 1.second)
        }
      }
      assert(result.errors.isEmpty)
    }

    "disable a `Await.ready` violation detection if the SuppressWarnings is used" in {
      val f = Future.successful(42)
      val result = WartTestTraverser(Awaits) {
        @SuppressWarnings(Array("lerna.warts.Awaits"))
        object App extends App {
          Await.ready(f, 1.second)
        }
      }
      assert(result.errors.isEmpty)
    }

  }
}
