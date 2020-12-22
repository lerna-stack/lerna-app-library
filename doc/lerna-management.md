# Lerna Management

*Lerna Management* library provides management related features like below.

- Metrics provider for [Kamon](https://kamon.io/)

## Metrics

`Metrics` is a trait that provides metrics reporting feature for *Kamon*.
We can use an instance of`Metrics` like following.

```scala mdoc:compile-only
import lerna.management.stats.Metrics
import lerna.util.tenant.Tenant
import akka.actor.ActorSystem
import kamon.Kamon
import kamon.system.SystemMetrics

val system = ActorSystem("metrics-example")
val tenants: Set[Tenant] = ???
val reporter = Metrics(system, tenants)
Kamon.addReporter(reporter)
SystemMetrics.startCollecting()
```
