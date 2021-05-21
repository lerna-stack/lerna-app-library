package lerna.management.stats

import kamon.tag.TagSet
import lerna.util.tenant.Tenant

/** An object that provides extension methods related to multi-tenant features
  */
object MetricsMultiTenantSupport {
  private[this] val tenantIdTagKey = "_tenant_id"

  implicit class TagsOps(val tags: TagSet) extends AnyVal {

    /** Create new TagSet including a tenant
      * @param tenant A tenant
      * @return new TagSet including the given tenant
      */
    def withTenant(implicit tenant: Tenant): TagSet = tags.withTag(tenantIdTagKey, tenant.id)
  }
}
