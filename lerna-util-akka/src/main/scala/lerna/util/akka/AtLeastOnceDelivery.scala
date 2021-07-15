package lerna.util.akka

import akka.actor.typed.scaladsl.adapter._
import akka.actor.{
  typed,
  Actor,
  ActorRef,
  ActorSystem,
  Cancellable,
  ExtendedActorSystem,
  Extension,
  ExtensionId,
  NoSerializationVerificationNeeded,
  Props,
}
import akka.util.Timeout
import lerna.log.AppActorLogging
import lerna.util.time.JavaDurationConverters._
import lerna.util.trace.RequestContext

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

// Marker trait for serialize/deserialize
private[akka] sealed trait AtLeastOnceDeliverySerializable

/** An object that provides reliable delivery features
  */
object AtLeastOnceDelivery {

  /** Send the message asynchronously and return a [[scala.concurrent.Future]] holding the reply message.
    * If no message is received within `timeout`, the [[scala.concurrent.Future]] holding an [[akka.pattern.AskTimeoutException]] is returned.
    * This behavior is the same as Akka's ask pattern, but this method has some different behavior like below.
    *
    * The sender waits for a reply message of the sent message until a specific timeout (called `redeliver-interval`) is reached.
    * If the sender receives no reply, the sender retries to send the same message again.
    * This retransmission continues until another specific timeout (called `retry-timeout`) is reached.
    * The above timeouts can be configured in your configuration file such as `reference.conf`.
    *
    * @param destination The destination actor to send
    * @param message The message to send
    * @param requestContext The context to be used for logging
    * @param system The [[akka.actor.ActorSystem]] to be used
    * @param timeout The entire timeout
    * @return The [[scala.concurrent.Future]] holding the reply message or an exception
    */
  @deprecated(message = "Use typed Actor", since = "2.0.0")
  def askTo(
      destination: ActorRef,
      message: Any,
  )(implicit requestContext: RequestContext, system: ActorSystem, timeout: Timeout): Future[Any] = {
    import akka.pattern.ask
    val atLeastOnceDeliveryProxy = system.actorOf(AtLeastOnceDelivery.props(destination))
    atLeastOnceDeliveryProxy ? AtLeastOnceDeliveryRequest(message)(atLeastOnceDeliveryProxy)
  }

  /** Send the message and return nothing.
    * This method has some different behavior from Akka's tell pattern, like below.
    *
    * In this method, the sender actor is created, and it waits for a reply message.
    * If the sender receives no reply, the sender retries tto send the same message gain.
    * This retransmission continues until a specific timeout (called `retry-timeout`) is reached.
    * The timeouts related to this method can be configured in your configuration file such as `reference.conf`.
    *
    * ==CAUTIONS==
    * If you use this method, you should be careful about the below.
    * We cannot know whether the receiver actually got the message.
    * Moreover, we have no chance to know whether the sender continues sending the message or not.
    *
    * @param destination The destination actor to send
    * @param message The message to send
    * @param requestContext The context to be used for logging
    * @param system The [[akka.actor.ActorSystem]] to be used
    * @param sender The sender actor. If you omit this parameter [[akka.actor.Actor.noSender]] is used.
    */
  @deprecated(message = "Use typed Actor", since = "2.0.0")
  def tellTo(
      destination: ActorRef,
      message: Any,
  )(implicit requestContext: RequestContext, system: ActorSystem, sender: ActorRef = Actor.noSender): Unit = {
    val atLeastOnceDeliveryProxy = system.actorOf(AtLeastOnceDelivery.props(destination))
    atLeastOnceDeliveryProxy ! AtLeastOnceDeliveryRequest(message)(atLeastOnceDeliveryProxy)
  }

  /** Send the message asynchronously and return a [[scala.concurrent.Future]] holding the reply message.
    * If no message is received within `timeout`, the [[scala.concurrent.Future]] holding an [[java.util.concurrent.TimeoutException]] is returned.
    * This behavior is the same as Akka's ask pattern, but this method has some different behavior like below.
    *
    * The sender waits for a reply message of the sent message until a specific timeout (called `redeliver-interval`) is reached.
    * If the sender receives no reply, the sender retries to send the same message again.
    * This retransmission continues until another specific timeout (called `retry-timeout`) is reached.
    * The above timeouts can be configured in your configuration file such as `reference.conf`.
    * @param destination The destination typed actor to send
    * @param message The message factory
    * @param requestContext The context to be used for logging
    * @param system The [[akka.actor.typed.ActorSystem]] to be used
    * @param timeout The entire timeout
    * @tparam Command Type of message to send
    * @tparam Reply The type of reply message
    * @return The [[scala.concurrent.Future]] holding the reply message or an exception
    */
  def askTo[Command, Reply](
      destination: typed.ActorRef[Command],
      message: (typed.ActorRef[Reply], typed.ActorRef[Confirm]) => Command,
  )(implicit requestContext: RequestContext, system: typed.ActorSystem[_], timeout: Timeout): Future[Reply] = {
    import akka.actor.typed.scaladsl.AskPattern._
    val atLeastOnceDeliveryProxy = AtLeastOnceDeliveryExtensionForTyped(system).createActor(destination).toTyped
    atLeastOnceDeliveryProxy.ask[Reply](replyTo => message(replyTo, atLeastOnceDeliveryProxy))
  }

  /** Send the message and return nothing.
    * This method has some different behavior from Akka's tell pattern, like below.
    *
    * In this method, the sender actor is created, and it waits for a reply message.
    * If the sender receives no reply, the sender retries tto send the same message gain.
    * This retransmission continues until a specific timeout (called `retry-timeout`) is reached.
    * The timeouts related to this method can be configured in your configuration file such as `reference.conf`.
    *
    * ==CAUTIONS==
    * If you use this method, you should be careful about the below.
    * We cannot know whether the receiver actually got the message.
    * Moreover, we have no chance to know whether the sender continues sending the message or not.
    * @param destination The destination typed actor to send
    * @param message The message factory
    * @param requestContext The context to be used for logging
    * @param system The [[akka.actor.typed.ActorSystem]] to be used
    * @tparam Command Type of message to send
    */
  def tellTo[Command](
      destination: typed.ActorRef[Command],
      message: typed.ActorRef[Confirm] => Command,
  )(implicit requestContext: RequestContext, system: typed.ActorSystem[_]): Unit = {
    val atLeastOnceDeliveryProxy = AtLeastOnceDeliveryExtensionForTyped(system).createActor(destination)
    atLeastOnceDeliveryProxy ! message(atLeastOnceDeliveryProxy)
  }

  // Actor's protocol
  private[akka] sealed trait AtLeastOnceDeliveryCommand

  sealed trait Confirm extends AtLeastOnceDeliveryCommand
  case object Confirm  extends Confirm with AtLeastOnceDeliverySerializable

  /** A message that holds the original message and the destination actor to send a confirmation
    *
    * @param originalMessage The original message
    * @param self The destination actor to send a confirmation
    */
  final case class AtLeastOnceDeliveryRequest(originalMessage: Any)(implicit private[akka] val self: ActorRef)
      extends AtLeastOnceDeliverySerializable {

    /** Send a confirmation to the sender
      *
      * If the confirmation is not sent, the sender retries to send the same message.
      */
    def accept(): Unit = self ! Confirm
  }

  // Marker trait for actor's private commands that need no serialization
  private sealed trait AtLeastOnceDeliveryPrivateCommand
      extends AtLeastOnceDeliveryCommand
      with NoSerializationVerificationNeeded
  private case object SendRequest      extends AtLeastOnceDeliveryPrivateCommand
  private case object RetrySendRequest extends AtLeastOnceDeliveryPrivateCommand
  private case object RetryTimeout     extends AtLeastOnceDeliveryPrivateCommand

  private def props(destination: ActorRef)(implicit requestContext: RequestContext) =
    Props(new AtLeastOnceDelivery(destination))

  private object AtLeastOnceDeliveryExtensionForTyped extends ExtensionId[AtLeastOnceDeliveryExtensionForTyped] {
    override def createExtension(system: ExtendedActorSystem): AtLeastOnceDeliveryExtensionForTyped =
      new AtLeastOnceDeliveryExtensionForTyped(system)
  }

  private class AtLeastOnceDeliveryExtensionForTyped(system: ExtendedActorSystem) extends Extension {
    private val counter = new AtomicInteger()
    private def name()  = s"${this.getClass.getName}_${counter.getAndIncrement().toString}"

    /** system Actor として AtLeastOnceDelivery Actor を作成する
      *
      * `new typed.ActorSystem()` で作成された system を使うと `system.toClassic.actorOf` で Actor を作れない問題対策。
      * system.toClassic.actorOf が使えるのは元々が classic ActorSystem の場合のみ。
      * Extension 化し ExtendedActorSystem を取得することで、 Props から system Actor を作成する
      */
    def createActor[Command](destination: typed.ActorRef[Command])(implicit requestContext: RequestContext): ActorRef =
      system.systemActorOf(AtLeastOnceDelivery.props(destination.toClassic), name())
  }
}

/** 到達保証用のActor<br>
  * 1 リクエスト -> 1 Actor<br>
  * ※ 再度Actorが作成されるケースは考えていない<br>
  */
private[akka] final class AtLeastOnceDelivery(destination: ActorRef)(implicit requestContext: RequestContext)
    extends Actor
    with AppActorLogging {
  import AtLeastOnceDelivery._

  private val config =
    context.system.settings.config.getConfig("lerna.util.akka.at-least-once-delivery")

  private val redeliverInterval: FiniteDuration = config.getDuration("redeliver-interval").asScala
  private val retryTimeout: FiniteDuration      = config.getDuration("retry-timeout").asScala

  import context.dispatcher
  context.system.scheduler.scheduleOnce(delay = retryTimeout, receiver = self, message = RetryTimeout)

  import context.dispatcher

  override def receive: Receive = {
    case RetryTimeout =>
      context.stop(self)

    case message =>
      val replyTo = ReplyTo(sender())
      val retryScheduler =
        context.system.scheduler.scheduleWithFixedDelay(
          initialDelay = redeliverInterval,
          redeliverInterval,
          self,
          RetrySendRequest,
        )
      context.become(accepted(replyTo, message, retryScheduler))
      self ! SendRequest
  }

  private def accepted(replyTo: ReplyTo, message: Any, retryScheduler: Cancellable): Receive = {
    case RetrySendRequest =>
      logger.info(s"再送します: destination = ${destination.toString}")
      self ! SendRequest

    case SendRequest =>
      destination.tell(message, replyTo.actorRef)

    case Confirm =>
      context.stop(self)
      retryScheduler.cancel()

    case RetryTimeout =>
      context.stop(self)
      retryScheduler.cancel()
      logger.info(s"到達確認ができず、${retryTimeout.toString} 経過したため再送を中止します")
  }
}
