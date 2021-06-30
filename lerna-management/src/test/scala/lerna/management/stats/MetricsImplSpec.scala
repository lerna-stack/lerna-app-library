package lerna.management.stats

import akka.actor.{ ActorSystem, Scheduler }
import akka.pattern.retry
import com.typesafe.config.{ Config, ConfigFactory }
import kamon.Kamon
import kamon.metric.MeasurementUnit
import kamon.tag.TagSet
import lerna.management.LernaManagementActorBaseSpec
import lerna.util.tenant.Tenant
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time._

import scala.concurrent.Await
import scala.concurrent.duration._

object MetricsImplSpec {
  val config: Config =
    ConfigFactory
      .parseString(s"""
                               |kamon {
                               |  metric {
                               |    tick-interval = 10 seconds
                               |  }
                               |}
                               |lerna.management.stats {
                               |  metrics-reporter {
                               |
                               |    /lerna/counters/example {
                               |      name = "lerna.counters.example"
                               |      tags {
                               |        category = "counter-example"
                               |      }
                               |    }
                               |
                               |    /lerna/gauges/example {
                               |      name = "lerna.gauges.example"
                               |      tags {
                               |        category = "gauge-example"
                               |      }
                               |    }
                               |
                               |    /lerna/histograms/example {
                               |      name = "lerna.histograms.example"
                               |      tags {
                               |        category = "histogram-example"
                               |      }
                               |    }
                               |
                               |    /lerna/range-samplers/example {
                               |      name = "lerna.range-samplers.example"
                               |      tags {
                               |        category = "range-sampler-example"
                               |      }
                               |    }
                               |
                               |    /system-metrics/jvm-memory/heap/used {
                               |      name = "jvm.memory.used"
                               |      tags {
                               |        component = "jvm"
                               |        region    = "heap"
                               |      }
                               |    }
                               |
                               |    /system-metrics/jvm-memory/heap/max {
                               |      name = "jvm.memory.max"
                               |      tags {
                               |        component = "jvm"
                               |        region    = "heap"
                               |      }
                               |    }
                               |
                               |    /not-exits-value {
                               |      name = "dummy"
                               |      null-value = "0"
                               |    }
                               |
                               |    /key_tenant {
                               |      name = "name_tenant"
                               |      tags {
                               |        component = "test"
                               |      }
                               |    }
                               |  }
                               |}
       """.stripMargin).withFallback(ConfigFactory.load())
}

@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.OptionPartial",
    "lerna.warts.Awaits",
  ),
)
class MetricsImplSpec extends LernaManagementActorBaseSpec(ActorSystem("MetricsImplSpec", MetricsImplSpec.config)) {
  implicit val scheduler: Scheduler = system.scheduler
  import system.dispatcher

  val tenant1: Tenant = new Tenant {
    override def id: String = "dummy-1"
  }
  val tenant2: Tenant = new Tenant {
    override def id: String = "dummy-2"
  }

  val metricsImpl: Metrics = new MetricsImpl(system, tenants = Set(tenant1, tenant2))

  override def beforeAll(): Unit = {
    super.beforeAll()
    Kamon.init(MetricsImplSpec.config)
  }

  override def afterAll(): Unit = {
    try {
      Await.result(Kamon.stop(), scaled(10.seconds))
    } finally {
      super.afterAll()
    }
  }

  "MetricsImpl.reconfigure" should {
    "configure itself again" in {

      val noReporterConfig = ConfigFactory.parseString("""
          |kamon {
          |  metric {
          |    tick-interval = 3 seconds
          |  }
          |}
          |lerna.management.stats {
          |  metrics-reporter {
          |  }
          |}
          |""".stripMargin)
      metricsImpl.reconfigure(noReporterConfig)

      // 10 seconds > 50 * 100 millis となるように設定する
      implicit val patienceConfig: PatienceConfig = this.patienceConfig.copy(scaled(Span(10, Seconds)))
      val attempts                                = 50
      val delay                                   = scaled(100.millis)
      val key                                     = MetricsKey("system-metrics/jvm-memory/heap/max", None)

      val expectingFailure = retry(() => metricsImpl.getMetrics(key).map(_.get), attempts, delay).failed.futureValue
      expectingFailure shouldBe a[NoSuchElementException]

      // 設定をもとに戻す処理をおこなう、
      // reconfigure にバグがある場合は、このテスト以外も失敗する
      metricsImpl.reconfigure(system.settings.config)
    }
  }

  "Get Metrics from Kamon" should {

    val timeout  = Timeout(scaled(Span(30, Seconds)))
    val attempts = 30
    val delay    = scaled(1000.millis)

    "collect metric values of counters as Long values" in {
      val name     = "lerna/counters/example"
      val kamonKey = "lerna.counters.example"
      val key      = MetricsKey(name, None)
      val tags = TagSet.from(
        Map(
          "category" -> "counter-example",
        ),
      )
      val counter = Kamon.counter(kamonKey).withTags(tags)

      counter.increment(123)
      whenReady(
        retry(() => metricsImpl.getMetrics(key).map(_.get), attempts, delay),
        timeout,
      ) { metrics =>
        expect(metrics.value.toLong === 123L)
      }
    }

    "collect metric values of gauges as Double values" in {
      val name     = "lerna/gauges/example"
      val kamonKey = "lerna.gauges.example"
      val key      = MetricsKey(name, None)
      val tags = TagSet.from(
        Map(
          "category" -> "gauge-example",
        ),
      )
      val gauge = Kamon.gauge(kamonKey).withTags(tags)

      gauge.update(1.23)
      whenReady(
        retry(() => metricsImpl.getMetrics(key).map(_.get), attempts, delay),
        timeout,
      ) { metrics =>
        expect(metrics.value.toDouble === 1.23)
      }
    }

    "collect average metric values of histograms as Long values" in {
      val name     = "lerna/histograms/example"
      val kamonKey = "lerna.histograms.example"
      val key      = MetricsKey(name, None)
      val tags = TagSet.from(
        Map(
          "category" -> "histogram-example",
        ),
      )
      val histogram = Kamon.histogram(kamonKey).withTags(tags)

      histogram.record(1)
      histogram.record(4)
      whenReady(
        retry(() => metricsImpl.getMetrics(key).map(_.get), attempts, delay),
        timeout,
      ) { metrics =>
        val expectedValue: Long = (1 + 4) / 2
        expect(metrics.value.toLong === expectedValue)
      }
    }

    "collect metric values of rangeSamplers as Long values" in {
      val name     = "lerna/range-samplers/example"
      val kamonKey = "lerna.range-samplers.example"
      val key      = MetricsKey(name, None)
      val tags = TagSet.from(
        Map(
          "category" -> "range-sampler-example",
        ),
      )
      val rangeSampler = Kamon.rangeSampler(kamonKey).withTags(tags)

      rangeSampler.increment(3)
      rangeSampler.increment(2)
      whenReady(
        retry(() => metricsImpl.getMetrics(key).map(_.get), attempts, delay),
        timeout,
      ) { metrics =>
        val expectedValue: Long = 5
        expect(metrics.value.toLong === expectedValue)
      }
    }

    "jvm_heap_used" in {
      val key = MetricsKey("system-metrics/jvm-memory/heap/used", None)
      whenReady(
        retry(() => metricsImpl.getMetrics(key).map(_.get), attempts, delay),
        timeout,
      ) { metrics =>
        expect(metrics.value.toLong > 0)
      }
    }

    "jvm_heap_max" in {
      val key = MetricsKey("system-metrics/jvm-memory/heap/max", None)
      whenReady(
        retry(() => metricsImpl.getMetrics(key).map(_.get), attempts, delay),
        timeout,
      ) { metrics =>
        expect(metrics.value.toDouble > 0)
      }
    }

    "not-exits-value" in {
      val key = MetricsKey("not-exits-value", None)
      whenReady(
        retry(() => metricsImpl.getMetrics(key).map(_.get), attempts, delay),
        timeout,
      ) { metrics =>
        expect(metrics.value === "0")
      }
    }

    "記録したテナントのみで `Some(記録した値)` が返る" in {
      val value = 123

      import MetricsMultiTenantSupport._
      val histogram = Kamon
        .histogram("name_tenant", MeasurementUnit.none)
        .withTags(TagSet.from(Map("component" -> "test")).withTenant(tenant1))

      histogram.record(value)

      val key1 = MetricsKey("key_tenant", Option(tenant1))
      whenReady(
        retry(() => metricsImpl.getMetrics(key1).map(_.get), attempts, delay),
        timeout,
      ) { metrics =>
        expect(metrics.value === value.toString)
      }

      val key2 = MetricsKey("key_tenant", Option(tenant2))
      whenReady(metricsImpl.getMetrics(key2)) { maybeMetricsValue =>
        expect(maybeMetricsValue === None)
      }

      val key3 = MetricsKey("key_tenant", None)
      whenReady(metricsImpl.getMetrics(key3)) { maybeMetricsValue =>
        expect(maybeMetricsValue === None)
      }
    }
  }

}
