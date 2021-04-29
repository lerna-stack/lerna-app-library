package lerna.testkit.akka

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import lerna.testkit.LernaTestKitBaseSpec

final class ScalaTestWithTypedActorTestKitSpec extends ScalaTestWithTypedActorTestKit() with LernaTestKitBaseSpec {
  import ScalaTestWithTypedActorTestKitSpec.Echo
  "ScalaTestWithTypedActorTestKit" should {
    "testKit is available in the test" in {
      val actor = testKit.spawn(Echo(), name = "echo")
      val probe = testKit.createTestProbe[Echo.Pong]()
      actor ! Echo.Ping("hello", probe.ref)
      probe.expectMessage(Echo.Pong("hello"))
    }
  }
}

object ScalaTestWithTypedActorTestKitSpec {
  private object Echo {
    final case class Ping(message: String, response: ActorRef[Pong])
    final case class Pong(message: String)

    def apply(): Behavior[Ping] = Behaviors.receiveMessage {
      case Ping(m, replyTo) =>
        replyTo ! Pong(m)
        Behaviors.same
    }
  }
}
