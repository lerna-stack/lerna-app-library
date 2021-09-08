package lerna.util.sequence

import akka.actor.ClassicActorSystemProvider
import com.datastax.oss.driver.api.core.CqlSession

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContextExecutor, Future }

private[sequence] object CqlSessionProvider {

  /** Connect to the Cassandra cluster and returns a [[Future]] instance containing [[CqlSession]].
    *
    * A driver configuration is resolved from the given [[ClassicActorSystemProvider]] and [[SequenceFactoryCassandraConfig]].
    * The connection initialization will be done asynchronously in a driver internal thread pool.
    */
  def connect(
      systemProvider: ClassicActorSystemProvider,
      config: SequenceFactoryCassandraConfig,
  ): Future[CqlSession] = {
    implicit val executionContext: ExecutionContextExecutor = systemProvider.classicSystem.dispatcher
    for {
      configLoader <- Future.fromTry(config.resolveDriverConfigLoader(systemProvider))
      session      <- CqlSession.builder().withConfigLoader(configLoader).buildAsync().toScala
    } yield {
      session
    }
  }

}
