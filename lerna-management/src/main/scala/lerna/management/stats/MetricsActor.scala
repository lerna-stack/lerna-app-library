package lerna.management.stats

import akka.actor.{ Actor, Props }
import lerna.util.lang.Equals._

@SuppressWarnings(Array("org.wartremover.warts.Recursion"))
private[stats] class MetricsActor extends Actor {

  import MetricsActor._

  override def receive: Receive = {
    Actor.emptyBehavior
  }

  override def preStart(): Unit = {
    context.become(active(Map()))
  }

  def active(metricsMap: Map[MetricsKey, MetricsValue]): Receive = {
    case GetMetrics(key) =>
      sender() ! metricsMap.get(key)
    case UpdateMetrics(key, Some(value)) =>
      context.become(active(metricsMap updated (key, value)))
    case UpdateMetrics(key, None) =>
      // delete value
      context.become(active(metricsMap.filterNot { case (k, _) => k === key }))
  }

}

private[stats] sealed trait MetricsActorCommand

private[stats] object MetricsActor {

  def props(): Props = Props(new MetricsActor)

  final case class UpdateMetrics(key: MetricsKey, value: Option[MetricsValue]) extends MetricsActorCommand
  final case class GetMetrics(key: MetricsKey)                                 extends MetricsActorCommand
}
