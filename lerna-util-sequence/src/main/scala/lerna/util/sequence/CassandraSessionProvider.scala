package lerna.util.sequence

import akka.actor.{ ClassicActorSystemProvider, ExtendedActorSystem }
import akka.stream.alpakka.cassandra.{ CqlSessionProvider, DriverConfigLoaderFromConfig }
import com.datastax.oss.driver.api.core.CqlSession
import com.typesafe.config.ConfigException

import scala.collection.SortedSet
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

private[sequence] object CassandraSessionProvider {

  /** Connect to the Cassandra cluster and returns a [[Future]] instance containing [[CqlSession]].
    *
    * A driver configuration is resolved from the given [[ClassicActorSystemProvider]] and [[SequenceFactoryCassandraConfig]].
    * The connection initialization will be done asynchronously in a driver internal thread pool.
    *
    * Return a [[scala.util.Failure]] if connecting to the cluster failed.
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def connect(
      system: ClassicActorSystemProvider,
      config: SequenceFactoryCassandraConfig,
  )(implicit executionContext: ExecutionContext): Future[CqlSession] = {
    for {
      validatedConfig <- Future.fromTry(validateConfig(system, config))
      sessionProvider <- Future {
        CqlSessionProvider(
          system.classicSystem.asInstanceOf[ExtendedActorSystem],
          validatedConfig.sessionProviderConfig,
        )
      }
      session <- sessionProvider.connect()
    } yield {
      session
    }
  }

  /** Validate the given [[SequenceFactoryCassandraConfig]].
    *
    * Ensure the following
    *  - `datastax-java-driver-config` exists
    *  - `read-profile` exists
    *  - `write-profile` exists
    *
    * Return a [[scala.util.Failure]] if the config is invalid.
    */
  private def validateConfig(
      system: ClassicActorSystemProvider,
      config: SequenceFactoryCassandraConfig,
  ): Try[SequenceFactoryCassandraConfig] = Try {
    try {
      val driverConfig          = CqlSessionProvider.driverConfig(system.classicSystem, config.sessionProviderConfig)
      val configLoader          = DriverConfigLoaderFromConfig.fromConfig(driverConfig)
      val profiles              = configLoader.getInitialConfig.getProfiles
      val isReadProfileMissing  = !profiles.containsKey(config.readProfileName)
      val isWriteProfileMissing = !profiles.containsKey(config.writeProfileName)
      if (isReadProfileMissing || isWriteProfileMissing) {
        val driverConfigPath = config.sessionProviderConfig.getString("datastax-java-driver-config")
        val readProfilePathCandidates = SortedSet(
          s"${driverConfigPath}.profiles.${config.readProfileName}",
          s"datastax-java-driver.profiles.${config.readProfileName}",
        ).mkString("[", ",", "]")
        val writeProfilePathCandidates = SortedSet(
          s"${driverConfigPath}.profiles.${config.writeProfileName}",
          s"datastax-java-driver.profiles.${config.writeProfileName}",
        ).mkString("[", ",", "]")
        throw new IllegalArgumentException(s"""
             |The driver execution profile is missing.
             |Read Profile: path="${readProfilePathCandidates}", missing=${isReadProfileMissing.toString}
             |Write Profile: path="${writeProfilePathCandidates}", missing=${isWriteProfileMissing.toString}
             |""".stripMargin)
      }
      config
    } catch {
      case missing: ConfigException.Missing =>
        throw new IllegalArgumentException(s"Could not resolve the DataStax Java Driver config", missing)
      case wrongType: ConfigException.WrongType =>
        throw new IllegalArgumentException(s"Could not resolve the DataStax Java Driver config", wrongType)
    }
  }

}
