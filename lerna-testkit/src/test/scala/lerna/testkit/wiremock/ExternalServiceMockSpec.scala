package lerna.testkit.wiremock

import _root_.akka.http.scaladsl.Http
import _root_.akka.http.scaladsl.model.{ HttpRequest, StatusCodes }
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.{ get, ok, urlEqualTo }
import lerna.testkit.LernaRouteTestBaseSpec

final class ExternalServiceMockSpec extends LernaRouteTestBaseSpec {

  object MyService extends ExternalServiceMock {
    override protected lazy val https: Boolean    = false
    override protected lazy val host: String      = "127.0.0.1"
    override protected lazy val port: Option[Int] = Option(9000)
  }

  override def afterAll(): Unit = {
    try MyService.close()
    finally super.afterAll()
  }

  "ExternalServiceMock" should {
    "serve the resource via the imported stubs" in {
      val ping: MappingBuilder =
        get(urlEqualTo("/ping"))
          .willReturn(
            ok("pong"),
          )
      MyService.importStubs(ping)

      val request        = HttpRequest(uri = "http://127.0.0.1:9000/ping")
      val responseFuture = Http().singleRequest(request)
      whenReady(responseFuture) { response =>
        response.status shouldBe StatusCodes.OK
        val body = unmarshalValue[String](response.entity)
        body shouldBe "pong"
      }
    }
  }

}
