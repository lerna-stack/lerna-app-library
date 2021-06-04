package lerna.util.akka.protobuf

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.testkit.TestActors
import lerna.util.akka.{ AtLeastOnceDelivery, AtLeastOnceDeliverySerializable, LernaAkkaActorBaseSpec }

import java.io.NotSerializableException

final class AtLeastOnceDeliverySerializerSpec()
    extends LernaAkkaActorBaseSpec(ActorSystem("at-least-once-delivery-serializer-spec")) {

  import AtLeastOnceDelivery._

  private val serializer = new AtLeastOnceDeliverySerializer(system.asInstanceOf[ExtendedActorSystem])

  private def checkSerialization(message: AtLeastOnceDeliverySerializable): Unit = {
    val blob = serializer.toBinary(message)
    val ref  = serializer.fromBinary(blob, serializer.manifest(message))
    expect(ref === message)
  }

  "AtLeastOnceDeliverySerializable" should {
    "be serializable" in {
      val ref = system.actorOf(TestActors.blackholeProps)
      checkSerialization(AtLeastOnceDeliveryRequest(123)(ref))
      checkSerialization(AtLeastOnceDeliveryRequest("abcdef")(ref))
      checkSerialization(Confirm)
    }

  }

  "AtLeastOnceDeliverySerializer.manifest" should {
    "throw an IllegalArgumentException if the given object is not an instance of AtLeastOnceDeliverySerializable" in {
      case class InvalidObject()
      a[IllegalArgumentException] shouldBe thrownBy {
        serializer.manifest(InvalidObject())
      }
    }
  }

  "AtLeastOnceDeliverySerializer.toBinary" should {
    "throw an IllegalArgumentException if the given object is not an instance of AtLeastOnceDeliverySerializable" in {
      case class InvalidObject()
      a[IllegalArgumentException] shouldBe thrownBy {
        serializer.toBinary(InvalidObject())
      }
    }
  }

  "AtLeastOnceDeliverySerializer.fromBinary" should {
    "throw a NotSerializableException if the given manifest is invalid" in {
      val validBinary     = serializer.toBinary(Confirm)
      val invalidManifest = "INVALID_MANIFEST"
      a[NotSerializableException] shouldBe thrownBy {
        serializer.fromBinary(validBinary, invalidManifest)
      }
    }
  }

}
