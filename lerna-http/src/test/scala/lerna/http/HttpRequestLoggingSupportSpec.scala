package lerna.http

import akka.actor.{ typed, ActorSystem }
import lerna.log.AppLogging
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec

private object HttpRequestLoggingSupportSpec {
  class HttpRequestLoggingSupportForClassic(
      val system: ActorSystem,
  ) extends HttpRequestLoggingSupport
      with AppLogging {
    override val scope: String = "dummy"
  }

  class HttpRequestLoggingSupportForTyped(
      val system: typed.ActorSystem[_],
  ) extends HttpRequestLoggingSupport
      with AppLogging {
    override val scope: String = "dummy"
  }
}

class HttpRequestLoggingSupportSpec extends ScalaTestWithTypedActorTestKit() with LernaBaseSpec {
  import HttpRequestLoggingSupportSpec._

  "HttpRequestLoggingSupport" should {
    "Classic ActorSystem を使ってインスタンス化できる" in {
      val classicSystem: ActorSystem = system.classicSystem
      noException should be thrownBy new HttpRequestLoggingSupportForClassic(classicSystem)
    }

    "Typed ActorSystem を使ってインスタンス化できる" in {
      val typedSystem: typed.ActorSystem[_] = system
      noException should be thrownBy new HttpRequestLoggingSupportForTyped(typedSystem)
    }
  }
}
