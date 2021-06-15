package lerna.management.stats

import akka.actor.{ typed, ActorSystem }
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import com.typesafe.config.Config
import kamon.Kamon
import lerna.util.lang.Equals._
import kamon.metric.{ Distribution, MetricSnapshot, PeriodSnapshot }
import kamon.tag.TagSet
import lerna.management.stats.MetricsActor.{ GetMetrics, UpdateMetrics }
import lerna.util.tenant.Tenant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode

private[stats] final case class SettingItem(key: MetricsKey, name: String, tags: TagSet, nullValue: Option[String])

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
  ),
)
private[stats] class MetricsImpl(system: ActorSystem, tenants: Set[Tenant]) extends Metrics {
  implicit private val typedSystem: typed.ActorSystem[Nothing] = system.toTyped

  private val kamonModuleName            = "lerna-app-library-metrics"
  private var settings: Set[SettingItem] = readSettings(system.settings.config)
  private val actor                      = typedSystem.systemActorOf(MetricsActor(), name = this.getClass.getName)

  implicit lazy val timeout: Timeout = Timeout(3.seconds)

  Kamon.registerModule(kamonModuleName, this)

  override def getMetrics(key: MetricsKey): Future[Option[MetricsValue]] = {
    actor.ask(replyTo => GetMetrics(key, replyTo))
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val emptyMetrics: Map[MetricsKey, UpdateMetrics] =
      settings
        .map(e => (e.key, UpdateMetrics(e.key, e.nullValue.map(MetricsValue)))).toMap

    val allMetrics: Seq[UpdateMetrics] =
      snapshot.counters.flatMap(collectUpdateMetricsFromValues) ++
      snapshot.gauges.flatMap(collectUpdateMetricsFromValues) ++
      snapshot.histograms.flatMap(collectUpdateMetricsFromDistributions) ++
      snapshot.rangeSamplers.flatMap(collectUpdateMetricsFromDistributions)

    allMetrics
      .foldLeft(emptyMetrics) { (acc, metrics) =>
        acc.updated(metrics.key, metrics)
      }.foreach {
        case (_, command) =>
          actor ! command
      }
  }

  private[this] def findSetting(name: String, tags: TagSet): Option[SettingItem] = {
    settings.find(s => s.name === name && s.tags === tags)
  }

  private[this] def collectUpdateMetricsFromValues[T](values: MetricSnapshot.Values[T]): Seq[UpdateMetrics] = {
    values.instruments.flatMap { instrument =>
      findSetting(values.name, instrument.tags)
        .map { setting =>
          UpdateMetrics(setting.key, Option(MetricsValue(instrument.value.toString)))
        }
    }
  }

  private[this] def collectUpdateMetricsFromDistributions(
      distributions: MetricSnapshot.Distributions,
  ): Seq[UpdateMetrics] = {
    def averageOf(distribution: Distribution): BigDecimal = {
      if (distribution.count === 0L) BigDecimal(0)
      else (BigDecimal(distribution.sum) / distribution.count).setScale(0, RoundingMode.HALF_DOWN)
    }
    distributions.instruments.flatMap { instrument =>
      findSetting(distributions.name, instrument.tags)
        .map { setting =>
          val average = averageOf(instrument.value)
          UpdateMetrics(setting.key, Option(MetricsValue(average.bigDecimal.toPlainString)))
        }
    }
  }

  override def stop(): Unit = {}

  override def reconfigure(config: Config): Unit = {
    settings = readSettings(config)
  }

  private def readSettings(config: Config): Set[SettingItem] = {
    val root = config.getConfig("lerna.management.stats.metrics-reporter")

    import scala.jdk.CollectionConverters._
    val items = root.root().entrySet().asScala.toSet

    items.flatMap { item =>
      val key    = item.getKey.replaceFirst("^/", "")
      val config = root.getConfig(item.getKey)
      val tagSet: TagSet = {
        val tags: Map[String, String] =
          if (config.hasPath("tags")) {
            val tagsConfig = config.getConfig("tags")
            tagsConfig
              .entrySet().asScala
              .map(e => (e.getKey, tagsConfig.getString(e.getKey))).toMap
          } else {
            Map()
          }
        TagSet.from(tags)
      }
      val nullValue: Option[String] =
        if (config.hasPath("null-value")) {
          Option(config.getString("null-value"))
        } else None

      def generateSettingItem(_tags: kamon.tag.TagSet, tenant: Option[Tenant]) =
        SettingItem(key = MetricsKey(key, tenant), name = config.getString("name"), tags = _tags, nullValue = nullValue)

      val tenantSettingItems = tenants.map { implicit tenant =>
        import MetricsMultiTenantSupport._
        generateSettingItem(tagSet.withTenant, Option(tenant))
      }

      // system-metrics など テナントに関係無いものとテナント別のものを別で管理する
      tenantSettingItems + generateSettingItem(tagSet, tenant = None)
    }
  }

}
