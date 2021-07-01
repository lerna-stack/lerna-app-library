package lerna.http

import akka.actor.{ ActorSystem, ClassicActorSystemProvider }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.after
import lerna.log.AppLogging
import lerna.util.trace.RequestContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContextExecutor, Future, TimeoutException }
import scala.util.{ Failure, Success }

/** A trait that provides sending an HTTP request with logging and timeout
  */
trait HttpRequestLoggingSupport extends HttpRequestProxySupport { self: AppLogging =>

  val system: ClassicActorSystemProvider
  implicit private def classicSystem: ActorSystem         = system.classicSystem
  private[this] implicit def ec: ExecutionContextExecutor = classicSystem.dispatcher

  val scope: String

  /** Send an HTTP request with logging and timeout
    *
    * If no response is received within the given `timeout`, return the failure instance of [[scala.concurrent.Future]].
    * The request is sent to the proxy instead of the destination of the request if the `useProxy` is `true`.
    *
    * The request and response are logged using [[lerna.log.AppLogging]].
    * If some value of the request or the response should be masked, use the parameter `maskLog`.
    *
    * @param req The plain HTTP request instance
    * @param timeout The timeout
    * @param useProxy Whether use the proxy or not. Proxy is used if `true`.
    * @param maskLog The function that masks the value in the request and response
    * @param requestContext The context of the request
    * @return A [[scala.concurrent.Future]] containing the response
    */
  def httpSingleRequestWithAroundLogWithTimeout(
      req: HttpRequest,
      timeout: FiniteDuration,
      useProxy: Boolean = false,
      maskLog: String => String = identity,
  )(implicit
      requestContext: RequestContext,
  ): Future[HttpResponse] = {
    import requestContext.tenant

    val start = System.nanoTime()
    Unmarshal(req).to[String].onComplete { triedString =>
      val requestBody = triedString.toEither.left.map { throwable =>
        logger.warn(throwable, "Failed to get the request body")
        req.entity.toString
      }.merge
      logger.info(
        s"Request: [${req.method.value}] ${req.uri.toString}, RequestHeaders: ${req.getHeaders.toString}, RequestBody: ${maskLog(requestBody)}",
      )
    }

    Future
      .firstCompletedOf(
        Seq(
          Http().singleRequest(req, settings = generateRequestSetting(useProxy)).flatMap(_.toStrict(timeout)),
          after(timeout, classicSystem.scheduler)(
            Future.failed(new TimeoutException(s"Request timed out after [${timeout.toString}]")),
          ),
        ),
      ).andThen {
        case Success(res) =>
          Unmarshal(res).to[String].onComplete { triedString =>
            val responseBody = triedString.toEither.left.map { throwable =>
              logger.warn(throwable, "Failed to get the response body")
              res.entity.toString
            }.merge
            logger.info(
              s"Response: ${req.uri.toString} : ${res.status.toString}, ${latencyAndScope(req, start)}, ResponseHeaders: ${res.getHeaders.toString}, ResponseBody: ${maskLog(responseBody)}",
            )
          }
        case Failure(exception) =>
          logger.warn(exception, s"Response: ${req.uri.toString} : failed, ${latencyAndScope(req, start)}")
      }
  }

  private def latencyAndScope(
      req: HttpRequest,
      startTime: Long,
  ): String = {

    val latency = (System.nanoTime() - startTime) / 1000000
    s"latency: ${latency.toString} ms, scope: $scope"
  }
}
