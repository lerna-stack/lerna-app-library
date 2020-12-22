package lerna.util.trace

import lerna.log.LogContext
import lerna.util.tenant.Tenant

/** A trait that represents a request context
  *
  * A request context also represents a logging context.
  * This logging context contains a tenant and a trace ID.
  */
trait RequestContext extends LogContext {

  /** A trace ID attached to this request
    * @return The trace ID
    */
  def traceId: TraceId

  /** A tenant attached to this request
    * @return The tenant
    */
  implicit def tenant: Tenant

  override def mdc: Map[String, String] = Map(
    "traceId"  -> traceId.id,
    "tenantId" -> tenant.id,
  )
}
