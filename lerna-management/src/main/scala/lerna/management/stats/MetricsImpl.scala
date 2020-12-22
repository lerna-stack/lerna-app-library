package lerna.management.stats

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import lerna.util.lang.Equals._
import kamon.metric.{ MetricDistribution, MetricValue, PeriodSnapshot }
import lerna.management.stats.MetricsActor.{ GetMetrics, UpdateMetrics }
import lerna.util.tenant.Tenant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode

private[stats] final case class SettingItem(key: MetricsKey, name: String, tags: kamon.Tags, nullValue: Option[String])

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
  ),
)
private[stats] class MetricsImpl(system: ActorSystem, tenants: Set[Tenant]) extends Metrics {

  private var settings: Set[SettingItem] = readSettings(system.settings.config)
  private val actor: ActorRef            = system.actorOf(MetricsActor.props())

  implicit lazy val timeout: Timeout = Timeout(3.seconds)

  override def getMetrics(key: MetricsKey): Future[Option[MetricsValue]] = {
    (actor ? GetMetrics(key)).mapTo[Option[MetricsValue]]
  }

  private object SettingExistsMetricValue {
    def unapply(arg: MetricValue): Option[(SettingItem, MetricValue)] = {
      settings.find(e => e.name === arg.name && e.tags === arg.tags).map(s => (s, arg))
    }
  }

  private object SettingExistsMetricDistribution {
    def unapply(arg: MetricDistribution): Option[(SettingItem, MetricDistribution)] = {
      settings.find(e => e.name === arg.name && e.tags === arg.tags).map(s => (s, arg))
    }
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val emptyMetrics: Map[MetricsKey, UpdateMetrics] =
      settings
        .map(e => (e.key, UpdateMetrics(e.key, e.nullValue.map(MetricsValue)))).toMap

    val newMetricValues = (snapshot.metrics.counters ++ snapshot.metrics.gauges)
      .collect {
        case SettingExistsMetricValue((item, value)) =>
          UpdateMetrics(item.key, Option(MetricsValue(value.value.toString)))
      }
    val newMetricDistributions = (snapshot.metrics.histograms ++ snapshot.metrics.rangeSamplers)
      .collect {
        case SettingExistsMetricDistribution((item, value)) =>
          val average =
            if (value.distribution.count === 0L) BigDecimal(0)
            else (BigDecimal(value.distribution.sum) / value.distribution.count).setScale(0, RoundingMode.HALF_DOWN)
          UpdateMetrics(item.key, Option(MetricsValue(average.bigDecimal.toPlainString)))
      }
    val allMetrics: Seq[UpdateMetrics] = newMetricValues ++ newMetricDistributions

    allMetrics
      .foldLeft(emptyMetrics) { (acc, metrics) =>
        acc.updated(metrics.key, metrics)
      }.foreach {
        case (_, command) =>
          actor ! command
      }
  }

  override def start(): Unit = {}

  override def stop(): Unit = {}

  override def reconfigure(config: Config): Unit = {
    settings = readSettings(config)
  }

  private def readSettings(config: Config): Set[SettingItem] = {
    val root = config.getConfig("lerna.management.stats.metrics-reporter")

    import collection.JavaConverters._
    val items = root.root().entrySet().asScala.toSet

    items.flatMap { item =>
      val key    = item.getKey.replaceFirst("^/", "")
      val config = root.getConfig(item.getKey)
      val tags: Map[String, String] =
        if (config.hasPath("tags")) {
          val tagsConfig = config.getConfig("tags")
          tagsConfig
            .entrySet().asScala
            .map(e => (e.getKey, tagsConfig.getString(e.getKey))).toMap
        } else {
          Map()
        }
      val nullValue: Option[String] =
        if (config.hasPath("null-value")) {
          Option(config.getString("null-value"))
        } else None

      def generateSettingItem(_tags: kamon.Tags, tenant: Option[Tenant]) =
        SettingItem(key = MetricsKey(key, tenant), name = config.getString("name"), tags = _tags, nullValue = nullValue)

      val tenantSettingItems = tenants.map { implicit tenant =>
        import MetricsMultiTenantSupport._
        generateSettingItem(tags.withTenant, Option(tenant))
      }

      // system-metrics など テナントに関係無いものとテナント別のものを別で管理する
      tenantSettingItems + generateSettingItem(tags, tenant = None)
    }
  }

}
