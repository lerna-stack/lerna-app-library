package lerna.util

import lerna.util.tenant.Tenant
import lerna.util.trace.{ RequestContext, TraceId }

package object akka {
  implicit def requestContext(implicit _traceId: TraceId): RequestContext = new RequestContext {
    override def traceId: TraceId = _traceId
    override def tenant: Tenant = new Tenant {
      override def id: String = "dummy"
    }
  }
}
