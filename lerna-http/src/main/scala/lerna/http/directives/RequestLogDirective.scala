package lerna.http.directives

import akka.http.scaladsl.server.directives.BasicDirectives
import akka.http.scaladsl.server.{ Directive0, RouteResult }
import lerna.log.{ AppLogging, LogContext }

/** A trait that provides custom logging ''Akka HTTP'' directives
  */
trait RequestLogDirective extends GenTraceIDDirective with AppLogging {
  import BasicDirectives._

  /** A directive that logs verbose of the request and the ''TraceID''
    *
    * @param logContext The context of the log
    */
  def logRequestDirective(implicit logContext: LogContext): Directive0 =
    extractRequest map { req =>
      logger info s"Request: [${req.method.value}] ${req.uri.path.toString}, RequestHeaders: ${req.getHeaders.toString}"
    }

  /** A directive that logs verbose of the response and the ''TraceID''
    * @param logContext The context of the log
    */
  def logRequestResultDirective(implicit logContext: LogContext): Directive0 =
    extractRequest flatMap { req =>
      mapRouteResult { routeResult =>
        routeResult match {
          case RouteResult.Complete(res) =>
            logger.info(
              s"Response: ${req.uri.path.toString} : ${res.status.toString}, ResponseHeaders: ${res.getHeaders.toString}, " +
              s"RequestBody: ${req.entity.toString.replaceAll("\n", "")}, " +
              s"ResponseBody: ${res.entity.toString.replaceAll("\n", "")}",
            )
          case _ => // no log entries for rejections
        }

        routeResult
      }
    }

}
