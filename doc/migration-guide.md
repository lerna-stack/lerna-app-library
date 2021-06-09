# Migration Guide

## 2.0.0 from 1.0.0

### lerna-testkit

#### Update ScalaTest to `3.1.0+`
*lerna-testkit 2.0.0* depends on *ScalaTest 3.1.0+*.
If we have used *ScalaTest 3.0.0+*, we have to update *ScalaTest* to *3.1.0+*.
This is because *ScalaTest 3.1.0* and *ScalaTest 3.0.0* are not binary compatible.

It may be quite simple to update ScalaTest 3.0.0+ to 3.1.0+
since we can grab the *ScalaFix* tool here([autofix/3.0.x at master · scalatest/autofix](https://github.com/scalatest/autofix/tree/master/3.0.x), [autofix/3.1.x at master · scalatest/autofix](https://github.com/scalatest/autofix/tree/master/3.1.x)).

### lerna-management

#### Initialization procedures are changed

Since *lerna-management* uses *Kamon 2.x*,
we have to change initialization procedures.
We should use `Kamon.init` to initialize *Kamon*.
We can see more details in [Migrating from 1.x to 2.0 | Kamon Documentation | Kamon](https://kamon.io/docs/latest/guides/migration/from-1.x-to-2.0/#there-is-a-new-kamoninit-method).
Be aware that we have to instantiate `Metrics` before calling `Kamon.init`.

```mdoc mdoc:compile-only
import lerna.management.stats.Metrics
import lerna.util.tenant.Tenant
import akka.actor.ActorSystem
import kamon.Kamon

val system: ActorSystem = ???
val tenants: Set[Tenant] = ???

// We have to instantiate `Metrics` before calling `Kamon.init`.
val metrics = Metrics(system, tenants)
// Substitute the argument of `Kamon.init` if we have another config we want to use.
Kamon.init(system.settings.config)
```

#### The value of `MetricsValue` may be changed

*Kamon* have 5 metric types.
- `Counters`
- `Gauges`
- `Histograms`
- `Timers` (not yet supported by lerna-management)
- `Range Samplers`

The type of `Gauge` metric values will be `Double` instead of `Long`.  
When we have used `Gauge` metric values and have published the value as `Long`,
we may need to reconcile the returned values from`Metrics.getMetrics` like below.

```mdoc mdoc:compile-only
import lerna.management.stats.{ Metrics, MetricsKey, MetricsValue }
import scala.concurrent.Future

val metrics: Metrics = ???

// Suppose we got a gauge metric value.
val gaugeMetricValue: Future[Option[MetricsValue]] =
  metrics.getMetrics(MetricsKey("system-metrics/jvm-memory/heap/max", None))

// We have to reconcile the returned value as we like.
// We convert the string value to Long via Double in this example.
val gaugeLongMetricValue: Future[Long] =
  gaugeMetricValue.map(_.map(_.toDouble.toLong).getOrElse(0))
```
