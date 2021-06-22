package lerna.http.directives

import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import lerna.http.LernaHttpBaseSpec
import lerna.log.SystemComponentLogContext

class RequestLogDirectiveSpec extends LernaHttpBaseSpec with ScalatestRouteTest with RequestLogDirective {
  import SystemComponentLogContext.logContext
  implicit private def typedSystem: ActorSystem[Nothing] = system.toTyped

  "RequestLogDirective.logRequestDirective" should {
    val route = logRequestDirective & complete("")

    "URL を リクエストログに 出力する" in {
      val request = Get("/dummy-path/123")

      LoggingTestKit
        .info("Request: [GET] /dummy-path/123,")
        .expect {
          request ~> route
        }
    }

    "HTTP Header を リクエストログに 出力する" in {
      val request = Get() ~> addHeader("X-dummy-header", "dummy-value")

      LoggingTestKit
        .info("RequestHeaders: [X-dummy-header: dummy-value]")
        .expect {
          request ~> route
        }
    }
  }

  "RequestLogDirective.logRequestResultDirective" should {
    "URL を レスポンスログに 出力する" in {
      val request = Get("/dummy-path/123")
      val route   = logRequestResultDirective & complete("")

      LoggingTestKit
        .info("Response: /dummy-path/123 : 200 OK,")
        .expect {
          request ~> route
        }
    }

    "HTTP Header を レスポンスログに 出力する" in {
      val request = Get()
      val route =
        logRequestResultDirective & respondWithHeaders(RawHeader("X-dummy-header", "dummy-value")) & complete("")

      LoggingTestKit
        .info("ResponseHeaders: [X-dummy-header: dummy-value]")
        .expect {
          request ~> route
        }
    }

    "HTTP Request Body を レスポンスログに 出力する" in { // Request Body はすべての処理が終わった後にレスポンスログに含めて出す仕様
      val request = Post().withEntity("dummy-body")
      val route   = logRequestResultDirective & complete("")

      LoggingTestKit
        .info("RequestBody: dummy-body,")
        .expect {
          request ~> route
        }
    }

    "HTTP Response Body を レスポンスログに 出力する" in {
      val request = Get()
      val route   = logRequestResultDirective & complete("dummy-body")

      LoggingTestKit
        .info("ResponseBody: dummy-body")
        .expect {
          request ~> route
        }
    }
  }
}
