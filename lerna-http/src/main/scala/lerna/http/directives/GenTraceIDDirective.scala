package lerna.http.directives

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{ optionalHeaderValueByName, provide }
import lerna.util.trace.TraceId

/** A trait that provides ''TraceID'' related ''Akka HTTP'' directives
  */
trait GenTraceIDDirective {

  /** Extract a ''TraceID'' from the HTTP Header
    *
    * ==Details==
    * ''TraceID'' is extracted from the HTTP header value with the name `X-Tracing-Id`.
    * If no header with the name `X-Tracing-Id` exists, the default value (`99999999999`) is returned.
    *
    * @return The ''TraceID'' extracted from the HTTP header
    */
  def extractTraceId: Directive1[TraceId] = {
    optionalHeaderValueByName("X-Tracing-Id") flatMap {
      case None          => provide(TraceId("99999999999")) // HttpHeaderにTraceIdが渡った来ない開発環境用
      case Some(traceId) => provide(TraceId(traceId))
    }
  }

}
