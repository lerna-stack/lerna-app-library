package lerna.util.sequence

import akka.Done
import akka.actor.{ Actor, ActorRef, NoSerializationVerificationNeeded, Props, Stash, Status }
import akka.pattern.pipe
import com.datastax.driver.core._
import lerna.log.AppActorLogging
import lerna.util.sequence.FutureConverters.ListenableFutureConverter
import lerna.util.tenant.Tenant

import scala.jdk.CollectionConverters._
import scala.concurrent.Future

private[sequence] object SequenceStore {

  def props(sequenceId: String, nodeId: Int, incrementStep: BigInt, config: SequenceFactoryCassandraConfig)(implicit
      tenant: Tenant,
  ): Props =
    Props(new SequenceStore(sequenceId = sequenceId, nodeId = nodeId, incrementStep = incrementStep, config = config))

  sealed trait Command            extends NoSerializationVerificationNeeded
  private case object OpenSession extends Command
  final case class InitialReserveSequence(firstValue: BigInt, reservationAmount: Int, sequenceSubId: Option[String])
      extends Command {
    require(firstValue > 0 && reservationAmount > 0)
  }
  final case class ReserveSequence(maxReservedValue: BigInt, reservationAmount: Int, sequenceSubId: Option[String])
      extends Command {
    require(maxReservedValue > 0 && reservationAmount > 0)
  }
  final case class ResetReserveSequence(firstValue: BigInt, reservationAmount: Int, sequenceSubId: Option[String])
      extends Command {
    require(firstValue > 0 && reservationAmount > 0)
  }

  sealed trait DomainEvent                                                 extends NoSerializationVerificationNeeded
  private final case class SessionOpened(session: Session)                 extends DomainEvent
  private final case class SessionPrepared(sessionContext: SessionContext) extends DomainEvent
  final case class InitialSequenceReserved(
      initialValue: BigInt,
      maxReservedValue: BigInt,
  ) extends DomainEvent {
    require(initialValue >= 0 && maxReservedValue > 0)
  }
  final case class SequenceReserved(maxReservedValue: BigInt) extends DomainEvent {
    require(maxReservedValue > 0)
  }
  final case class SequenceReset(maxReservedValue: BigInt) extends DomainEvent {
    require(maxReservedValue > 0)
  }
  final case object ReservationFailed extends RuntimeException with DomainEvent

  private final case class SessionContext(
      session: Session,
      selectSequenceReservationStatement: PreparedStatement,
      insertSequenceReservationStatement: PreparedStatement,
  )
}

private[sequence] final class SequenceStore(
    sequenceId: String,
    nodeId: Int,
    incrementStep: BigInt,
    config: SequenceFactoryCassandraConfig,
)(implicit tenant: Tenant)
    extends Actor
    with Stash
    with AppActorLogging {
  require(nodeId > 0)

  import SequenceStore._
  import context.dispatcher
  import lerna.util.tenant.TenantComponentLogContext.logContext

  val statements = new CassandraStatements(config)

  override def receive: Receive = notReady

  override def preStart(): Unit = {
    super.preStart()
    self ! OpenSession
  }

  // Actor の状態を意識しながら Session を扱いたいので、Session の乱用を防ぐため、
  // Session を直接配置せずに Session を close するだけの関数を配置
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private[this] var sessionCloser: Option[() => Unit] = None

  override def postStop(): Unit = {
    sessionCloser.foreach(_.apply())
    super.postStop()
  }

  private[this] def notReady: Receive = {
    case OpenSession               => openSession() pipeTo self
    case _: InitialReserveSequence => stash()
    case _: ReserveSequence        => stash()
    case SessionOpened(session) =>
      sessionCloser = Option(() => session.close())
      prepareSession(session) pipeTo self
    case SessionPrepared(sessionContext) =>
      unstashAll()
      logger.info("Cassandra session was ready (sequenceId: {}, nodeId: {})", sequenceId, nodeId)
      context.become(ready(sessionContext))
  }

  private[this] def ready(implicit sessionContext: SessionContext): Receive = {
    case InitialReserveSequence(firstValue, reservationAmount, sequenceSubId) =>
      initialReserve(firstValue, reservationAmount, sequenceSubId) pipeTo self
      context.become(reserving(replyTo = sender()))
    case ReserveSequence(maxReservationValue, reservationAmount, sequenceSubId) =>
      reserve(maxReservationValue, reservationAmount, sequenceSubId) pipeTo self
      context.become(reserving(replyTo = sender()))
    case ResetReserveSequence(firstValue, reservationAmount, sequenceSubId) =>
      reset(firstValue, reservationAmount, sequenceSubId) pipeTo self
      context.become(reserving(replyTo = sender()))
  }

  private[this] def reserving(replyTo: ActorRef)(implicit sessionContext: SessionContext): Receive = {
    case _: InitialReserveSequence =>
      stash()
    case _: ReserveSequence =>
      stash()
    case _: ResetReserveSequence =>
      stash()
    case event: InitialSequenceReserved =>
      replyTo ! event
      unstashAll()
      context.become(ready)
    case event: SequenceReserved =>
      replyTo ! event
      unstashAll()
      context.become(ready)
    case event: SequenceReset =>
      replyTo ! event
      unstashAll()
      context.become(ready)
    case Status.Failure(ex) =>
      replyTo ! ReservationFailed
      throw ex
  }

  override def unhandled(message: Any): Unit = message match {
    case Status.Failure(ex) => throw ex
    case other              => super.unhandled(other)
  }

  private[this] def openSession(): Future[SessionOpened] = {
    val sessionFuture = config
      .buildCassandraClusterConfig()
      .connectAsync().asScala

    sessionFuture.map(SessionOpened.apply)
  }

  private[this] def executeWrite(statement: Statement)(implicit sessionContext: SessionContext): Future[Done] = {
    import sessionContext._
    statement.setConsistencyLevel(config.cassandraWriteConsistency)
    session.executeAsync(statement).asScala.map(_ => Done)
  }

  private[this] def executeRead(statement: Statement)(implicit sessionContext: SessionContext): Future[Option[Row]] = {
    import sessionContext._
    statement.setConsistencyLevel(config.cassandraReadConsistency)
    session.executeAsync(statement).asScala.map(_.all().asScala.headOption)
  }

  private[this] def prepareSession(session: Session): Future[SessionPrepared] = {
    for {
      _                                  <- session.executeAsync(statements.createKeyspace).asScala
      _                                  <- session.executeAsync(statements.useKeyspace).asScala
      _                                  <- session.executeAsync(statements.createTable).asScala
      selectSequenceReservationStatement <- session.prepareAsync(statements.selectSequenceReservation).asScala
      insertSequenceReservationStatement <- session.prepareAsync(statements.insertSequenceReservation).asScala
    } yield SessionPrepared(
      SessionContext(
        session,
        selectSequenceReservationStatement.setRetryPolicy(config.cassandraReadRetryPolicy),
        insertSequenceReservationStatement.setRetryPolicy(config.cassandraWriteRetryPolicy),
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
        selectSequenceReservationStatement.bind(sequenceId, normalizeSubId(sequenceSubId), Integer.valueOf(nodeId)),
      )
      maybePrevMaxReservedValue = maybeRow.map(v => BigInt(v.getVarint("max_reserved_value")))
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
