package lerna.management.stats

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import lerna.util.lang.Equals._

private[stats] object MetricsActor {

  def apply(): Behavior[Command] = active(Map())

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def active(metricsMap: Map[MetricsKey, MetricsValue]): Behaviors.Receive[Command] =
    Behaviors.receiveMessage {
      case GetMetrics(key, replyTo) =>
        replyTo ! metricsMap.get(key)
        Behaviors.same

      case UpdateMetrics(key, Some(value)) =>
        active(metricsMap.updated(key, value))

      case UpdateMetrics(key, None) =>
        // delete value
        active(metricsMap.filterNot { case (k, _) => k === key })
    }

  sealed trait Command

  final case class UpdateMetrics(key: MetricsKey, value: Option[MetricsValue])          extends Command
  final case class GetMetrics(key: MetricsKey, replyTo: ActorRef[Option[MetricsValue]]) extends Command
}
