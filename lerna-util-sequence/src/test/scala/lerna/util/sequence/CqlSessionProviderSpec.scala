package lerna.util.sequence

import com.typesafe.config.{ Config, ConfigFactory }
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant
import org.scalatest.concurrent.ScalaFutures

import java.util.concurrent.CompletionException

object CqlSessionProviderSpec {

  private object DefaultExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "default-example"
    }
  }
  private object FailureExample {
    val tenant: Tenant = new Tenant {
      override def id: String = "failure-example"
    }
  }

  private val config: Config = ConfigFactory
    .parseString(s"""
                    |akka.actor {
                    |  provider = local
                    |}
                    |lerna.util.sequence {
                    |  cassandra.tenants.${DefaultExample.tenant.id} = $${lerna.util.sequence.cassandra.default}
                    |  cassandra.tenants.${FailureExample.tenant.id} = $${lerna.util.sequence.cassandra.default} {
                    |    datastax-java-driver-config = "datastax-java-driver-failure"
                    |  }
                    |}
                    |datastax-java-driver-failure = $${datastax-java-driver} {
                    |  advanced.auth-provider {
                    |    class = null
                    |  }
                    |}
                    """.stripMargin)
    .withFallback(ConfigFactory.defaultReferenceUnresolved())
    .resolve()
}

@SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
final class CqlSessionProviderSpec
    extends ScalaTestWithTypedActorTestKit(CqlSessionProviderSpec.config)
    with LernaBaseSpec
    with ScalaFutures {

  import CqlSessionProviderSpec._

  "CqlSessionProvider shoudl respect the given configuration to connect to a Cassandra cluster" in {

    // Testing against two different configurations ensures
    // CqlSessionProvider should respect the given configuration.

    val cassandraConfig = new SequenceFactoryConfig(config).cassandraConfig(DefaultExample.tenant)
    val session         = CqlSessionProvider.connect(system, cassandraConfig).futureValue
    // CqlSessionProvider has done connecting to the cluster here since we have the valid session.
    session.close()

    // CqlSessionProvider should not connect to the cluster since we have the wrong configuration.
    val cassandraConfigWithWrongCredentials =
      new SequenceFactoryConfig(config).cassandraConfig(FailureExample.tenant)
    val exception = CqlSessionProvider.connect(system, cassandraConfigWithWrongCredentials).failed.futureValue
    expect(exception.isInstanceOf[CompletionException])

  }

}
