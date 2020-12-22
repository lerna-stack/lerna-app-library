package lerna.management.stats

import lerna.util.tenant.Tenant

/** A metrics key
  *
  * @param key An underlying value which represents the key of the metric.
  * @param tenant `Some` for tenant-specific values, `None` for system-common values
  */
final case class MetricsKey(key: String, tenant: Option[Tenant])
