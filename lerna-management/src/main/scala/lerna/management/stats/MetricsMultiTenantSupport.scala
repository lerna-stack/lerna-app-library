package lerna.management.stats

import kamon.Tags
import lerna.util.tenant.Tenant

/** An object that provides extension methods related to multi-tenant features
  */
object MetricsMultiTenantSupport {
  private[this] val tenantIdTagKey = "_tenant_id"

  implicit class TagsOps(val tags: Tags) extends AnyVal {

    /** Create new tags that are including a tenant
      * @param tenant A tenant
      * @return new tags that are including the given tenant
      */
    def withTenant(implicit tenant: Tenant): Tags = tags.updated(tenantIdTagKey, tenant.id)
  }
}
