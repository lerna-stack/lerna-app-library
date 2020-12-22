package lerna.util.sequence

import akka.actor.{ Actor, ActorRef, PoisonPill, Props, ReceiveTimeout, Stash }
import lerna.log.AppActorLogging
import lerna.util.lang.Equals._
import lerna.util.tenant.Tenant

import scala.concurrent.duration.FiniteDuration

private[sequence] object SequenceFactoryWorker {

  def props(
      maxSequenceValue: BigInt,
      firstValue: BigInt,
      incrementStep: Int,
      reservationAmount: Int,
      sequenceStore: ActorRef,
      idleTimeout: FiniteDuration,
      sequenceSubId: Option[String],
  )(implicit tenant: Tenant): Props =
    Props(
      new SequenceFactoryWorker(
        maxSequenceValue = maxSequenceValue,
        firstValue = firstValue,
        incrementStep = incrementStep,
        reservationAmount = reservationAmount,
        sequenceStore = sequenceStore,
        idleTimeout = idleTimeout,
        sequenceSubId = sequenceSubId,
      ),
    )

  sealed trait Command
  final case object Initialize                                            extends Command
  final case class GenerateSequence(sequenceSubId: Option[String] = None) extends Command

  sealed trait DomainEvent
  final case class SequenceGenerated(value: BigInt, sequenceSubId: Option[String]) extends DomainEvent

  final case class SequenceContext(maxReservedValue: BigInt, nextValue: BigInt)
}

private[sequence] final class SequenceFactoryWorker(
    maxSequenceValue: BigInt,
    firstValue: BigInt,
    incrementStep: Int,
    reservationAmount: Int,
    sequenceStore: ActorRef,
    idleTimeout: FiniteDuration,
    sequenceSubId: Option[String],
)(implicit tenant: Tenant)
    extends Actor
    with Stash
    with AppActorLogging {
  import SequenceFactoryWorker._

  require(maxSequenceValue > 0 && reservationAmount > 0)

  context.setReceiveTimeout(idleTimeout)

  import lerna.util.tenant.TenantComponentLogContext.logContext

  // 残りが予約したシーケンスの 1/2 以下になったら予約を開始
  private[this] val reservationFactor = 2

  override def receive: Receive = notReady

  override def preStart(): Unit = {
    super.preStart()
    self ! Initialize
  }

  private[this] def notReady: Receive = {
    case Initialize =>
      sequenceStore ! SequenceStore.InitialReserveSequence(firstValue, reservationAmount, sequenceSubId)

    case msg: SequenceStore.InitialSequenceReserved if msg.initialValue > maxSequenceValue =>
      sequenceStore ! SequenceStore.ResetReserveSequence(firstValue, reservationAmount, sequenceSubId)
      context.become(resetting)
    case msg: SequenceStore.InitialSequenceReserved =>
      unstashAll()
      logger.info("initial reserved: max:{}, initial:{}", msg.maxReservedValue, msg.initialValue)
      context.become(ready(SequenceContext(msg.maxReservedValue, nextValue = msg.initialValue)))
    case _: GenerateSequence => stash()
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private[this] def ready(implicit sequenceContext: SequenceContext): Receive = {
    case msg: GenerateSequence if msg.sequenceSubId === sequenceSubId =>
      import sequenceContext._

      if (nextValue <= maxSequenceValue) {
        sender() ! SequenceGenerated(nextValue, sequenceSubId)
        logger.debug("SequenceGenerated when ready: {}", nextValue)
      } else {
        stash()
      }

      val newNextValue = nextValue + incrementStep
      val remainAmount = // 残数
        if (maxReservedValue > newNextValue) {
          (maxReservedValue - newNextValue) / incrementStep
        } else BigInt(0)

      if (newNextValue > maxSequenceValue) {
        sequenceStore ! SequenceStore.ResetReserveSequence(firstValue, reservationAmount, sequenceSubId)
        context.become(resetting)
      } else if (remainAmount <= (reservationAmount / reservationFactor)) {
        val amount = (reservationAmount - remainAmount).toInt
        logger.info(
          "Reserving sequence: remain {}, add {}, current max reserved: {}",
          remainAmount,
          amount,
          maxReservedValue,
        )
        sequenceStore ! SequenceStore.ReserveSequence(maxReservedValue, amount, sequenceSubId)
        context.become(reserving(sequenceContext.copy(nextValue = newNextValue)))
      } else {
        context.become(ready(sequenceContext.copy(nextValue = newNextValue)))
      }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private[this] def reserving(implicit sequenceContext: SequenceContext): Receive = {
    case msg: GenerateSequence if msg.sequenceSubId === sequenceSubId =>
      import sequenceContext._

      if (nextValue > maxSequenceValue) {
        stash()
        sequenceStore ! SequenceStore.ResetReserveSequence(firstValue, reservationAmount, sequenceSubId)
        context.become(resetting)
      } else if (nextValue > maxReservedValue) {
        logger.warn("Pending generate sequence because reserving sequence: {}", nextValue)
        stash()
      } else {
        sender() ! SequenceGenerated(nextValue, sequenceSubId)
        logger.debug("SequenceGenerated when reserving: {}", nextValue)
        context.become(reserving(sequenceContext.copy(nextValue = nextValue + incrementStep)))
      }
    case msg: SequenceStore.SequenceReserved =>
      unstashAll()
      context.become(ready(sequenceContext.copy(maxReservedValue = msg.maxReservedValue)))
    case SequenceStore.ReservationFailed =>
      unstashAll()
      // 即座にリトライしたところで予約できる見込みは薄いケースがあるので、
      // クライアントから再び採番を要求されたときにリトライする方針とする
      context.become(ready)
  }

  private[this] def resetting: Receive = {
    case msg: SequenceStore.SequenceReset =>
      unstashAll()
      context.become(ready(SequenceContext(msg.maxReservedValue, nextValue = firstValue)))
      logger.warn("reset sequence: {}", msg.maxReservedValue)
    case _: GenerateSequence => stash()
  }

  override def unhandled(message: Any): Unit = message match {
    case ReceiveTimeout =>
      self ! PoisonPill
    case other =>
      super.unhandled(other)
  }
}
