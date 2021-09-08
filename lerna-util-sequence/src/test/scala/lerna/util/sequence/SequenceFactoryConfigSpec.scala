package lerna.util.sequence

import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.typesafe.config.{ Config, ConfigException, ConfigFactory }
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant
import org.scalatest.TryValues

import scala.concurrent.duration._

object SequenceFactoryConfigSpec {

  private object DefaultExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "default-example"
    }
  }
  private object OverrideExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "override-example"
    }
    val driverConfigPath        = "datastax-java-driver-override-example"
    val readProfileName         = "my-read-profile"
    val writeProfileName        = "my-write-profile"
    val keyspaceName            = "my_sequence"
    val tableName               = "my_sequence_reservation"
    val readRequestConsistency  = "ALL"
    val writeRequestConsistency = "ONE"
  }
  private object MissingDriverConfig {
    val tenant: Tenant = new Tenant {
      override def id: String = "datastax-java-driver-missing-example"
    }
    val driverConfigPath = "datastax-java-driver-missing"
  }
  private object WrongDriverConfigType {
    val tenant: Tenant = new Tenant {
      override def id: String = "datastax-java-driver-wrong-type-example"
    }
    val driverConfigPath = "datastax-java-driver-wrong-type"
  }
  private object MissingReadProfileExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "missing-read-profile-example"
    }
    val readProfileName = "missing-read-profile"
    val readProfilePath = s"datastax-java-driver.profiles.${readProfileName}"
  }
  private object MissingWriteProfileExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "missing-write-profile-example"
    }
    val writeProfileName = "missing-write-profile"
    val writeProfilePath = s"datastax-java-driver.profiles.${writeProfileName}"
  }

  private val config: Config = ConfigFactory
    .parseString(s"""
                    |akka.actor {
                    |  provider = local
                    |}
                    |lerna.util.sequence {
                    |  cassandra.tenants.${DefaultExample.tenant.id} = $${lerna.util.sequence.cassandra.default}
                    |  cassandra.tenants.${OverrideExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    datastax-java-driver-config = "${OverrideExample.driverConfigPath}"
                    |    read-profile = "${OverrideExample.readProfileName}"
                    |    write-profile = "${OverrideExample.writeProfileName}"
                    |    keyspace = "${OverrideExample.keyspaceName}"
                    |    table = "${OverrideExample.tableName}"
                    |  }
                    |  cassandra.tenants.${MissingDriverConfig.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    datastax-java-driver-config = "${MissingDriverConfig.driverConfigPath}"
                    |  }
                    |  cassandra.tenants.${WrongDriverConfigType.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    datastax-java-driver-config = "${WrongDriverConfigType.driverConfigPath}"
                    |  }
                    |  cassandra.tenants.${MissingReadProfileExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    read-profile = "${MissingReadProfileExample.readProfileName}"
                    |  }
                    |  cassandra.tenants.${MissingWriteProfileExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    write-profile = "${MissingWriteProfileExample.writeProfileName}"
                    |  }
                    |}
                    |${OverrideExample.driverConfigPath} = $${datastax-java-driver} {
                    |  profiles {
                    |    ${OverrideExample.readProfileName} {
                    |      basic.request {
                    |        consistency = "${OverrideExample.readRequestConsistency}"
                    |      }
                    |    }
                    |    ${OverrideExample.writeProfileName} {
                    |      basic.request {
                    |        consistency = "${OverrideExample.writeRequestConsistency}"
                    |      }
                    |    }
                    |  }
                    |}
                    |${WrongDriverConfigType.driverConfigPath} = "Config type is wrong"
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

  "default SequenceFactoryCassandraConfig" in {

    val cassandraConfig = new SequenceFactoryConfig(system.settings.config).cassandraConfig(DefaultExample.tenant)
    expect(cassandraConfig.driverConfigPath === "datastax-java-driver")
    expect(cassandraConfig.readProfileName === "lerna-util-sequence-profile")
    expect(cassandraConfig.writeProfileName === "lerna-util-sequence-profile")
    expect(cassandraConfig.cassandraKeyspace === "sequence")
    expect(cassandraConfig.cassandraTable === "sequence_reservation")

    val driverConfigLoader = cassandraConfig.resolveDriverConfigLoader(system).success.value
    val driverConfig       = driverConfigLoader.getInitialConfig
    val executionProfile   = driverConfig.getProfile("lerna-util-sequence-profile")
    expect(
      executionProfile.getString(DefaultDriverOption.REQUEST_CONSISTENCY) === "QUORUM",
    )

  }

  "overridden SequenceFactoryCassandraConfig" in {

    val cassandraConfig = new SequenceFactoryConfig(system.settings.config).cassandraConfig(OverrideExample.tenant)
    expect(cassandraConfig.driverConfigPath === OverrideExample.driverConfigPath)
    expect(cassandraConfig.readProfileName === OverrideExample.readProfileName)
    expect(cassandraConfig.writeProfileName === OverrideExample.writeProfileName)
    expect(cassandraConfig.cassandraKeyspace === OverrideExample.keyspaceName)
    expect(cassandraConfig.cassandraTable === OverrideExample.tableName)

    val driverConfigLoader = cassandraConfig.resolveDriverConfigLoader(system).success.value
    val driverConfig       = driverConfigLoader.getInitialConfig
    val readProfile        = driverConfig.getProfile(OverrideExample.readProfileName)
    val writeProfile       = driverConfig.getProfile(OverrideExample.writeProfileName)
    expect(
      readProfile.getString(DefaultDriverOption.REQUEST_CONSISTENCY) === OverrideExample.readRequestConsistency,
    )
    expect(
      writeProfile.getString(DefaultDriverOption.REQUEST_CONSISTENCY) === OverrideExample.writeRequestConsistency,
    )

  }

  "resolveDriverConfigLoader should return a Failure with ConfigException.Missing when the driver config is missing" in {

    val cassandraConfigWithMissingDriverConfig =
      new SequenceFactoryConfig(system.settings.config).cassandraConfig(MissingDriverConfig.tenant)

    val exception = cassandraConfigWithMissingDriverConfig.resolveDriverConfigLoader(system).failure.exception
    expect(exception.isInstanceOf[ConfigException.Missing])

  }

  "resolveDriverConfigLoader should return a Failure with ConfigException.WrongType when the driver config has wrong type." in {

    val cassandraConfigWithWrongDriverConfigType =
      new SequenceFactoryConfig(system.settings.config).cassandraConfig(WrongDriverConfigType.tenant)

    val exception = cassandraConfigWithWrongDriverConfigType.resolveDriverConfigLoader(system).failure.exception
    expect(exception.isInstanceOf[ConfigException.WrongType])

  }

  "resolveDriverConfigLoader should return a Failure with IllegalArgumentException when the read-profile is missing" in {

    val cassandraConfigWithMissingReadProfile =
      new SequenceFactoryConfig(system.settings.config).cassandraConfig(MissingReadProfileExample.tenant)

    val exception = cassandraConfigWithMissingReadProfile.resolveDriverConfigLoader(system).failure.exception
    expect(exception.isInstanceOf[IllegalArgumentException])
    expect(
      exception.getMessage.contains(
        s"""Read Profile: path="${MissingReadProfileExample.readProfilePath}", missing=true""",
      ),
    )

  }

  "resolveDriverConfigLoader should return a Failure with IllegalArgumentException when the write-profile is missing" in {

    val cassandraConfigWithMissingWriteProfile =
      new SequenceFactoryConfig(system.settings.config).cassandraConfig(MissingWriteProfileExample.tenant)

    val exception = cassandraConfigWithMissingWriteProfile.resolveDriverConfigLoader(system).failure.exception
    expect(exception.isInstanceOf[IllegalArgumentException])
    expect(
      exception.getMessage.contains(
        s"""Write Profile: path="${MissingWriteProfileExample.writeProfilePath}", missing=true""",
      ),
    )

  }

}
