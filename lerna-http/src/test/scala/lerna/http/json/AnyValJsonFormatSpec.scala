package lerna.http.json

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{ HttpEntity, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import lerna.http.LernaHttpRouteBaseSpec
import spray.json.{ JsonFormat, RootJsonFormat }

object AnyValJsonFormatSpec {
  final case class ExampleVal(value: String) extends AnyVal

  final case class ExampleObject(ex: ExampleVal)
}

class AnyValJsonFormatSpec extends LernaHttpRouteBaseSpec {
  import AnyValJsonFormatSpec._

  object JsonSupport extends SprayJsonSupport {
    import spray.json.DefaultJsonProtocol._
    implicit val exampleValueFormat: JsonFormat[ExampleVal]         = AnyValJsonFormat(ExampleVal.apply, ExampleVal.unapply)
    implicit val exampleObjectFormat: RootJsonFormat[ExampleObject] = jsonFormat1(ExampleObject)
  }

  "AnyValJsonFormat" should {
    import JsonSupport._

    val route =
      post {
        entity(as[ExampleObject]) { req =>
          complete {
            StatusCodes.OK -> req
          }
        }
      }

    "AnyVal から JSValue に、JSValue から AnyVal に相互に変換可能" in {
      Post("/", HttpEntity(`application/json`, """{ "ex": "01" }""")) ~> Route.seal(route) ~> check {
        expect {
          status === StatusCodes.OK
          responseAs[ExampleObject] === ExampleObject(ExampleVal("01"))
        }
      }
    }

    "AnyVal の型と一致しない場合は BadRequest" in {
      Post("/", HttpEntity(`application/json`, """{ "ex": 1 }""")) ~> Route.seal(route) ~> check {
        expect {
          status === StatusCodes.BadRequest
        }
      }
    }
  }
}
