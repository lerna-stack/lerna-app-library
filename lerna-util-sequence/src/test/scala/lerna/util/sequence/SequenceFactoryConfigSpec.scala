package lerna.util.sequence

import com.typesafe.config.{ Config, ConfigFactory }
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant
import org.scalatest.TryValues

import scala.concurrent.duration._

object SequenceFactoryConfigSpec {

  private val DefaultExecutionProfileName: String = "lerna-util-sequence-profile"
  private val DefaultKeyspaceName: String         = "sequence"
  private val DefaultTableName: String            = "sequence_reservation"

  private object DefaultExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "default-example"
    }
  }
  private object OverrideExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "override-example"
    }
    val readProfileName  = "my-read-profile"
    val writeProfileName = "my-write-profile"
    val keyspaceName     = "my_sequence"
    val tableName        = "my_sequence_reservation"
  }

  private val config: Config = ConfigFactory
    .parseString(s"""
                    |akka.actor {
                    |  provider = local
                    |}
                    |lerna.util.sequence {
                    |  cassandra.tenants.${DefaultExample.tenant.id} = $${lerna.util.sequence.cassandra.default}
                    |  cassandra.tenants.${OverrideExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    read-profile = "${OverrideExample.readProfileName}"
                    |    write-profile = "${OverrideExample.writeProfileName}"
                    |    keyspace = "${OverrideExample.keyspaceName}"
                    |    table = "${OverrideExample.tableName}"
                    |  }
                    |}
                    """.stripMargin)
    .withFallback(ConfigFactory.defaultReferenceUnresolved())
    .resolve()

}

@SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
final class SequenceFactoryConfigSpec
    extends ScalaTestWithTypedActorTestKit(SequenceFactoryConfigSpec.config)
    with LernaBaseSpec
    with TryValues {

  import SequenceFactoryConfigSpec._

  "default SequenceFactoryConfig" in {

    val sequenceFactoryConfig = new SequenceFactoryConfig(system.settings.config)
    expect(sequenceFactoryConfig.nodeId === 1)
    expect(sequenceFactoryConfig.maxNodeId === 9)
    expect(sequenceFactoryConfig.generateTimeout === 10.seconds)
    expect(sequenceFactoryConfig.workerIdleTimeout === 10.seconds)

  }

  "SequenceFactoryCassandraConfig has default values" in {

    val cassandraConfig = new SequenceFactoryConfig(system.settings.config).cassandraConfig(DefaultExample.tenant)
    expect(cassandraConfig.readProfileName === DefaultExecutionProfileName)
    expect(cassandraConfig.writeProfileName === DefaultExecutionProfileName)
    expect(cassandraConfig.cassandraKeyspace === DefaultKeyspaceName)
    expect(cassandraConfig.cassandraTable === DefaultTableName)

  }

  "SequenceFactoryCassandraConfig can be overwritten" in {

    // Overwritten values should not equal the default.
    expect(OverrideExample.readProfileName !== DefaultExecutionProfileName)
    expect(OverrideExample.writeProfileName !== DefaultExecutionProfileName)
    expect(OverrideExample.readProfileName !== OverrideExample.writeProfileName)
    expect(OverrideExample.keyspaceName !== DefaultKeyspaceName)
    expect(OverrideExample.tableName !== DefaultTableName)

    val cassandraConfig = new SequenceFactoryConfig(system.settings.config).cassandraConfig(OverrideExample.tenant)
    expect(cassandraConfig.readProfileName === OverrideExample.readProfileName)
    expect(cassandraConfig.writeProfileName === OverrideExample.writeProfileName)
    expect(cassandraConfig.cassandraKeyspace === OverrideExample.keyspaceName)
    expect(cassandraConfig.cassandraTable === OverrideExample.tableName)

  }

}
