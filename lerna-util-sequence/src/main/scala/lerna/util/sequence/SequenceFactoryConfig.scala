package lerna.util.sequence

import com.typesafe.config.Config
import lerna.util.tenant.Tenant
import lerna.util.time.JavaDurationConverters._

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

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

private[sequence] class SequenceFactoryCassandraConfig(baseConfig: Config)(implicit tenant: Tenant) {
  private[this] val cassandraConfig = baseConfig.getConfig(s"cassandra.tenants.${tenant.id}")

  val sessionProviderConfig: Config = cassandraConfig
  val readProfileName: String       = cassandraConfig.getString("read-profile")
  val writeProfileName: String      = cassandraConfig.getString("write-profile")

  val cassandraKeyspace: String = cassandraConfig.getString("keyspace")
  val cassandraTable: String    = cassandraConfig.getString("table")

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

}
