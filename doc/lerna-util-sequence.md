# Lerna Util Sequence

*Lerna Util Sequence* library provides an ID generator.

## SequenceFactory

`SequenceFactory` is a trait that defines sequence factory (ID generator) API.  
For more details about this trait, see a Scaladoc.

## CassandraSequenceFactory

`CassandraSequenceFactory` is an implementation of `SequenceFactory`.
This abstract class uses *Cassandra* as a persistent store and generates unique IDs scalably.
We can use this class like below code.
Moreover, you need to write your own configuration.
For more details, see a Scaladoc and `reference.conf` of the library.

```scala mdoc:compile-only
import lerna.util.sequence.CassandraSequenceFactory
import lerna.util.tenant.Tenant
import akka.actor.ActorSystem
import com.typesafe.config.Config

class MySequenceFactory(val config: Config, val system: ActorSystem)
    extends CassandraSequenceFactory{
  final override def seqId: String = "SEQ"
  final override def sequenceCacheSize: Int = 10
  final override def maxSequence: BigInt = Int.MaxValue

  override def supportedTenants: Seq[Tenant] = ???
}
```
