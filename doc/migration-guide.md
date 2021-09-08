# Migration Guide

## 3.0.0 from 2.0.0

### lerna-util-sequence

#### Settings

*lerna-util-sequence 3.0.0* depend on [Alpakka Cassandra 2.0.2](https://doc.akka.io/docs/alpakka/2.0.2/cassandra.html)
instead of [DataStax Java Driver for Cassandra 3.7.1](https://docs.datastax.com/en/developer/java-driver/3.7/).
This change includes the DataStax Java Driver upgrade to [4.6.1](https://docs.datastax.com/en/developer/java-driver/4.6/).
By upgrading the driver, we can now utilize the driver's new configuration mechanism.
Understanding the configuration mechanism is required to migrate settings.
Before migrating, please read the [DataStax Java Driver - Configuration](https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/configuration/).

* `lerna.util.sequence.cassandra.default.contact-points`  
  This setting is the contact points to use for the initial connection.  
  Move this setting value to `datastax-java-driver.basic.contact-points`.  
  See also [DataStax Java Driver - Core driver](https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/#contact-points).
* `lerna.util.sequence.cassandra.default.authentication`  
  This setting is authentication credentials to use for connecting the cluster.  
  Use `datastax-java-driver.advanced.auth-provider` instead.  
  See also [DataStax Java Driver - Authentication](https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/authentication/).
* `lerna.util.sequence.cassandra.default.socket.connection-timeout`  
  This setting is the timeout to use for establishing driver connections.  
  Move this setting value to `datastax-java-driver.advanced.connection.connect-timeout`.  
  See also [DataStax Java Driver - Reference configuration](https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/configuration/reference/).
* `lerna.util.sequence.cassandra.default.socket.read-timeout`  
  This setting is how long the driver waits for a response from a Cassandra node.  
  Use `datastax-java-driver.profiles.lerna-util-sequence-profile.basic.request.timeout` instead.  
  Note that the new setting is not the exact same as the old setting.  
  See also [DataStax Java Driver - Upgrade guide](https://docs.datastax.com/en/developer/java-driver/4.6/upgrade_guide/#statements).
* `lerna.util.sequence.cassandra.default.local-datacenter`  
  This setting is the datacenter considered as "local".  
  The driver will query only the nodes on the datacenter.  
  Use `datastax-java-driver.profiles.lerna-util-sequence-profile.basic.load-balancing-policy` instead.  
  See also [DataStax Java Driver - Load balancing](https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/load_balancing/).


We can move the following two setting values to `datastax-java-driver.profiles.lerna-util-sequence-profile.basic.request.consistency`.
* `lerna.util.sequence.cassandra.default.write-consistency`  
* `lerna.util.sequence.cassandra.default.read-consistency`  

We now use the same consistency level between reads and writes.
However, by using execution profiles, we can use different consistency level between reads and writes.
For more details, see the *lerna-util-sequence*'s configuration and [DataStax Java Driver - Configuration](https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/configuration/#execution-profiles).

We drop the following two retry settings.
Instead, we use the driver's built-in retry policy.
However, if we prefer to use a custom one, we can implement and configure it.
For more details, see [DataStax Java Driver - Retries](https://docs.datastax.com/en/developer/java-driver/4.6/manual/core/retries/).
* `lerna.util.sequence.cassandra.default.write-retries`
* `lerna.util.sequence.cassandra.default.read-retries`

#### Breaking API changes

* `lerna.util.sequence.SequenceFactoryCassandraConfig`  
  We make this class package-private.
  Since it would be implementation details, it should not be exposed to users.
* `lerna.util.sequence.FutureConverters`  
  We delete this class.
  Although the class was not providing any public APIs, the class itself was public.

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
