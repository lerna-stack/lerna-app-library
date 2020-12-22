package lerna.util.sequence

import akka.actor.SupervisorStrategy._
import akka.actor.{ Actor, ActorRef, OneForOneStrategy, Props, SupervisorStrategy }
import com.datastax.driver.core.exceptions._
import lerna.log.AppActorLogging
import lerna.util.tenant.Tenant

private[sequence] object SequenceFactorySupervisor {

  def props(sequenceId: String, maxSequenceValue: BigInt, reservationAmount: Int)(implicit tenant: Tenant): Props =
    Props(
      new SequenceFactorySupervisor(
        sequenceId = sequenceId,
        maxSequenceValue = maxSequenceValue,
        reservationAmount = reservationAmount,
      ),
    )

}

private[sequence] final class SequenceFactorySupervisor(
    sequenceId: String,
    maxSequenceValue: BigInt,
    reservationAmount: Int,
)(implicit tenant: Tenant)
    extends Actor
    with AppActorLogging {

  import lerna.util.tenant.TenantComponentLogContext.logContext

  private[this] val config = new SequenceFactoryConfig(context.system.settings.config)

  private[this] val store = createSequenceStore()

  override def receive: Receive = {
    case command @ SequenceFactoryWorker.GenerateSequence(sequenceSubId) =>
      val worker =
        context
          .child(workerNameOf(sequenceSubId))
          .getOrElse(createWorker(sequenceSubId))

      worker forward command
  }

  // see: https://docs.datastax.com/en/developer/java-driver/3.6/manual/retries/#retry-policy
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(loggingEnabled = false) {
      case e: NoHostAvailableException =>
        logger.warn(e, "Cassandra is not available")
        Restart
      case e: UnsupportedFeatureException =>
        logger.warn(e, "Illegal operation detected")
        Restart
      case e: ReadTimeoutException =>
        // 一時的にレプリカが処理できなくなっているだけなので、Cassandra サイドで回復することを期待する
        logger.warn(e, "Read query failed. Resuming SequenceStore")
        Resume
      case e: WriteTimeoutException =>
        // 一時的にレプリカが処理できなくなっているだけなので、Cassandra サイドで回復することを期待する
        logger.warn(e, "Write query failed. Resuming SequenceStore")
        Resume
      case e: OperationTimedOutException =>
        // コーディネーターに何らかの問題が起きている可能性がある。再接続して回復することを期待する
        logger.warn(e, "Query failed. Re-establishing connection")
        Restart
      case e: ConnectionException =>
        // コネクションに問題がある。再接続して回復することを期待する
        logger.warn(e, "Connection broken. Re-establishing connection")
        Restart
      case e =>
        logger.warn(e, "Unexpected error detected")
        Restart
    }

  private def createWorker(sequenceSubId: Option[String]): ActorRef =
    context.actorOf(
      SequenceFactoryWorker.props(
        maxSequenceValue = maxSequenceValue,
        firstValue = config.firstValue,
        incrementStep = config.incrementStep,
        reservationAmount = reservationAmount,
        sequenceStore = store,
        idleTimeout = config.workerIdleTimeout,
        sequenceSubId = sequenceSubId,
      ),
      workerNameOf(sequenceSubId),
    )

  private def createSequenceStore(): ActorRef =
    context.actorOf(
      SequenceStore
        .props(
          sequenceId = sequenceId,
          nodeId = config.nodeId,
          incrementStep = config.incrementStep,
          config = config.cassandraConfig,
        ),
      s"$sequenceId-store",
    )

  private def workerNameOf(sequenceSubId: Option[String]) =
    s"$sequenceId-worker${sequenceSubId.map(sub => "-" + sub).getOrElse("")}"
}
