# Lerna Management

*Lerna Management* library provides management related features like below.

- Metrics reporter for [Kamon](https://kamon.io/)

## Metrics

`Metrics` is a trait that provides metrics reporting feature for *Kamon*.
This is useful when making our APIs providing metrics collected by *Kamon*.
We can use an instance of `Metrics` like the following.

```scala mdoc:compile-only
import lerna.management.stats.{ Metrics, MetricsKey, MetricsValue }
import lerna.util.tenant.Tenant
import akka.actor.ActorSystem
import kamon.Kamon
import scala.concurrent.Future

val system = ActorSystem("metrics-example")
val exampleTenant = new Tenant { def id = "example" }
val tenants: Set[Tenant] = Set(exampleTenant)

// We have to instantiate `Metrics` before calling `Kamon.init`.
val metrics = Metrics(system, tenants)
// Substitute the argument of `Kamon.init` if we have another config we want to use.
Kamon.init(system.settings.config)

// We can get metrics collected by Kamon via `Metrics.getMetrics`.
// NOTE: We have to configure which metrics will be collected in a configuration file.
val metricValue: Future[Option[MetricsValue]] =
  metrics.getMetrics(MetricsKey("system-metrics/metrics/jvm-memory/heap/used", None))
```

The *lerna-management* metric key name (`system-metrics/metrics/jvm-memory/heap/used` in this example) should be defined at `lerna.management.stats.metrics-reporter` in `application.conf`.
We can see examples in [reference.conf](../lerna-management/src/main/resources/reference.conf).

### Multi-tenancy metric values
We can collect the metric values of each tenant.
The following example illustrates how to collect multi-tenancy metric values.

```scala mdoc:compile-only
import lerna.management.stats.{ Metrics, MetricsKey, MetricsValue }
import lerna.management.stats.MetricsMultiTenantSupport._
import lerna.util.tenant.Tenant
import akka.actor.ActorSystem
import kamon.Kamon
import kamon.tag.TagSet
import scala.concurrent.Future

val system = ActorSystem("metrics-example")

// Define our tenants
val exampleTenant = new Tenant { def id = "example" }
val tenants: Set[Tenant] = Set(exampleTenant)

// Initialize
val metrics = Metrics(system, tenants)
Kamon.init(system.settings.config)

// We have to collect metric values tagged with a tenant ID.
// This is achieved by containing the tenant ID to `TagSet`.
// We can use the `withTenant` extension method as below.
// Note that we need to import `MetricsMultiTenantSupport._` to use the extension method.
val tags = TagSet.of("component", "custom").withTenant(exampleTenant)
val counter = Kamon.counter("my.custom.kamon-metric-name").withTags(tags)
counter.increment()
counter.increment()

// Once we have collected metric values of the tenant,
// we can get the values by calling `Metrics.getMetrics` with the tenant.
val metricValueWithTenant: Future[Option[MetricsValue]] =
  metrics.getMetrics(MetricsKey("my-custom-metric-key-name", Option(exampleTenant)))
```
