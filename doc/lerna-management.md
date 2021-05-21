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

// We have to register the instance of `Metrics` before calling `Kamon.init`.
val metrics = Metrics(system, tenants)
metrics.registerToKamon()
Kamon.init()

// We can get metrics collected by Kamon via `Metrics.getMetrics`.
// NOTE: We have to configure which metrics will be collected in a configuration file.
val metricValue: Future[Option[MetricsValue]] =
  metrics.getMetrics(MetricsKey("/system-metrics/metrics/jvm-memory/heap/used", None))
val metricValueWithTenant: Future[Option[MetricsValue]] =
  metrics.getMetrics(MetricsKey("/tenants/example/some-key", Option(exampleTenant)))
```

The metric keys used above should be defined at `lerna.management.stats.metrics-reporter` in `application.conf`.
We can see examples in [reference.conf](//lerna-management/src/main/resources/reference.conf).
