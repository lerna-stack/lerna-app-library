package lerna.management.stats

import akka.actor.{ typed, ActorSystem }
import lerna.management.LernaManagementActorBaseSpec
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant

final class MetricsSpec extends LernaManagementActorBaseSpec(ActorSystem("MetricsSpec")) {
  "Metrics.apply" should {
    "create a default instance" in {
      val tenant1: Tenant = new Tenant {
        override def id: String = "dummy-1"
      }
      val metrics = Metrics(system, Set(tenant1))
      metrics shouldBe a[MetricsImpl]
    }
  }
}

final class MetricsTypedSpec extends ScalaTestWithTypedActorTestKit() with LernaBaseSpec {
  "Metrics.apply" should {
    "create a default instance by typed ActorSystem" in {
      val typedSystem: typed.ActorSystem[Nothing] = system
      val tenant1: Tenant = new Tenant {
        override def id: String = "dummy-1"
      }
      val metrics = Metrics(typedSystem, Set(tenant1))
      metrics shouldBe a[MetricsImpl]
    }
  }
}
