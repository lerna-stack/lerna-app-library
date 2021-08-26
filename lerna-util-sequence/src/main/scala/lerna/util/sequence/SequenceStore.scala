package lerna.util.sequence

import akka.Done
import akka.actor.NoSerializationVerificationNeeded
import akka.actor.typed._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{ PreparedStatement, Row, Statement }
import com.datastax.oss.driver.api.core.servererrors.{ ReadTimeoutException, WriteTimeoutException }
import com.datastax.oss.driver.api.core.session.Session
import lerna.log.{ AppLogger, AppTypedActorLogging }
import lerna.util.lang.Equals._
import lerna.util.tenant.Tenant

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
import scala.util.{ Failure, Success }

private[sequence] object SequenceStore extends AppTypedActorLogging {

  def apply(sequenceId: String, nodeId: Int, incrementStep: BigInt, config: SequenceFactoryCassandraConfig)(implicit
      tenant: Tenant,
  ): Behavior[Command] = {
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    var behavior: Behavior[Command] = Behaviors.setup { context =>
      val capacity = context.system.settings.config.getInt("lerna.util.sequence.store.stash-capacity")
      Behaviors.withStash(capacity) { buffer =>
        withLogger { logger =>
          new SequenceStore(
            sequenceId = sequenceId,
            nodeId = nodeId,
            incrementStep = incrementStep,
            config = config,
            context,
            buffer,
            logger,
          ).createBehavior()
        }
      }
    }

    // SupervisorStrategy
    // see also https://docs.datastax.com/en/developer/java-driver/4.13/manual/core/retries/#retries
    // 一時的にレプリカが処理できなくなっているだけなので、Cassandra サイドで回復することを期待する
    behavior = Behaviors.supervise(behavior).onFailure[ReadTimeoutException](SupervisorStrategy.resume)
    // 一時的にレプリカが処理できなくなっているだけなので、Cassandra サイドで回復することを期待する
    behavior = Behaviors.supervise(behavior).onFailure[WriteTimeoutException](SupervisorStrategy.resume)
    // その他の例外は restart して、回復することを期待する
    behavior = Behaviors.supervise(behavior).onFailure[Exception](SupervisorStrategy.restart)
    behavior
  }

  sealed trait Command            extends NoSerializationVerificationNeeded
  private case object OpenSession extends Command
  final case class InitialReserveSequence(
      firstValue: BigInt,
      reservationAmount: Int,
      sequenceSubId: Option[String],
      replyTo: ActorRef[ReservationResponse],
  ) extends Command {
    require(firstValue > 0 && reservationAmount > 0)
  }
  final case class ReserveSequence(
      maxReservedValue: BigInt,
      reservationAmount: Int,
      sequenceSubId: Option[String],
      replyTo: ActorRef[ReservationResponse],
  ) extends Command {
    require(maxReservedValue > 0 && reservationAmount > 0)
  }
  final case class ResetReserveSequence(
      firstValue: BigInt,
      reservationAmount: Int,
      sequenceSubId: Option[String],
      replyTo: ActorRef[ReservationResponse],
  ) extends Command {
    require(firstValue > 0 && reservationAmount > 0)
  }

  sealed trait SessionResult                                               extends Command
  private final case class SessionOpened(session: CqlSession)              extends SessionResult
  private final case class SessionPrepared(sessionContext: SessionContext) extends SessionResult
  private final case class SessionFailed(exception: Throwable)             extends SessionResult

  sealed trait ReservationResponse       extends NoSerializationVerificationNeeded
  sealed trait InternalReservationResult extends Command
  final case class InitialSequenceReserved(
      initialValue: BigInt,
      maxReservedValue: BigInt,
  ) extends InternalReservationResult
      with ReservationResponse {
    require(initialValue >= 0 && maxReservedValue > 0)
  }
  final case class SequenceReserved(maxReservedValue: BigInt)
      extends InternalReservationResult
      with ReservationResponse {
    require(maxReservedValue > 0)
  }
  final case class SequenceReset(maxReservedValue: BigInt) extends InternalReservationResult with ReservationResponse {
    require(maxReservedValue > 0)
  }
  private final case class InternalReservationFailed(exception: Throwable) extends InternalReservationResult

  final case object ReservationFailed extends RuntimeException with ReservationResponse

  private final case class SessionContext(
      session: CqlSession,
      selectSequenceReservationStatement: PreparedStatement,
      insertSequenceReservationStatement: PreparedStatement,
  )
}

private[sequence] final class SequenceStore(
    sequenceId: String,
    nodeId: Int,
    incrementStep: BigInt,
    config: SequenceFactoryCassandraConfig,
    context: ActorContext[SequenceStore.Command],
    stashBuffer: StashBuffer[SequenceStore.Command],
    logger: AppLogger,
)(implicit tenant: Tenant) {
  require(nodeId > 0)

  import SequenceStore._
  import context.executionContext
  import lerna.util.tenant.TenantComponentLogContext.logContext

  val statements = new CassandraStatements(config)

  def createBehavior(): Behavior[Command] = {
    context.self ! OpenSession
    notReady
  }

  private def close(session: Session): PartialFunction[(ActorContext[Command], Signal), Behavior[Command]] = {
    case (_, signal) if signal === PreRestart || signal === PostStop =>
      session.close()
      Behaviors.same
  }

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity", "org.wartremover.warts.Recursion"))
  private[this] def notReady: Behaviors.Receive[Command] = Behaviors.receiveMessage {
    case OpenSession =>
      context.pipeToSelf(openSession()) {
        case Success(sessionOpened) => sessionOpened
        case Failure(exception)     => SessionFailed(exception)
      }
      Behaviors.same
    case message: InitialReserveSequence =>
      stashBuffer.stash(message)
      Behaviors.same
    case message: ReserveSequence =>
      stashBuffer.stash(message)
      Behaviors.same
    case message: ResetReserveSequence =>
      stashBuffer.stash(message)
      Behaviors.same
    case SessionOpened(session) =>
      context.pipeToSelf(prepareSession(session)) {
        case Success(sessionPrepared) => sessionPrepared
        case Failure(exception)       => SessionFailed(exception)
      }
      notReady.receiveSignal(close(session))
    case SessionPrepared(sessionContext) =>
      logger.info("Cassandra session was ready (sequenceId: {}, nodeId: {})", sequenceId, nodeId)
      stashBuffer.unstashAll(ready(sessionContext))

    case SessionFailed(throwable)     => throw throwable
    case _: InternalReservationResult => Behaviors.unhandled
  }

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  private[this] def ready(implicit sessionContext: SessionContext): Behavior[Command] = Behaviors
    .receiveMessage[Command] {
      case InitialReserveSequence(firstValue, reservationAmount, sequenceSubId, replyTo) =>
        context.pipeToSelf(initialReserve(firstValue, reservationAmount, sequenceSubId)) {
          case Success(initialSequenceReserved) => initialSequenceReserved
          case Failure(exception)               => InternalReservationFailed(exception)
        }
        reserving(replyTo = replyTo)
      case ReserveSequence(maxReservationValue, reservationAmount, sequenceSubId, replyTo) =>
        context.pipeToSelf(reserve(maxReservationValue, reservationAmount, sequenceSubId)) {
          case Success(sequenceReserved) => sequenceReserved
          case Failure(exception)        => InternalReservationFailed(exception)
        }
        reserving(replyTo = replyTo)
      case ResetReserveSequence(firstValue, reservationAmount, sequenceSubId, replyTo) =>
        context.pipeToSelf(reset(firstValue, reservationAmount, sequenceSubId)) {
          case Success(sequenceReset) => sequenceReset
          case Failure(exception)     => InternalReservationFailed(exception)
        }
        reserving(replyTo = replyTo)
      case OpenSession                  => Behaviors.unhandled
      case _: SessionResult             => Behaviors.unhandled
      case _: InternalReservationResult => Behaviors.unhandled
    }.receiveSignal(close(sessionContext.session))

  private[this] def reserving(replyTo: ActorRef[ReservationResponse])(implicit sessionContext: SessionContext) =
    Behaviors
      .receiveMessage[Command] {
        case message: InitialReserveSequence =>
          stashBuffer.stash(message)
          Behaviors.same
        case message: ReserveSequence =>
          stashBuffer.stash(message)
          Behaviors.same
        case message: ResetReserveSequence =>
          stashBuffer.stash(message)
          Behaviors.same
        case event: InitialSequenceReserved =>
          replyTo ! event
          stashBuffer.unstashAll(ready)
        case event: SequenceReserved =>
          replyTo ! event
          stashBuffer.unstashAll(ready)
        case event: SequenceReset =>
          replyTo ! event
          stashBuffer.unstashAll(ready)
        case InternalReservationFailed(exception) =>
          replyTo ! ReservationFailed
          throw exception
        case OpenSession      => Behaviors.unhandled
        case _: SessionResult => Behaviors.unhandled
      }.receiveSignal(close(sessionContext.session))

  private[this] def openSession(): Future[SessionOpened] = {
    CqlSessionProvider
      .connect(context.system, config)
      .map(SessionOpened.apply)
  }

  private[this] def executeWrite[T <: Statement[T]](
      statement: Statement[T],
  )(implicit sessionContext: SessionContext): Future[Done] = {
    import sessionContext._
    session.executeAsync(statement.setExecutionProfileName(config.writeProfileName)).toScala.map(_ => Done)
  }

  private[this] def executeRead[T <: Statement[T]](
      statement: Statement[T],
  )(implicit sessionContext: SessionContext): Future[Option[Row]] = {
    import sessionContext._
    session.executeAsync(statement.setExecutionProfileName(config.readProfileName)).toScala.map { asyncResult =>
      Option(asyncResult.one())
    }
  }

  private[this] def prepareSession(session: CqlSession): Future[SessionPrepared] = {
    for {
      _                                  <- session.executeAsync(statements.createKeyspace).toScala
      _                                  <- session.executeAsync(statements.useKeyspace).toScala
      _                                  <- session.executeAsync(statements.createTable).toScala
      selectSequenceReservationStatement <- session.prepareAsync(statements.selectSequenceReservation).toScala
      insertSequenceReservationStatement <- session.prepareAsync(statements.insertSequenceReservation).toScala
    } yield SessionPrepared(
      SessionContext(
        session,
        selectSequenceReservationStatement,
        insertSequenceReservationStatement,
      ),
    )
  }

  private[this] def reserve(
      maxReservedValue: BigInt,
      reservationAmount: BigInt,
      sequenceSubId: Option[String],
  )(implicit sessionContext: SessionContext): Future[SequenceReserved] = {
    writeReservation(
      newMaxReservedValue = maxReservedValue + (incrementStep * reservationAmount),
      sequenceSubId = sequenceSubId,
    )
  }

  private[this] def initialReserve(
      firstValue: BigInt,
      reservationAmount: BigInt,
      sequenceSubId: Option[String],
  )(implicit sessionContext: SessionContext): Future[InitialSequenceReserved] = {
    import sessionContext._
    for {
      maybeRow <- executeRead(
        selectSequenceReservationStatement.bind(
          sequenceId,
          normalizeSubId(sequenceSubId),
          Integer.valueOf(nodeId),
        ),
      )
      maybePrevMaxReservedValue = maybeRow.map(v => BigInt(v.getBigInteger("max_reserved_value")))
      initialValue              = maybePrevMaxReservedValue.map(_ + incrementStep).getOrElse(firstValue)
      reserved <- writeReservation(
        newMaxReservedValue = initialValue + (incrementStep * (reservationAmount - 1)),
        sequenceSubId = sequenceSubId,
      )
    } yield InitialSequenceReserved(initialValue, reserved.maxReservedValue)
  }

  private[this] def reset(
      firstValue: BigInt,
      reservationAmount: BigInt,
      sequenceSubId: Option[String],
  )(implicit sessionContext: SessionContext): Future[SequenceReset] = {
    reserve(maxReservedValue = firstValue, reservationAmount, sequenceSubId)
      .map(r => SequenceReset(r.maxReservedValue))
  }

  private[this] def writeReservation(
      newMaxReservedValue: BigInt,
      sequenceSubId: Option[String],
  )(implicit sessionContext: SessionContext): Future[SequenceReserved] = {
    import sessionContext._
    for {
      _ <- executeWrite(
        insertSequenceReservationStatement.bind(
          sequenceId,
          normalizeSubId(sequenceSubId),
          Integer.valueOf(nodeId),
          newMaxReservedValue.bigInteger,
        ),
      )
    } yield {
      // Cassandra のシーケンスをフルバックアップから回復させるとバックアップ取得後に発行したシーケンスが重複する問題対策
      // フルバックアップ後に発行したシーケンスがわかるように採番（予約）済み番号をログに出す
      logger.info(
        "Reserved > sequenceId: {}  sequenceSubId: {}  nodeId: {}  reservedValue: {}",
        sequenceId,
        sequenceSubId.getOrElse("-"),
        nodeId,
        newMaxReservedValue,
      )
      SequenceReserved(newMaxReservedValue)
    }
  }

  private[this] def normalizeSubId(sequenceSubId: Option[String]): String = {
    sequenceSubId.getOrElse("")
  }
}
