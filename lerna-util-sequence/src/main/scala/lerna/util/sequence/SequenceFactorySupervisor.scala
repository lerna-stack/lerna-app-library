package lerna.util.sequence

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
import lerna.util.sequence.SequenceStore.Command
import lerna.util.tenant.Tenant

private[sequence] object SequenceFactorySupervisor {

  def apply(sequenceId: String, maxSequenceValue: BigInt, reservationAmount: Int)(implicit
      tenant: Tenant,
  ): Behavior[SequenceFactoryWorker.GenerateSequence] = Behaviors
    .supervise[SequenceFactoryWorker.GenerateSequence](
      Behaviors.setup { context =>
        new SequenceFactorySupervisor(
          sequenceId = sequenceId,
          maxSequenceValue = maxSequenceValue,
          reservationAmount = reservationAmount,
          context,
        ).createBehavior()
      },
    ).onFailure[Exception](SupervisorStrategy.restart)
}

private[sequence] final class SequenceFactorySupervisor(
    sequenceId: String,
    maxSequenceValue: BigInt,
    reservationAmount: Int,
    context: ActorContext[SequenceFactoryWorker.GenerateSequence],
)(implicit tenant: Tenant) {

  private[this] val config = new SequenceFactoryConfig(context.system.settings.config)

  private[this] val store = createSequenceStore()

  def createBehavior(): Behavior[SequenceFactoryWorker.GenerateSequence] =
    Behaviors.receiveMessage[SequenceFactoryWorker.GenerateSequence] { command =>
      val sequenceSubId = command.sequenceSubId
      val worker =
        context
          .child(workerNameOf(sequenceSubId))
          // FIXME: `unsafeUpcast` https://doc.akka.io/docs/akka/current/typed/from-classic.html#actorcontext-children
          .map(_.unsafeUpcast[SequenceFactoryWorker.Command])
          .getOrElse(createWorker(sequenceSubId))

      worker ! command
      Behaviors.same
    }

  private def createWorker(sequenceSubId: Option[String]): ActorRef[SequenceFactoryWorker.Command] =
    context.spawn(
      SequenceFactoryWorker.apply(
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

  private def createSequenceStore(): ActorRef[SequenceStore.Command] = {
    val behavior: Behavior[Command] = SequenceStore.apply(
      sequenceId = sequenceId,
      nodeId = config.nodeId,
      incrementStep = config.incrementStep,
      config = config.cassandraConfig,
    )

    context.spawn(
      behavior,
      s"$sequenceId-store",
    )
  }

  private def workerNameOf(sequenceSubId: Option[String]) =
    s"$sequenceId-worker${sequenceSubId.map(sub => "-" + sub).getOrElse("")}"
}
