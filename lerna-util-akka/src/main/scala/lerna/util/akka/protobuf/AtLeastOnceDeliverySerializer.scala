package lerna.util.akka.protobuf

import java.io.NotSerializableException

import akka.actor.ExtendedActorSystem
import akka.serialization.{
  BaseSerializer,
  Serialization,
  SerializationExtension,
  SerializerWithStringManifest,
  Serializers,
}
import com.google.protobuf.ByteString
import lerna.util.akka.{ AtLeastOnceDelivery, AtLeastOnceDeliverySerializable }

import scala.collection.mutable

@SuppressWarnings(
  Array(
    "org.wartremover.warts.TryPartial",
    "org.wartremover.warts.AsInstanceOf",
  ),
)
private[akka] final class AtLeastOnceDeliverySerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {

  import AtLeastOnceDelivery._

  private lazy val serialization = SerializationExtension(system)

  private val RequestManifest = "A"
  private val ConfirmManifest = "B"

  private val fromBinaryMap = mutable.HashMap[String, Array[Byte] => AtLeastOnceDeliverySerializable](
    RequestManifest -> requestFromBinary,
    ConfirmManifest -> confirmFromBinary,
  )

  override def manifest(o: AnyRef): String = o match {
    case message: AtLeastOnceDeliverySerializable => serializableToManifest(message)
    case _ =>
      throw new IllegalArgumentException(
        s"Can't serialize object of type ${o.getClass.toString} in [${getClass.getName}]",
      )
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case message: AtLeastOnceDeliverySerializable => serializableToBinary(message)
    case _ =>
      throw new IllegalArgumentException(
        s"Can't serialize object of type ${o.getClass.toString} in [${getClass.getName}]",
      )
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    fromBinaryMap.get(manifest) match {
      case Some(deserializeFunc) =>
        deserializeFunc(bytes)
      case None =>
        throw new NotSerializableException(
          s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]",
        )
    }
  }

  private def serializableToManifest(message: AtLeastOnceDeliverySerializable): String = message match {
    case _: AtLeastOnceDeliveryRequest => RequestManifest
    case AtLeastOnceDeliveryConfirm    => ConfirmManifest
  }

  private def serializableToBinary(message: AtLeastOnceDeliverySerializable): Array[Byte] = message match {
    case m: AtLeastOnceDeliveryRequest      => requestToBinary(m)
    case m: AtLeastOnceDeliveryConfirm.type => confirmToBinary(m)
  }

  private def requestToBinary(request: AtLeastOnceDeliveryRequest): Array[Byte] = {
    val replyActorPath = Serialization.serializedActorPath(request.self)
    val payload        = payloadToProto(request.originalMessage)
    msg.AtLeastOnceDeliveryRequest
      .of(
        replyActorPath = replyActorPath,
        payload = Option(payload),
      ).toByteArray
  }

  private def requestFromBinary(bytes: Array[Byte]): AtLeastOnceDeliveryRequest = {
    val message       = msg.AtLeastOnceDeliveryRequest.parseFrom(bytes)
    val replyActorRef = system.provider.resolveActorRef(message.replyActorPath)
    val payload = message.payload.getOrElse {
      throw new NotSerializableException(
        s"Unimplemented deserialization of the empty payload for ${classOf[AtLeastOnceDeliveryRequest].getName} in [${getClass.getName}]",
      )
    }
    val originalMessage = payloadFromProto(payload)
    AtLeastOnceDeliveryRequest(originalMessage)(replyActorRef)
  }

  private def confirmToBinary(message: AtLeastOnceDeliveryConfirm.type): Array[Byte] = {
    // We serialize nothing for now, but use protobuf for schema evolution in the future.
    msg.AtLeastOnceDeliveryConfirm.of().toByteArray
  }

  private def confirmFromBinary(bytes: Array[Byte]): AtLeastOnceDeliveryConfirm.type = {
    // We don't need anything from deserialized message, but check it for safety guard.
    val _ = msg.AtLeastOnceDeliveryConfirm.parseFrom(bytes)
    AtLeastOnceDeliveryConfirm
  }

  private def payloadToProto(message: Any): msg.Payload = {
    val messageRef      = message.asInstanceOf[AnyRef]
    val serializer      = serialization.findSerializerFor(messageRef)
    val enclosedMessage = ByteString.copyFrom(serializer.toBinary(messageRef))
    val serializerId    = serializer.identifier
    val manifest        = ByteString.copyFromUtf8(Serializers.manifestFor(serializer, messageRef))
    msg.Payload.of(
      enclosedMessage = enclosedMessage,
      serializerId = serializerId,
      messageManifest = manifest,
    )
  }

  private def payloadFromProto(payload: msg.Payload): AnyRef = {
    val manifest        = payload.messageManifest.toStringUtf8
    val enclosedMessage = payload.enclosedMessage.toByteArray
    val serializerId    = payload.serializerId
    serialization.deserialize(enclosedMessage, serializerId, manifest).get
  }

}
