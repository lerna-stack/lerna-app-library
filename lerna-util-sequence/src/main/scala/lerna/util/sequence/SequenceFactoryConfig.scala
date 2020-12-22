package lerna.util.sequence

import com.datastax.driver.core._
import com.datastax.driver.core.policies._
import com.typesafe.config.Config
import lerna.util.tenant.Tenant
import lerna.util.time.JavaDurationConverters._

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

private[sequence] final class SequenceFactoryConfig(root: Config) {

  val config: Config = root.getConfig("lerna.util.sequence")

  val nodeId: Int = {
    val id = config.getInt("node-id")
    require(id > 0) // シリーズIDは生成するシーケンスの初項になる
    id
  }

  val firstValue: Int = nodeId

  val maxNodeId: Int = {
    val number = config.getInt("max-node-id")
    require(number > 0)
    number
  }

  val incrementStep: Int = maxNodeId

  val generateTimeout: FiniteDuration = config.getDuration("generate-timeout").asScala

  val workerIdleTimeout: FiniteDuration = config.getDuration("worker.idle-timeout").asScala

  def cassandraConfig(implicit tenant: Tenant) = new SequenceFactoryCassandraConfig(config)
}

class SequenceFactoryCassandraConfig(baseConfig: Config)(implicit tenant: Tenant) {
  private[this] val cassandraConfig = baseConfig.getConfig(s"cassandra.tenants.${tenant.id}")

  val cassandraContactPoints: Seq[String] = cassandraConfig.getStringList("contact-points").asScala

  val cassandraKeyspace: String = cassandraConfig.getString("keyspace")

  val cassandraTable: String = cassandraConfig.getString("table")

  val cassandraWriteConsistency: ConsistencyLevel =
    ConsistencyLevel.valueOf(cassandraConfig.getString("write-consistency"))

  val cassandraReadConsistency: ConsistencyLevel =
    ConsistencyLevel.valueOf(cassandraConfig.getString("read-consistency"))

  def cassandraReplication: String = {
    s"""
     |'class' : 'NetworkTopologyStrategy',
     |$cassandraDataCenterReplicationFactors
     """.stripMargin
  }

  /** [dc0:3, dc1:3] => 'dc0':3, 'dc1':3
    */
  def cassandraDataCenterReplicationFactors: String =
    cassandraConfig
      .getStringList("data-center-replication-factors").asScala
      .map { dataCenterWithReplicationFactor =>
        dataCenterWithReplicationFactor.split(":") match {
          case Array(dataCenter, replicationFactor) =>
            s"'$dataCenter':$replicationFactor"
          case _ =>
            throw new IllegalArgumentException(
              s"data-center-replication-factors は [データセンター名:レプリケーションファクター] の形式で設定してください: $dataCenterWithReplicationFactor",
            )
        }
      }.mkString(",")

  val cassandraUsername: String = cassandraConfig.getString("authentication.username")

  val cassandraPassword: String = cassandraConfig.getString("authentication.password")

  private[this] def cassandraSocketOptions: SocketOptions = {
    val connectionTimeout =
      cassandraConfig.getDuration("socket.connection-timeout").asScala
    val readTimeout =
      cassandraConfig.getDuration("socket.read-timeout").asScala

    new SocketOptions()
      .setConnectTimeoutMillis(connectionTimeout.toMillis.toInt)
      .setReadTimeoutMillis(readTimeout.toMillis.toInt)
  }

  private[this] def cassandraLoadBalancingPolicy: Option[LoadBalancingPolicy] = {
    val localDatacenter = cassandraConfig.getString("local-datacenter")
    if (localDatacenter.isEmpty) None
    else
      Option {
        new TokenAwarePolicy(
          DCAwareRoundRobinPolicy
            .builder()
            .withLocalDc(localDatacenter)
            .build(),
        )
      }
  }

  def cassandraReadRetryPolicy: RetryPolicy = {
    new LoggingRetryPolicy(new FixedRetryPolicy(cassandraConfig.getInt("read-retries")))
  }

  def cassandraWriteRetryPolicy: RetryPolicy = {
    new LoggingRetryPolicy(new FixedRetryPolicy(cassandraConfig.getInt("write-retries")))
  }

  def buildCassandraClusterConfig(): Cluster = {

    val builder =
      Cluster
        .builder()
        .withSocketOptions(cassandraSocketOptions)
        // DataStax 4.x が依存している io.dropwizard.metrics:metrics-core:4.0.5 では JMXが別モジュールになっているため
        // JMXを無効にする必要がある。有効にしたい場合は、次のURLを参考にして依存ライブラリを追加する必要がある。
        // https://docs.datastax.com/en/developer/java-driver/4.5/manual/core/metrics/#jmx
        .withoutJMXReporting()

    if (!cassandraUsername.isEmpty) {
      builder.withCredentials(cassandraUsername, cassandraPassword)
    }

    cassandraLoadBalancingPolicy.foreach { policy =>
      builder.withLoadBalancingPolicy(policy)
    }

    cassandraContactPoints
      .foldLeft(builder) { (builder, contact) =>
        builder.addContactPoint(contact)
      }.build()
  }
}
