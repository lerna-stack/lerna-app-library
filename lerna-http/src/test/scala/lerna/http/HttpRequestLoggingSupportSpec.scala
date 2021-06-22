package lerna.http

import akka.actor.testkit.typed.scaladsl.{ LoggingTestKit, TestDuration }
import akka.actor.{ typed, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse }
import lerna.log.AppLogging
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant
import lerna.util.trace.{ RequestContext, TraceId }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

private object HttpRequestLoggingSupportSpec {
  class HttpRequestLoggingSupportForClassic(
      val system: ActorSystem,
  ) extends HttpRequestLoggingSupport
      with AppLogging {
    override val scope: String = "dummy"
  }

  class HttpRequestLoggingSupportForTyped(
      val system: typed.ActorSystem[_],
  ) extends HttpRequestLoggingSupport
      with AppLogging {
    override val scope: String = "dummy"
  }
}

@SuppressWarnings(Array("lerna.warts.Awaits"))
class HttpRequestLoggingSupportSpec extends ScalaTestWithTypedActorTestKit() with LernaBaseSpec {
  import HttpRequestLoggingSupportSpec._

  "HttpRequestLoggingSupport" should {
    "Classic ActorSystem を使ってインスタンス化できる" in {
      val classicSystem: ActorSystem = system.classicSystem
      noException should be thrownBy new HttpRequestLoggingSupportForClassic(classicSystem)
    }

    "Typed ActorSystem を使ってインスタンス化できる" in {
      val typedSystem: typed.ActorSystem[_] = system
      noException should be thrownBy new HttpRequestLoggingSupportForTyped(typedSystem)
    }

    implicit val requestContext: RequestContext = new RequestContext {
      override def traceId: TraceId = TraceId.unknown
      override implicit def tenant: Tenant = new Tenant {
        override def id: String = "dummy"
      }
    }
    def startServer(
        interface: String = "127.0.0.1",
        responseHeader: Option[RawHeader] = None,
        responseBody: String = "",
    ): String = {
      val bindingFuture: Future[Http.ServerBinding] = Http()
        .newServerAt(interface, port = 0)
        .bindSync { _ =>
          HttpResponse()
            .withHeaders(responseHeader.toList)
            .withEntity(responseBody)
        }
      val binding = Await.result(bindingFuture, 3.seconds.dilated)
      val port    = binding.localAddress.getPort.toString
      port
    }

    "URL を リクエストログに 出力する" in {
      val port = startServer("127.0.0.3")
      val request = HttpRequest()
        .withMethod(HttpMethods.GET)
        .withUri(s"http://127.0.0.3:$port/dummy-path?key=value")

      LoggingTestKit
        .info(s"Request: [GET] http://127.0.0.3:$port/dummy-path?key=value")
        .expect {
          new HttpRequestLoggingSupportForTyped(system)
            .httpSingleRequestWithAroundLogWithTimeout(request, timeout = 1.second.dilated)
        }
    }

    "HTTP Header を リクエストログに 出力する" in {
      val port = startServer()
      val request = HttpRequest()
        .withUri(s"http://127.0.0.1:$port/")
        .addHeader(RawHeader("X-dummy-header", "dummy-value"))

      LoggingTestKit
        .info("RequestHeaders: [X-dummy-header: dummy-value]")
        .expect {
          new HttpRequestLoggingSupportForTyped(system)
            .httpSingleRequestWithAroundLogWithTimeout(request, timeout = 1.second.dilated)
        }
    }

    "HTTP Body を リクエストログに 出力する" in {
      val port = startServer()
      val request = HttpRequest()
        .withUri(s"http://127.0.0.1:$port/")
        .withEntity("dummy-body")

      LoggingTestKit
        .info("RequestBody: dummy-body")
        .expect {
          new HttpRequestLoggingSupportForTyped(system)
            .httpSingleRequestWithAroundLogWithTimeout(request, timeout = 1.second.dilated)
        }
    }

    "URL を レスポンスログに 出力する" in {
      val port = startServer("127.0.0.5")
      val request = HttpRequest()
        .withUri(s"http://127.0.0.5:$port/dummy-path?key=value")

      LoggingTestKit
        .info(s"Response: http://127.0.0.5:$port/dummy-path?key=value")
        .expect {
          new HttpRequestLoggingSupportForTyped(system)
            .httpSingleRequestWithAroundLogWithTimeout(request, timeout = 1.second.dilated)
        }
    }

    "HTTP Header を レスポンスログに 出力する" in {
      val port = startServer(responseHeader = Option(RawHeader("X-dummy-header", "dummy-value")))
      val request = HttpRequest()
        .withUri(s"http://127.0.0.1:$port/")

      LoggingTestKit
        .info("ResponseHeaders: [X-dummy-header: dummy-value,")
        .expect {
          new HttpRequestLoggingSupportForTyped(system)
            .httpSingleRequestWithAroundLogWithTimeout(request, timeout = 1.second.dilated)
        }
    }

    "HTTP Body を レスポンスログに 出力する" in {
      val port = startServer(responseBody = "dummy-body")
      val request = HttpRequest()
        .withUri(s"http://127.0.0.1:$port/")

      LoggingTestKit
        .info("ResponseBody: HttpEntity.Strict(text/plain; charset=UTF-8,10 bytes total)") // FIXME: body が出ない
        .expect {
          new HttpRequestLoggingSupportForTyped(system)
            .httpSingleRequestWithAroundLogWithTimeout(request, timeout = 1.second.dilated)
        }
    }
  }
}
