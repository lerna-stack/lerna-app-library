package lerna.util.akka.protobuf

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import akka.testkit.TestActors
import lerna.util.akka.{ AtLeastOnceDelivery, AtLeastOnceDeliverySerializable, LernaAkkaActorBaseSpec }

final class AtLeastOnceDeliverySerializerBindingSpec
    extends LernaAkkaActorBaseSpec(ActorSystem("at-least-once-delivery-serializer-binding-spec")) {

  import AtLeastOnceDelivery._

  private val serialization = SerializationExtension(system)

  private def checkSerializer(message: AtLeastOnceDeliverySerializable): Unit = {
    val serializer = serialization.findSerializerFor(message)
    serializer shouldBe a[AtLeastOnceDeliverySerializer]
  }

  "AtLeastOnceDeliverySerializer" should {
    "be bound to AtLeastDeliverySerializable" in {
      val ref = system.actorOf(TestActors.blackholeProps)
      checkSerializer(AtLeastOnceDeliveryRequest(123)(ref))
      checkSerializer(AtLeastOnceDeliveryRequest("abc")(ref))
      checkSerializer(AtLeastOnceDeliveryConfirm)
    }
  }

}
