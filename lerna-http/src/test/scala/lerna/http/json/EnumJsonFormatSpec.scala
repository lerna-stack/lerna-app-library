package lerna.http.json

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{ HttpEntity, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import lerna.http.LernaHttpRouteBaseSpec
import spray.json.RootJsonFormat

object EnumJsonFormatSpec {

  object ExampleCode extends Enumeration {
    type ExampleCode = ExampleCode.Value

    val One: ExampleCode.Value = Value("01")
    val Two: ExampleCode.Value = Value("02")
  }

  import ExampleCode._

  final case class ExampleRequest(code: ExampleCode)
}

class EnumJsonFormatSpec extends LernaHttpRouteBaseSpec {
  import EnumJsonFormatSpec._
  import ExampleCode._

  object JsonSupport extends SprayJsonSupport {
    import spray.json.DefaultJsonProtocol._
    implicit val exampleCodeFormat: RootJsonFormat[ExampleCode]       = EnumJsonFormat(ExampleCode)
    implicit val exampleRequestFormat: RootJsonFormat[ExampleRequest] = jsonFormat1(ExampleRequest)
  }

  "EnumJsonFormat" should {
    import JsonSupport._

    val route =
      post {
        entity(as[ExampleRequest]) { req =>
          complete {
            StatusCodes.OK -> req.code
          }
        }
      }

    "区分値にある値を受け取ったら case class に変換される" in {

      Post("/", HttpEntity(`application/json`, """{ "code": "01" }""")) ~> Route.seal(route) ~> check {
        expect {
          status === StatusCodes.OK
          responseAs[ExampleCode] === ExampleCode.One
        }
      }
    }

    "区分値にない値を受け取ったら case class には変換されずにエラーレスポンスを返す" in {

      Post("/", HttpEntity(`application/json`, """{ "code": "03" }""")) ~> Route.seal(route) ~> check {
        expect {
          status === StatusCodes.BadRequest
        }
      }
    }

  }

}
