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

  /** ここに実装されているメソッドは採番前に実行されることを想定する。
    * 例えば [[remainAmount]] は、[[nextValue]] がまだ消費されていない前提で残数を返す。
    */
  final case class SequenceContext(maxReservedValue: BigInt, nextValue: BigInt) {

    /** 採番値を次に進める */
    def next()(implicit config: SequenceConfig): SequenceContext =
      copy(nextValue = nextValue + config.incrementStep)

    /** 採番可能なシーケンスの残数 */
    def remainAmount(implicit config: SequenceConfig): BigInt =
      if (isEmpty) {
        BigInt(0)
      } else {
        val remainExceptNextValue =
          if (maxReservedValue > nextValue) {
            ((maxReservedValue - nextValue) / config.incrementStep)
          } else BigInt(0)
        remainExceptNextValue + 1 // nextValue 分 + 1 する
      }

    /** 追加で予約可能なシーケンスの数 */
    def freeAmount(implicit config: SequenceConfig): Int =
      Math.min(
        // 予約数制限（reservationAmount）の中で採番可能なシーケンスの数
        (config.reservationAmount - remainAmount).toInt,
        // 最大シーケンス番号（maxSequenceValue）までの間で採番可能なシーケンスの数
        ((config.maxSequenceValue - maxReservedValue) / config.incrementStep).toInt,
      )

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
      stashBuffer.unstashAll(prepareNextSequence(nextSequence = sequenceContext))
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
      prepareNextSequence(nextSequence = sequenceContext)
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
    prepareNextSequence(nextSequence = sequenceContext.next())
  }

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  private[this] def prepareNextSequence(nextSequence: SequenceContext): Behavior[Command] = {
    if (nextSequence.isOverflow) {
      reset()
      empty(nextSequence)
    } else if (nextSequence.isEmpty) {
      val freeAmount = nextSequence.freeAmount
      if (freeAmount > 0) {
        reserve(sequenceContext = nextSequence, amount = freeAmount)
        empty(nextSequence)
      } else {
        val message =
          s"freeAmount (${freeAmount.toString}) must be greater than 0 because freeAmount ≦ 0 means that the next sequence is overflow"
        logger.error(new IllegalStateException(message), message)
        Behaviors.stopped
      }
    } else if (nextSequence.isStarving) {
      val freeAmount = nextSequence.freeAmount
      if (freeAmount > 0) {
        reserve(sequenceContext = nextSequence, amount = freeAmount)
        ready(nextSequence)
      } else {
        ready(nextSequence)
      }
    } else {
      ready(nextSequence)
    }
  }

  private[this] def reserve(sequenceContext: SequenceContext, amount: Int): Unit = {
    logger.info(
      "Reserving sequence: remain {}, add {}, current max reserved: {}",
      sequenceContext.remainAmount,
      amount,
      sequenceContext.maxReservedValue,
    )
    sequenceStore ! SequenceStore.ReserveSequence(
      sequenceContext.maxReservedValue,
      amount,
      sequenceSubId,
      responseMapper,
    )
  }

  private[this] def handleSequenceReserved(
      msg: SequenceStore.SequenceReserved,
      sequenceContext: SequenceContext,
  ): Behavior[Command] = {
    if (msg.maxReservedValue > sequenceContext.maxReservedValue) {
      val nextSequence = sequenceContext.copy(maxReservedValue = msg.maxReservedValue)
      if (nextSequence.isOverflow) {
        val message =
          s"Worker should reserve sequence so that it does not overflow [${sequenceContext.toString}, ${msg.toString}]"
        logger.error(new IllegalStateException(message), message)
        Behaviors.stopped
      } else if (nextSequence.isEmpty) {
        val message =
          s"Sequence normally never dries up after reserving [${sequenceContext.toString}, ${msg.toString}]"
        logger.error(new IllegalStateException(message), message)
        Behaviors.stopped
      } else {
        ready(nextSequence)
      }
    } else if (msg.maxReservedValue === sequenceContext.maxReservedValue) {
      // 採番予約のリトライにより同じ結果が返ってきた場合
      Behaviors.same
    } else {
      // SequenceStore が予約要求した順とはことなる順番で結果を返した場合に到達する可能性があるが、
      // 現在の SequenceStore の実装では結果の順序が前後することがないので、到達しないはずのコード。
      val message =
        s"Ignore the maxReservedValue since it reverts to the old value [${sequenceContext.toString}, ${msg.toString}]"
      logger.warn(new IllegalStateException(message), message)
      Behaviors.same
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
    if (sequenceContext.isOverflow) {
      logger.warn("reset sequence: {}", msg.maxReservedValue)
      val nextSequence = SequenceContext(msg.maxReservedValue, nextValue = firstValue)
      if (nextSequence.isOverflow) {
        val message =
          s"Worker should reset sequence so that it does not overflow [${sequenceContext.toString}, ${msg.toString}]"
        logger.error(new IllegalStateException(message), message)
        Behaviors.stopped
      } else if (nextSequence.isEmpty) {
        val message =
          s"Sequence normally never dries up after resetting [${sequenceContext.toString}, ${msg.toString}]"
        logger.error(new IllegalStateException(message), message)
        Behaviors.stopped
      } else {
        ready(nextSequence)
      }
    } else {
      // リセットのリトライによる応答が遅れて返ってきた場合
      Behaviors.same
    }
  }
}
