package lerna.util.akka

import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ typed, Actor, ActorRef, ActorSystem, Cancellable, NoSerializationVerificationNeeded, Props }
import akka.util.Timeout
import lerna.log.AppActorLogging
import lerna.util.time.JavaDurationConverters._
import lerna.util.trace.RequestContext

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
    * @note Use [[https://doc.akka.io/docs/akka/current/typed/reliable-delivery.html Akka Reliable Delivery]] if you use Akka typed.
    */
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
  def tellTo(
      destination: ActorRef,
      message: Any,
  )(implicit requestContext: RequestContext, system: ActorSystem, sender: ActorRef = Actor.noSender): Unit = {
    val atLeastOnceDeliveryProxy = system.actorOf(AtLeastOnceDelivery.props(destination))
    atLeastOnceDeliveryProxy ! AtLeastOnceDeliveryRequest(message)(atLeastOnceDeliveryProxy)
  }

  /** Send the message asynchronously and return a [[scala.concurrent.Future]] holding the reply message.
    * If no message is received within `timeout`, the [[scala.concurrent.Future]] holding an [[akka.pattern.AskTimeoutException]] is returned.
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
      message: (typed.ActorRef[Reply], typed.ActorRef[AtLeastOnceDeliveryConfirm.type]) => Command,
  )(implicit requestContext: RequestContext, system: typed.ActorSystem[_], timeout: Timeout): Future[Reply] = {
    import akka.actor.typed.scaladsl.AskPattern._
    val atLeastOnceDeliveryProxy = system.toClassic.actorOf(AtLeastOnceDelivery.props(destination.toClassic)).toTyped
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
      message: typed.ActorRef[AtLeastOnceDeliveryConfirm.type] => Command,
  )(implicit requestContext: RequestContext, system: typed.ActorSystem[_]): Unit = {
    val atLeastOnceDeliveryProxy = system.toClassic.actorOf(AtLeastOnceDelivery.props(destination.toClassic))
    atLeastOnceDeliveryProxy ! message(atLeastOnceDeliveryProxy)
  }

  // Actor's protocol
  private[akka] sealed trait AtLeastOnceDeliveryCommand

  case object AtLeastOnceDeliveryConfirm extends AtLeastOnceDeliveryCommand with AtLeastOnceDeliverySerializable

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
    def accept(): Unit = self ! AtLeastOnceDeliveryConfirm
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

    case AtLeastOnceDeliveryConfirm =>
      context.stop(self)
      retryScheduler.cancel()

    case RetryTimeout =>
      context.stop(self)
      retryScheduler.cancel()
      logger.info(s"到達確認ができず、${retryTimeout.toString} 経過したため再送を中止します")
  }
}
