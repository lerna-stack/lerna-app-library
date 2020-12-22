package lerna.management.stats

import akka.actor.ActorSystem
import kamon.MetricReporter
import lerna.log.AppLogging
import lerna.util.tenant.Tenant

import scala.concurrent.Future

/** A trait that provides metric reporting feature for [[kamon]]
  *
  * ==Overview==
  * The trait can be used for [[kamon]] since it extends [[kamon.MetricReporter]].
  */
trait Metrics extends MetricReporter with AppLogging {

  /** Get the metric value for the `key` optionally
    *
    * @param key The metric key
    * @return An optional value containing the metric value associated with the key, or [[scala.None]] if none exists.
    */
  def getMetrics(key: MetricsKey): Future[Option[MetricsValue]]

}

/** An object that provides metrics reporting features for [[kamon]]
  */
object Metrics {

  /** Create a default instance of [[Metrics]]
    *
    * @param system An actor system used in the instance
    * @param tenants Set of supporting tenants
    * @return The instance of [[Metrics]]
    */
  def apply(system: ActorSystem, tenants: Set[Tenant]): Metrics = {
    new MetricsImpl(system, tenants)
  }

}
