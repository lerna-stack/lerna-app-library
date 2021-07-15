package lerna.testkit.akka

import akka.actor.ActorSystem
import akka.testkit.TestActors
import lerna.testkit.LernaTestKitBaseSpec

import scala.annotation.nowarn

@nowarn("msg=Use ScalaTestWithTypedActorTestKit")
final class ScalaTestWithClassicActorTestKitSpec
    extends ScalaTestWithClassicActorTestKit(ActorSystem("scala-test-with-classic-actor-test-kit-spec"))
    with LernaTestKitBaseSpec {
  "ScalaTestWithClassicActorTestKit" should {
    "ActorSystem is available in the test" in {
      val actor = system.actorOf(TestActors.echoActorProps)
      actor ! "hello"
      expectMsg("hello")
    }
  }
}
