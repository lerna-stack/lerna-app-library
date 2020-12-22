package lerna.util.tenant

import lerna.log.LogContext

/** A trait that represents logging context that is including a tenant
  */
final case class TenantComponentLogContext private (tenant: Tenant) extends LogContext {
  override protected[lerna] def mdc: Map[String, String] = Map(
    "tenantId" -> tenant.id,
  )
}

/** An object that provides implicit conversions from [[lerna.util.tenant.Tenant]] to [[lerna.log.LogContext]]
  */
object TenantComponentLogContext {

  /** Convert a tenant to a logging context that is including the tenant
    * @param tenant A tenant to be converted
    * @return A logging context that is including the tenant
    */
  implicit def logContext(implicit tenant: Tenant): LogContext = TenantComponentLogContext(tenant)
}
