package lerna.management.stats

import akka.actor.ActorSystem
import lerna.management.LernaManagementActorBaseSpec
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
