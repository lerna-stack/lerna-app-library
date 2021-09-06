package lerna.util.sequence

import com.datastax.oss.driver.api.core.AllNodesFailedException
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.typesafe.config.{ Config, ConfigFactory }
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant
import org.scalatest.concurrent.ScalaFutures

import java.util.concurrent.CompletionException
import scala.concurrent.ExecutionContext

object CassandraSessionProviderSpec {

  private val DefaultExecutionProfileName: String    = "lerna-util-sequence-profile"
  private val DefaultRequestConsistencyLevel: String = "QUORUM"

  private object DefaultExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "default-example"
    }
  }
  private object OverrideSessionProviderExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "session-provider-override-example"
    }
  }
  private object OverrideServiceDiscoveryExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "service-discovery-override-example"
    }
  }
  private object OverrideDriverConfigExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "datastax-java-driver-override-example"
    }
    val driverConfigPath        = "datastax-java-driver-override-config"
    val readProfileName         = "my-read-profile"
    val writeProfileName        = "my-write-profile"
    val readRequestConsistency  = "ALL"
    val writeRequestConsistency = "ONE"
  }
  private object MissingDriverConfigExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "datastax-java-driver-missing-example"
    }
    val driverConfigPath = "datastax-java-driver-missing"
  }
  private object WrongDriverConfigTypeExample {
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
    val readProfilePath = s"datastax-java-driver.profiles.$readProfileName"
  }
  private object MissingWriteProfileExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "missing-write-profile-example"
    }
    val writeProfileName = "missing-write-profile"
    val writeProfilePath = s"datastax-java-driver.profiles.$writeProfileName"
  }

  private val config: Config = ConfigFactory
    .parseString(s"""
                    |akka {
                    |  actor.provider = local
                    |  discovery.method = config
                    |}
                    |
                    |lerna.util.sequence {
                    |  cassandra.tenants.${DefaultExample.tenant.id} = $${lerna.util.sequence.cassandra.default}
                    |  cassandra.tenants.${OverrideSessionProviderExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    session-provider = "invalid-session-provider"
                    |  }
                    |  cassandra.tenants.${OverrideServiceDiscoveryExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    service-discovery {
                    |      name = "cassandra-service"
                    |    }
                    |  }
                    |  cassandra.tenants.${OverrideDriverConfigExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    datastax-java-driver-config = "${OverrideDriverConfigExample.driverConfigPath}"
                    |    read-profile = "${OverrideDriverConfigExample.readProfileName}"
                    |    write-profile = "${OverrideDriverConfigExample.writeProfileName}"
                    |  }
                    |  cassandra.tenants.${MissingDriverConfigExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    datastax-java-driver-config = "${MissingDriverConfigExample.driverConfigPath}"
                    |  }
                    |  cassandra.tenants.${WrongDriverConfigTypeExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    datastax-java-driver-config = "${WrongDriverConfigTypeExample.driverConfigPath}"
                    |  }
                    |  cassandra.tenants.${MissingReadProfileExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    read-profile = "${MissingReadProfileExample.readProfileName}"
                    |  }
                    |  cassandra.tenants.${MissingWriteProfileExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    write-profile = "${MissingWriteProfileExample.writeProfileName}"
                    |  }
                    |}
                    |
                    |akka.discovery.config.services = {
                    |  cassandra-service = {
                    |    endpoints = [
                    |      {
                    |        host = "127.0.0.1"
                    |        port = 19042
                    |      }
                    |    ]
                    |  }
                    |}
                    |
                    |${OverrideDriverConfigExample.driverConfigPath} = $${datastax-java-driver} {
                    |  profiles {
                    |    ${OverrideDriverConfigExample.readProfileName} {
                    |      basic.request {
                    |        consistency = "${OverrideDriverConfigExample.readRequestConsistency}"
                    |      }
                    |    }
                    |    ${OverrideDriverConfigExample.writeProfileName} {
                    |      basic.request {
                    |        consistency = "${OverrideDriverConfigExample.writeRequestConsistency}"
                    |      }
                    |    }
                    |  }
                    |}
                    |
                    |${WrongDriverConfigTypeExample.driverConfigPath} = "Config type is wrong"
                    """.stripMargin)
    .withFallback(ConfigFactory.defaultReferenceUnresolved())
    .resolve()
}

@SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
final class CassandraSessionProviderSpec
    extends ScalaTestWithTypedActorTestKit(CassandraSessionProviderSpec.config)
    with LernaBaseSpec
    with ScalaFutures {

  import CassandraSessionProviderSpec._

  implicit val ec: ExecutionContext = system.executionContext

  "CassandraSessionProvider.connect should connect to the cluster with default settings" in {

    val cassandraConfig = new SequenceFactoryConfig(config).cassandraConfig(DefaultExample.tenant)
    val session         = CassandraSessionProvider.connect(system, cassandraConfig).futureValue
    // CassandraSessionProvider has done connecting to the cluster here since we have the valid session.

    // Test the config has default values
    val driverConfig     = session.getContext.getConfig
    val executionProfile = driverConfig.getProfile(DefaultExecutionProfileName)
    expect(
      executionProfile.getString(DefaultDriverOption.REQUEST_CONSISTENCY) === DefaultRequestConsistencyLevel,
    )

    session.close()

  }

  "CassandraSessionProvider.connect should respect the given `session-provider` setting" in {

    // If the CassandraSessionProvider respects the given `session-provider` setting, connecting to the cluster should fail.
    val cassandraConfigWithInvalidSessionProvider =
      new SequenceFactoryConfig(config).cassandraConfig(OverrideSessionProviderExample.tenant)
    val exception =
      CassandraSessionProvider.connect(system, cassandraConfigWithInvalidSessionProvider).failed.futureValue
    expect(exception.isInstanceOf[ClassNotFoundException])

  }

  "CassandraSessionProvider.connect should respect the given `service-discovery` setting" in {

    // If the CassandraSessionProvider respects the given `service-discovery` setting, connecting to the cluster should fail.
    val cassandraConfigWithInvalidServiceDiscovery =
      new SequenceFactoryConfig(config).cassandraConfig(OverrideServiceDiscoveryExample.tenant)
    val exception =
      CassandraSessionProvider.connect(system, cassandraConfigWithInvalidServiceDiscovery).failed.futureValue
    expect(exception.isInstanceOf[CompletionException])
    expect(exception.getCause.isInstanceOf[AllNodesFailedException])

  }

  "CassandraSessionProvider.connect should respect the given `datastax-java-driver-config` setting" in {

    // Overwritten values should not equal the default.
    assume(OverrideDriverConfigExample.readProfileName !== DefaultExecutionProfileName)
    assume(OverrideDriverConfigExample.writeProfileName !== DefaultExecutionProfileName)
    assume(OverrideDriverConfigExample.readProfileName !== OverrideDriverConfigExample.writeProfileName)
    assume(OverrideDriverConfigExample.readRequestConsistency !== DefaultRequestConsistencyLevel)
    assume(OverrideDriverConfigExample.writeRequestConsistency !== DefaultRequestConsistencyLevel)
    assume(OverrideDriverConfigExample.readRequestConsistency !== OverrideDriverConfigExample.writeRequestConsistency)

    val cassandraConfig =
      new SequenceFactoryConfig(system.settings.config).cassandraConfig(OverrideDriverConfigExample.tenant)

    val session = CassandraSessionProvider.connect(system, cassandraConfig).futureValue
    // CassandraSessionProvider has done connecting to the cluster here since we have the valid session.

    // Tests the config has overwritten values
    val driverConfig = session.getContext.getConfig
    val readProfile  = driverConfig.getProfile(OverrideDriverConfigExample.readProfileName)
    val writeProfile = driverConfig.getProfile(OverrideDriverConfigExample.writeProfileName)
    expect(
      readProfile.getString(
        DefaultDriverOption.REQUEST_CONSISTENCY,
      ) === OverrideDriverConfigExample.readRequestConsistency,
    )
    expect(
      writeProfile.getString(
        DefaultDriverOption.REQUEST_CONSISTENCY,
      ) === OverrideDriverConfigExample.writeRequestConsistency,
    )

    session.close()

  }

  "CassandraSessionProvider.connect should return a Failure with IllegalArgumentException when the driver config is missing" in {

    val cassandraConfigWithMissingDriverConfig =
      new SequenceFactoryConfig(system.settings.config).cassandraConfig(MissingDriverConfigExample.tenant)

    val exception = CassandraSessionProvider.connect(system, cassandraConfigWithMissingDriverConfig).failed.futureValue
    expect(exception.isInstanceOf[IllegalArgumentException])

  }

  "resolveDriverConfigLoader.connect should return a Failure with IllegalArgumentException when the driver config is a wrong type." in {

    val cassandraConfigWithWrongDriverConfigType =
      new SequenceFactoryConfig(system.settings.config).cassandraConfig(WrongDriverConfigTypeExample.tenant)

    val exception =
      CassandraSessionProvider.connect(system, cassandraConfigWithWrongDriverConfigType).failed.futureValue
    expect(exception.isInstanceOf[IllegalArgumentException])

  }

  "CassandraSessionProvider.connect should return a Failure with IllegalArgumentException when the read-profile is missing" in {

    val cassandraConfigWithMissingReadProfile =
      new SequenceFactoryConfig(system.settings.config).cassandraConfig(MissingReadProfileExample.tenant)

    val exception = CassandraSessionProvider.connect(system, cassandraConfigWithMissingReadProfile).failed.futureValue
    expect(exception.isInstanceOf[IllegalArgumentException])
    expect(
      exception.getMessage.contains(
        s"""Read Profile: path="[${MissingReadProfileExample.readProfilePath}]", missing=true""",
      ),
    )

  }

  "CassandraSessionProvider.connect should return a Failure with IllegalArgumentException when the write-profile is missing" in {

    val cassandraConfigWithMissingWriteProfile =
      new SequenceFactoryConfig(system.settings.config).cassandraConfig(MissingWriteProfileExample.tenant)

    val exception = CassandraSessionProvider.connect(system, cassandraConfigWithMissingWriteProfile).failed.futureValue
    expect(exception.isInstanceOf[IllegalArgumentException])
    expect(
      exception.getMessage.contains(
        s"""Write Profile: path="[${MissingWriteProfileExample.writeProfilePath}]", missing=true""",
      ),
    )

  }

}
