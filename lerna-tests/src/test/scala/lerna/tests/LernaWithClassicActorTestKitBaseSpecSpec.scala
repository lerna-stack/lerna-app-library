package lerna.tests

import akka.actor.ActorSystem
import akka.testkit.TestActors

final class LernaWithClassicActorTestKitBaseSpecSpec
    extends LernaWithClassicActorTestKitBaseSpec(ActorSystem("lerna-with-classic-actor-testkit-base-spec-spec")) {
  "LernaWithClassicActorTestKitBaseSpec" should {
    "ActorSystem is available in the test" in {
      val actor = system.actorOf(TestActors.echoActorProps)
      actor ! "hello"
      expectMsg("hello")
    }
  }
}
