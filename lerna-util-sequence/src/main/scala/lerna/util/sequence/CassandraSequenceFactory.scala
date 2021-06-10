package lerna.util.sequence

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import akka.actor.ClassicActorSystemProvider
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import lerna.log.AppLogging
import lerna.util.tenant.Tenant

import scala.concurrent.{ ExecutionContextExecutor, Future }

/** A sequence factory using Cassandra
  *
  * ==Overview==
  *
  *  - Manage sequences using (`seqId`,`subId`) as a key
  *  - Reduce overhead by generating and persisting sequence beforehand
  *  - For each key (`seqId`,`subId`), hold the next IDs in memory
  *
  * `seqID` concept is introduced by this implementation.
  * It can be thought of as a sub-namespace.
  *
  * ==Details==
  * Define an increment value as max server count (name it `n`) beforehand.
  * Assign the different initial values (that is in the range [1 to `n`]) to each server.
  * We can generate a unique ID using the above condition.
  *
  * For example, suppose we have 3 servers initially and scale it out to 5 servers in the future.
  *
  * Assign initial values as follows
  *  - ''Server A'': (Assign an initial value = 1)
  *  - ''Server B'': (Assign an initial value = 2)
  *  - ''Server C'': (Assign an initial value = 3)
  *
  * Each server generates a sequence like the below
  *  - ''Server A'' generates a sequence 1, 6, 11, ..., `Ai = 1 + n * i`
  *  - ''Server B'' generates a sequence 2, 7, 12, ..., `Bi = 2 + n * i`
  *  - ''Server C'' generates a sequence 3, 8, 13, ..., `Ci = 3 + n * i`
  *
  * In the future, we can add two servers (named ''Server D'' and ''Server E'').
  *  - ''Server D'' is assigned 4 as an initial value.
  * It generates a sequence 4, 9, 14, ..., `Di = 4 + n * i`.
  *  - ''Server E'' is assigned 5 as an initial value.
  * It generates a sequence 5, 10, 15, ..., `Ei = 5 + n * i`.
  *
  * Therefore, as we see the above, we can generate unique IDs.
  */
@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.Null",
  ),
)
abstract class CassandraSequenceFactory extends SequenceFactory with AppLogging {

  /** The actor system that is used for creating internal actors
    */
  val system: ClassicActorSystemProvider

  /** The configuration that is used for reading settings
    */
  val config: Config

  /** The sequence ID for this factory
    * @return The sequence ID
    */
  def seqId: String

  /** The number of IDs that the factory generates before it receives a generating request
    */
  def sequenceCacheSize: Int

  /** List of supported tenants.
    *
    * Used to create SequenceFactorySupervisor at initialization.
    * If a numbering request is made for a tenant not included in this list, `Future.failed(new IllegalArgumentException)` will be returned.
    */
  def supportedTenants: Seq[Tenant]

  private[this] val sequenceConfig = new SequenceFactoryConfig(config)

  private[this] implicit val generateTimeout: Timeout = Timeout(sequenceConfig.generateTimeout)

  private[this] implicit def ec: ExecutionContextExecutor = system.classicSystem.dispatcher

  private[this] def encode(str: String) = URLEncoder.encode(str, StandardCharsets.UTF_8.name)

  private[this] val sequenceFactoryMap = supportedTenants.map { implicit tenant =>
    val actor = system.classicSystem.actorOf(
      SequenceFactorySupervisor
        .props(seqId, maxSequenceValue = maxSequence, reservationAmount = sequenceCacheSize),
      name = encode(s"SequenceFactory-$seqId-${tenant.id}"),
    )

    tenant -> actor
  }.toMap

  override def nextId(subId: Option[String])(implicit tenant: Tenant): Future[BigInt] = {
    sequenceFactoryMap
      .get(tenant)
      .map { sequenceFactory =>
        (sequenceFactory ? SequenceFactoryWorker.GenerateSequence(subId))
          .mapTo[SequenceFactoryWorker.SequenceGenerated].map(_.value)
      }
      .getOrElse {
        // `supportedTenants` の値は外部に漏れるとまずい場合があるので message に入れていない
        Future.failed(new IllegalArgumentException(s"tenant(${tenant.id}) must be included in supportedTenants"))
      }
  }
}
