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

  final case class SequenceConfig(
      maxSequenceValue: BigInt,
      incrementStep: Int,
      reservationAmount: Int,
      reservationFactor: Int,
  )

  final case class SequenceContext(maxReservedValue: BigInt, nextValue: BigInt) {

    /** 採番値を次に進める */
    def next()(implicit config: SequenceConfig): SequenceContext =
      copy(nextValue = nextValue + config.incrementStep)

    /** 採番可能なシーケンスの残数 */
    def remainAmount(implicit config: SequenceConfig): BigInt =
      if (maxReservedValue > nextValue) {
        (maxReservedValue - nextValue) / config.incrementStep
      } else BigInt(0)

    /** 追加で予約可能なシーケンスの数 */
    def freeAmount(implicit config: SequenceConfig): Int =
      (config.reservationAmount - remainAmount).toInt

    /** 発行できるシーケンスの最大値を超えている */
    def isOverflow(implicit config: SequenceConfig): Boolean =
      nextValue > config.maxSequenceValue

    /** 発行できるシーケンスが少なくなっている */
    def isStarving(implicit config: SequenceConfig): Boolean =
      remainAmount <= (config.reservationAmount / config.reservationFactor)

    /** 発行できるシーケンスがない */
    def isEmpty: Boolean =
      nextValue > maxReservedValue
  }
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
    notReady(initializeTried = false)
  }

  private val responseMapper: ActorRef[SequenceStore.ReservationResponse] =
    context.messageAdapter(response => WrappedSequenceStoreResponse(response))

  private[this] implicit val config: SequenceConfig = SequenceConfig(
    maxSequenceValue = maxSequenceValue,
    incrementStep = incrementStep,
    reservationAmount = reservationAmount,
    reservationFactor = reservationFactor,
  )

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private[this] def notReady(initializeTried: Boolean): Behavior[Command] = Behaviors.receiveMessage[Command] {
    case Initialize =>
      sequenceStore ! SequenceStore.InitialReserveSequence(firstValue, reservationAmount, sequenceSubId, responseMapper)
      Behaviors.same
    case WrappedSequenceStoreResponse(msg: SequenceStore.InitialSequenceReserved) =>
      logger.info("initial reserved: max:{}, initial:{}", msg.maxReservedValue, msg.initialValue)
      val sequenceContext: SequenceContext =
        SequenceContext(msg.maxReservedValue, nextValue = msg.initialValue)
      stashBuffer.unstashAll(prepareNextSequence(sequenceContext = sequenceContext, nextSequence = sequenceContext))
    case WrappedSequenceStoreResponse(SequenceStore.ReservationFailed) =>
      notReady(initializeTried = true)
    case message: GenerateSequence =>
      if (initializeTried) {
        context.self ! Initialize
      }
      stashBuffer.stash(message)
      Behaviors.same
    case ReceiveTimeout                                                  => Behaviors.stopped
    case WrappedSequenceStoreResponse(_: SequenceStore.SequenceReserved) => Behaviors.unhandled
    case WrappedSequenceStoreResponse(_: SequenceStore.SequenceReset)    => Behaviors.unhandled
  }

  private[this] def ready(sequenceContext: SequenceContext): Behavior[Command] = Behaviors.receiveMessage {
    case msg: GenerateSequence =>
      if (msg.sequenceSubId === sequenceSubId) {
        acceptGenerateSequence(msg, sequenceContext)
      } else Behaviors.unhandled
    case WrappedSequenceStoreResponse(msg: SequenceStore.SequenceReserved) =>
      handleSequenceReserved(msg, sequenceContext)
    case WrappedSequenceStoreResponse(SequenceStore.ReservationFailed)          => Behaviors.same
    case ReceiveTimeout                                                         => Behaviors.stopped
    case Initialize                                                             => Behaviors.unhandled
    case WrappedSequenceStoreResponse(_: SequenceStore.InitialSequenceReserved) => Behaviors.unhandled
    case WrappedSequenceStoreResponse(msg: SequenceStore.SequenceReset)         =>
      // reset するときは必ず empty になっているため
      Behaviors.unhandled
  }

  private[this] def empty(sequenceContext: SequenceContext): Behavior[Command] = Behaviors.receiveMessage {
    case msg: GenerateSequence =>
      // no reply
      logger.warn(
        "Pending generate sequence because reserving sequence: current max reserved: {}, next sequence value: {}",
        sequenceContext.maxReservedValue,
        sequenceContext.nextValue,
      )
      stashBuffer.stash(msg)
      prepareNextSequence(sequenceContext = sequenceContext, nextSequence = sequenceContext)
    case WrappedSequenceStoreResponse(msg: SequenceStore.SequenceReserved) =>
      stashBuffer.unstashAll(handleSequenceReserved(msg, sequenceContext))
    case WrappedSequenceStoreResponse(msg: SequenceStore.SequenceReset) =>
      stashBuffer.unstashAll(handleSequenceReset(msg, sequenceContext))
    case WrappedSequenceStoreResponse(SequenceStore.ReservationFailed)          => Behaviors.same
    case ReceiveTimeout                                                         => Behaviors.stopped
    case Initialize                                                             => Behaviors.unhandled
    case WrappedSequenceStoreResponse(_: SequenceStore.InitialSequenceReserved) => Behaviors.unhandled
  }

  private[this] def acceptGenerateSequence(
      msg: GenerateSequence,
      sequenceContext: SequenceContext,
  ): Behavior[Command] = {
    msg.replyTo ! SequenceGenerated(sequenceContext.nextValue, sequenceSubId)
    logger.debug("SequenceGenerated: {}", sequenceContext.nextValue)
    prepareNextSequence(sequenceContext = sequenceContext, nextSequence = sequenceContext.next())
  }

  private[this] def prepareNextSequence(
      sequenceContext: SequenceContext,
      nextSequence: SequenceContext,
  ): Behavior[Command] = {
    if (nextSequence.isOverflow) {
      reset()
      empty(nextSequence)
    } else if (nextSequence.isEmpty) {
      empty(nextSequence)
    } else if (nextSequence.isStarving) {
      reserve(sequenceContext = sequenceContext, nextSequence = nextSequence)
      ready(nextSequence)
    } else {
      ready(nextSequence)
    }
  }

  private[this] def reserve(sequenceContext: SequenceContext, nextSequence: SequenceContext): Unit = {
    val freeAmount = nextSequence.freeAmount
    logger.info(
      "Reserving sequence: remain {}, add {}, current max reserved: {}",
      sequenceContext.remainAmount,
      freeAmount,
      sequenceContext.maxReservedValue,
    )
    sequenceStore ! SequenceStore.ReserveSequence(
      sequenceContext.maxReservedValue,
      freeAmount,
      sequenceSubId,
      responseMapper,
    )
  }

  private[this] def handleSequenceReserved(
      msg: SequenceStore.SequenceReserved,
      sequenceContext: SequenceContext,
  ): Behavior[Command] = {
    val nextSequence = sequenceContext.copy(maxReservedValue = msg.maxReservedValue)
    if (nextSequence.isOverflow) {
      empty(nextSequence)
    } else if (nextSequence.isEmpty) {
      empty(nextSequence)
    } else {
      ready(nextSequence)
    }
  }

  private[this] def reset(): Unit = {
    sequenceStore ! SequenceStore.ResetReserveSequence(
      firstValue,
      reservationAmount,
      sequenceSubId,
      responseMapper,
    )
  }

  private[this] def handleSequenceReset(
      msg: SequenceStore.SequenceReset,
      sequenceContext: SequenceContext,
  ): Behavior[Command] = {
    logger.warn("reset sequence: {}", msg.maxReservedValue)
    val nextSequence = SequenceContext(msg.maxReservedValue, nextValue = firstValue)
    prepareNextSequence(sequenceContext, nextSequence)
  }
}
