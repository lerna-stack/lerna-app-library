package lerna.http.directives

import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import lerna.http.LernaHttpBaseSpec

final class GenTraceIDDirectiveSpec extends LernaHttpBaseSpec with ScalatestRouteTest {
  object DirectiveSupport extends GenTraceIDDirective
  import DirectiveSupport._

  "the extractTraceId directive" should {

    val route = Route.seal(
      extractTraceId { traceId =>
        complete(traceId.id)
      },
    )

    "extract the trace ID from the custom header" in {
      Get("abc") ~> addHeader("X-Tracing-Id", "123456789") ~> route ~> check {
        responseAs[String] shouldBe "123456789"
      }
    }

    "extract the default trace ID when the custom header is not available" in {
      Get("abc") ~> route ~> check {
        responseAs[String] shouldBe "99999999999"
      }
    }

  }

}
