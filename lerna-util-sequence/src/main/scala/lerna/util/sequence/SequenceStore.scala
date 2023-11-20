package lerna.util.sequence

import akka.Done
import akka.actor.NoSerializationVerificationNeeded
import akka.actor.typed._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import com.datastax.oss.driver.api.core.connection.{ ClosedConnectionException, HeartbeatException }
import com.datastax.oss.driver.api.core.cql.{ PreparedStatement, Row, Statement }
import com.datastax.oss.driver.api.core.servererrors._
import com.datastax.oss.driver.api.core.session.Session
import com.datastax.oss.driver.api.core.{ AllNodesFailedException, CqlSession, DriverTimeoutException }
import lerna.log.{ AppLogger, AppTypedActorLogging }
import lerna.util.lang.Equals._
import lerna.util.tenant.Tenant

import scala.concurrent.Future
import scala.util.{ Failure, Success }

private[sequence] object SequenceStore extends AppTypedActorLogging {

  def apply(
      sequenceId: String,
      nodeId: Int,
      incrementStep: BigInt,
      config: SequenceFactoryCassandraConfig,
      cqlSessionProvider: CqlSessionProvider = CqlSessionProvider,
      executor: CqlStatementExecutor = CqlStatementExecutor,
  )(implicit
      tenant: Tenant,
  ): Behavior[Command] = {
    val behavior: Behavior[Command] = Behaviors.setup { context =>
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
            cqlSessionProvider,
            executor,
          ).createBehavior()
        }
      }
    }
    Behaviors.supervise(behavior).onFailure(SupervisorStrategy.restart)
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
      reservationAmount: BigInt,
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

  /** Returns true if a SequenceStore should restart to recover from the given exception. */
  def shouldRestartToRecoverFrom(exception: Throwable): Boolean = {
    // 基本方針として、Cassandra に再接続しなくても回復できるだろう例外は自身を再起動しない。
    // それ以外の例外は、Cassandra に再接続するために自身を再起動する。
    //
    // 発生しうる例外は次のリンクから確認できる:
    // https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/retries/#retries
    //
    // DefaultRetryPolicy は次のリンクから確認できる:
    // https://github.com/datastax/java-driver/blob/4.6.0/core/src/main/java/com/datastax/oss/driver/internal/core/retry/DefaultRetryPolicy.java
    //
    // 例外発生状況を明示することを目的として、一部コードは冗長な記載である:
    //
    exception match {
      case _: UnavailableException =>
        // Consistency Level を満たせる十分な Replica が存在しなかった。
        // Cassandra クラスタが回復することを期待する。Cassandra に再接続しなくてよい。
        // - https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/retries/#on-unavailable
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/servererrors/UnavailableException.html
        false
      case _: ReadTimeoutException =>
        // レプリカが読み込みリクエストに時間内に応答しなかった。
        // Cassandra クラスタが回復することを期待する。Cassandra に再接続しなくてよい。
        // - https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/retries/#on-read-timeout
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/servererrors/ReadTimeoutException.html
        false
      case _: WriteTimeoutException =>
        // レプリカが書き込みリクエストに時間内に応答しなかった。
        // Cassandra クラスタが回復することを期待する。Cassandra に再接続しなくてよい。
        // - https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/retries/#on-write-timeout
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/servererrors/WriteTimeoutException.html
        false
      case _: ClosedConnectionException | _: HeartbeatException =>
        // ClosedConnectionException: コネクションが外部要因で閉じられた。
        // HeartbeatException: ハートビートクエリに失敗した。
        // 安全のため再起動する。
        // - https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/retries/#on-request-aborted
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/connection/ClosedConnectionException.html
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/connection/HeartbeatException.html
        true
      case _: OverloadedException | _: ServerError | _: TruncateException =>
        // OverloadedException: コーディネータが過負荷であった。
        // ServerError: サーバが内部エラーを報告した。
        // TruncateException: truncate 操作でエラーが発生した。
        // 安全のため再起動する。
        // - https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/retries/#on-error-response
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/servererrors/OverloadedException.html
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/servererrors/ServerError.html
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/servererrors/TruncateException.html
        true
      case _: ReadFailureException | _: WriteFailureException =>
        // ReadFailureException: レプリカが読み込みリクエストにタイムアウト以外のエラーを応答した。
        // WriteFailureException: レプリカが書き込みリクエストにタイムアウト以外のエラーを応答した。
        // Cassandra クラスタが回復することを期待する。Cassandra に再接続しなくてよい。
        // - https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/retries/#on-error-response
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/servererrors/ReadFailureException.html
        // - https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/servererrors/WriteFailureException.html
        false
      case _: AllNodesFailedException =>
        // すべてのコーディネータに対してクエリが失敗した。
        // 自身が孤立しているか、Cassandra で災害が発生している可能性がある。
        // 安全のため再起動する。
        // https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/AllNodesFailedException.html
        true
      case _: DriverTimeoutException =>
        // ドライバ要求でタイムアウトが発生した。
        // https://docs.datastax.com/en/drivers/java/4.6/com/datastax/oss/driver/api/core/DriverTimeoutException.html
        // 安全のため再起動する。
        true
      case _ =>
        // その他の例外は再起動して回復することを期待する。
        true
    }
  }

}

private[sequence] final class SequenceStore(
    sequenceId: String,
    nodeId: Int,
    incrementStep: BigInt,
    config: SequenceFactoryCassandraConfig,
    context: ActorContext[SequenceStore.Command],
    stashBuffer: StashBuffer[SequenceStore.Command],
    logger: AppLogger,
    cqlSessionProvider: CqlSessionProvider,
    executor: CqlStatementExecutor,
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
    case SessionFailed(throwable) =>
      // TODO 一部の例外は再起動せずに処理を再試行する
      throw throwable // to restart
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

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
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
          if (shouldRestartToRecoverFrom(exception)) {
            throw exception // to restart
          } else {
            // TODO 失敗した処理の情報(initialReserve,reserve,rest)を出力する
            logger.info(
              "sequenceId=[{}], nodeId=[{}]: Recovering from InternalReservationFailed: [{}: {}]",
              sequenceId,
              nodeId,
              exception.getClass.getCanonicalName,
              exception.getMessage,
            )
            stashBuffer.unstashAll(ready)
          }
        case OpenSession      => Behaviors.unhandled
        case _: SessionResult => Behaviors.unhandled
      }.receiveSignal(close(sessionContext.session))

  private[this] def openSession(): Future[SessionOpened] = {
    cqlSessionProvider
      .connect(context.system, config)
      .map(SessionOpened.apply)
  }

  private[this] def executeWrite[T <: Statement[T]](
      statement: Statement[T],
  )(implicit sessionContext: SessionContext): Future[Done] = {
    implicit val session: CqlSession = sessionContext.session
    val statementWithWriteProfileName =
      statement.setExecutionProfileName(config.writeProfileName)
    executor
      .executeAsync(statementWithWriteProfileName)
      .map(_ => Done)
  }

  private[this] def executeRead[T <: Statement[T]](
      statement: Statement[T],
  )(implicit sessionContext: SessionContext): Future[Option[Row]] = {
    implicit val session: CqlSession = sessionContext.session
    val statementWithReadProfileName =
      statement.setExecutionProfileName(config.readProfileName)
    executor
      .executeAsync(statementWithReadProfileName)
      .map { asyncResultSet =>
        Option(asyncResultSet.one())
      }
  }

  private[this] def prepareSession(session: CqlSession): Future[SessionPrepared] = {
    implicit val cqSession: CqlSession = session
    for {
      _                                  <- executor.executeAsync(statements.createKeyspace)
      _                                  <- executor.executeAsync(statements.useKeyspace)
      _                                  <- executor.executeAsync(statements.createTable)
      selectSequenceReservationStatement <- executor.prepareAsync(statements.selectSequenceReservation)
      insertSequenceReservationStatement <- executor.prepareAsync(statements.insertSequenceReservation)
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
    reserve(maxReservedValue = firstValue, reservationAmount - 1, sequenceSubId)
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
