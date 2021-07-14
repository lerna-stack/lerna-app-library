package lerna.util.sequence

import akka.actor.typed
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
import lerna.log.{ AppLogger, AppTypedActorLogging }
import lerna.util.lang.Equals._
import lerna.util.tenant.Tenant

import scala.concurrent.duration.FiniteDuration

private[sequence] object SequenceFactoryWorker extends AppTypedActorLogging {

  def apply(
      maxSequenceValue: BigInt,
      firstValue: BigInt,
      incrementStep: Int,
      reservationAmount: Int,
      sequenceStore: ActorRef[SequenceStore.Command],
      idleTimeout: FiniteDuration,
      sequenceSubId: Option[String],
  )(implicit tenant: Tenant): Behavior[Command] = Behaviors
    .supervise[Command] {
      Behaviors.setup { context =>
        val capacity = context.system.settings.config.getInt("lerna.util.sequence.worker.stash-capacity")
        Behaviors.withStash(capacity) { buffer =>
          withLogger { logger =>
            new SequenceFactoryWorker(
              maxSequenceValue = maxSequenceValue,
              firstValue = firstValue,
              incrementStep = incrementStep,
              reservationAmount = reservationAmount,
              sequenceStore = sequenceStore,
              idleTimeout = idleTimeout,
              sequenceSubId = sequenceSubId,
              context,
              buffer,
              logger,
            ).createBehavior()
          }
        }
      }
    }.onFailure[Exception](SupervisorStrategy.restart)

  sealed trait Command
  final case object Initialize extends Command
  final case class GenerateSequence(sequenceSubId: Option[String] = None, replyTo: typed.ActorRef[SequenceGenerated])
      extends Command

  private final case class WrappedSequenceStoreResponse(response: SequenceStore.ReservationResponse) extends Command
  private case object ReceiveTimeout                                                                 extends Command

  sealed trait DomainEvent
  final case class SequenceGenerated(value: BigInt, sequenceSubId: Option[String]) extends DomainEvent

  final case class SequenceContext(maxReservedValue: BigInt, nextValue: BigInt)
}

private[sequence] final class SequenceFactoryWorker(
    maxSequenceValue: BigInt,
    firstValue: BigInt,
    incrementStep: Int,
    reservationAmount: Int,
    sequenceStore: ActorRef[SequenceStore.Command],
    idleTimeout: FiniteDuration,
    sequenceSubId: Option[String],
    context: ActorContext[SequenceFactoryWorker.Command],
    stashBuffer: StashBuffer[SequenceFactoryWorker.Command],
    logger: AppLogger,
)(implicit tenant: Tenant) {
  import SequenceFactoryWorker._

  require(maxSequenceValue > 0 && reservationAmount > 0)

  context.setReceiveTimeout(idleTimeout, ReceiveTimeout)

  import lerna.util.tenant.TenantComponentLogContext.logContext

  // 残りが予約したシーケンスの 1/2 以下になったら予約を開始
  private[this] val reservationFactor = 2

  def createBehavior(): Behavior[Command] = {
    context.self ! Initialize
    notReady
  }

  private val responseMapper: ActorRef[SequenceStore.ReservationResponse] =
    context.messageAdapter(response => WrappedSequenceStoreResponse(response))

  private[this] def notReady = Behaviors.receiveMessage[Command] {
    case Initialize =>
      sequenceStore ! SequenceStore.InitialReserveSequence(firstValue, reservationAmount, sequenceSubId, responseMapper)
      Behaviors.same
    case WrappedSequenceStoreResponse(msg: SequenceStore.InitialSequenceReserved) =>
      if (msg.initialValue > maxSequenceValue) {
        sequenceStore ! SequenceStore.ResetReserveSequence(firstValue, reservationAmount, sequenceSubId, responseMapper)
        resetting
      } else {
        logger.info("initial reserved: max:{}, initial:{}", msg.maxReservedValue, msg.initialValue)
        stashBuffer.unstashAll(ready(SequenceContext(msg.maxReservedValue, nextValue = msg.initialValue)))
      }
    case message: GenerateSequence =>
      stashBuffer.stash(message)
      Behaviors.same
    case ReceiveTimeout                                                  => Behaviors.stopped
    case WrappedSequenceStoreResponse(_: SequenceStore.SequenceReserved) => Behaviors.unhandled
    case WrappedSequenceStoreResponse(_: SequenceStore.SequenceReset)    => Behaviors.unhandled
    case WrappedSequenceStoreResponse(SequenceStore.ReservationFailed)   => Behaviors.unhandled // FIXME
  }

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity", "org.wartremover.warts.Recursion"))
  private[this] def ready(implicit sequenceContext: SequenceContext): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
      case msg: GenerateSequence =>
        if (msg.sequenceSubId === sequenceSubId) {
          import sequenceContext._

          if (nextValue <= maxSequenceValue && nextValue <= maxReservedValue) {
            msg.replyTo ! SequenceGenerated(nextValue, sequenceSubId)
            logger.debug("SequenceGenerated when ready: {}", nextValue)
          } else {
            stashBuffer.stash(msg)
          }

          val newNextValue = nextValue + incrementStep
          val remainAmount = // 残数
            if (maxReservedValue > newNextValue) {
              (maxReservedValue - newNextValue) / incrementStep
            } else BigInt(0)

          if (newNextValue > maxSequenceValue) {
            sequenceStore ! SequenceStore.ResetReserveSequence(
              firstValue,
              reservationAmount,
              sequenceSubId,
              responseMapper,
            )
            resetting
          } else if (remainAmount <= (reservationAmount / reservationFactor)) {
            val amount = (reservationAmount - remainAmount).toInt
            logger.info(
              "Reserving sequence: remain {}, add {}, current max reserved: {}",
              remainAmount,
              amount,
              maxReservedValue,
            )
            sequenceStore ! SequenceStore.ReserveSequence(maxReservedValue, amount, sequenceSubId, responseMapper)
            reserving(sequenceContext.copy(nextValue = newNextValue))
          } else {
            ready(sequenceContext.copy(nextValue = newNextValue))
          }
        } else {
          Behaviors.unhandled
        }
      case ReceiveTimeout                  => Behaviors.stopped
      case Initialize                      => Behaviors.unhandled
      case _: WrappedSequenceStoreResponse => Behaviors.unhandled
    }

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity", "org.wartremover.warts.Recursion"))
  private[this] def reserving(implicit sequenceContext: SequenceContext): Behavior[Command] =
    Behaviors.receiveMessage {
      case msg: GenerateSequence =>
        if (msg.sequenceSubId === sequenceSubId) {
          import sequenceContext._

          if (nextValue > maxSequenceValue) {
            stashBuffer.stash(msg)
            sequenceStore ! SequenceStore.ResetReserveSequence(
              firstValue,
              reservationAmount,
              sequenceSubId,
              responseMapper,
            )
            resetting
          } else if (nextValue > maxReservedValue) {
            logger.warn("Pending generate sequence because reserving sequence: {}", nextValue)
            stashBuffer.stash(msg)
            Behaviors.same
          } else {
            msg.replyTo ! SequenceGenerated(nextValue, sequenceSubId)
            logger.debug("SequenceGenerated when reserving: {}", nextValue)
            reserving(sequenceContext.copy(nextValue = nextValue + incrementStep))
          }
        } else {
          Behaviors.unhandled
        }
      case WrappedSequenceStoreResponse(msg: SequenceStore.SequenceReserved) =>
        stashBuffer.unstashAll(ready(sequenceContext.copy(maxReservedValue = msg.maxReservedValue)))
      case WrappedSequenceStoreResponse(SequenceStore.ReservationFailed) =>
        // 即座にリトライしたところで予約できる見込みは薄いケースがあるので、
        // クライアントから再び採番を要求されたときにリトライする方針とする
        stashBuffer.unstashAll(ready)
      case ReceiveTimeout                                                         => Behaviors.stopped
      case Initialize                                                             => Behaviors.unhandled
      case WrappedSequenceStoreResponse(_: SequenceStore.InitialSequenceReserved) => Behaviors.unhandled
      case WrappedSequenceStoreResponse(_: SequenceStore.SequenceReset)           => Behaviors.unhandled
    }

  private[this] def resetting: Behavior[Command] = Behaviors.receiveMessage[Command] {
    case WrappedSequenceStoreResponse(msg: SequenceStore.SequenceReset) =>
      logger.warn("reset sequence: {}", msg.maxReservedValue)
      stashBuffer.unstashAll(ready(SequenceContext(msg.maxReservedValue, nextValue = firstValue)))
    case message: GenerateSequence =>
      stashBuffer.stash(message)
      Behaviors.same
    case ReceiveTimeout                                                         => Behaviors.stopped
    case Initialize                                                             => Behaviors.unhandled
    case WrappedSequenceStoreResponse(_: SequenceStore.InitialSequenceReserved) => Behaviors.unhandled
    case WrappedSequenceStoreResponse(_: SequenceStore.SequenceReserved)        => Behaviors.unhandled
    case WrappedSequenceStoreResponse(SequenceStore.ReservationFailed)          => Behaviors.unhandled // FIXME
  }
}
