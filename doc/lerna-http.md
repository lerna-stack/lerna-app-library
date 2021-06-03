# Lerna HTTP

*Lerna HTTP* library provides HTTP related features like below.

- Custom Json Format for [spray-json](https://github.com/spray/spray-json)
- Custom Directives for [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html)
- Custom Header for *Akka HTTP*


## Custom Json Formats of *spray-json*

### AnyValJsonFormat
`AnyValJsonFormat` is a class that provides a JSON serialization/deserialization of any type.
It is useful for a class extending `AnyVal` since it serializes and deserializes an underlying value of the given instance.
But, now, we need to write  some boilerplate.

```scala
import lerna.http.json.AnyValJsonFormat
import spray.json.JsonFormat
import spray.json.DefaultJsonProtocol._

final case class ExampleVal(value: Int) extends AnyVal
implicit val exampleValueFormat: JsonFormat[ExampleVal] = AnyValJsonFormat(ExampleVal.apply, ExampleVal.unapply)
```

### EnumJsonFormat
`EnumJsonFormat` is a class that provides a JSON serialization/deserialization of scala `Enumeration`.
We can use it like below.

```scala mdoc:reset
import lerna.http.json.EnumJsonFormat
import spray.json.RootJsonFormat

object ExampleEnum extends Enumeration {
  val One: ExampleEnum.Value = Value("01")
  val Two: ExampleEnum.Value = Value("02")
}
implicit val exampleEnumFormat: RootJsonFormat[ExampleEnum.Value] = EnumJsonFormat(ExampleEnum)
```

### LocalDateTimeJsonFormat
`LocalDateTimeJsonFormat` is a class that provides a JSON format of `LocalDateTime`.
We can use it like below.

```scala mdoc:reset
import lerna.http.json.LocalDateTimeJsonFormat
import spray.json.RootJsonFormat
import java.time.LocalDateTime

implicit val format: RootJsonFormat[LocalDateTime] = LocalDateTimeJsonFormat("yyyy/MM/dd_HH:mm:ss")
```

### SnakifiedSprayJsonSupport
`SnakifiedSprayJsonSupport` provides a custom version of the *spray-json*'s `DefaultJsonProtocol` with a modified field naming strategy.
It makes us use a snake case as a field naming strategy.
We can use this object (or trait) like below.

```scala
import lerna.http.json.SnakifiedSprayJsonSupport._
import spray.json._

final case class MyClass(val myValue: String)
implicit val testStringJsonFormat: RootJsonFormat[MyClass] = jsonFormat1(MyClass)

val json: JsValue = MyClass("test1").toJson
// == """{"my_value":"test1"}"""
```


## Custom Headers of *Akka HTTP*

### SecretHeader

`SecretHeader` is a trait that provides a custom HTTP header with a credential value or a secret value.
The trait overrides toString for masking the value of this header.

```scala mdoc:reset
import lerna.http.SecretHeader
import akka.http.scaladsl.model.headers._
import scala.util.Try

object MySecretHeader extends ModeledCustomHeaderCompanion[MySecretHeader] {
  override def name: String = "My-Secret"
  override def parse(value: String): Try[MySecretHeader] = Try(new MySecretHeader(value))
}

final class MySecretHeader(secretValue: String)
  extends ModeledCustomHeader[MySecretHeader]
    with SecretHeader {
  override def companion: ModeledCustomHeaderCompanion[MySecretHeader] = MySecretHeader
  override def value(): String                                              = secretValue
  override def renderInRequests(): Boolean                                  = true
  override def renderInResponses(): Boolean                                 = true
}

val headerString: String = MySecretHeader("my-password").toString
// == "My-Secret: ***********"

```


## Custom Directives of *Akka HTTP*

### GenTraceIDDirective
`GenTraceIDDirective` provides custom directive that extract a TraceID from the HTTP Header.
We can use it like below.

```scala mdoc:compile-only
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import lerna.http.directives.GenTraceIDDirective
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

final class MySpec extends AnyWordSpecLike with Matchers with ScalatestRouteTest with GenTraceIDDirective {
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
}
```

### RequestLogDirective

`RequestLogDirective` is a trait that provides custom logging Akka HTTP directives.
It provides two methods.

- `logRequestDirective`: A directive that logs verbose of the request
- `logRequestResultDirective`: A directive that logs verbose of the response

### HttpRequestLoggingSupport
`HttpRequestLoggingSupport` is a trait that provides sending an HTTP request with logging and timeout.
We can use it like below.

```scala mdoc:compile-only
import lerna.http.HttpRequestLoggingSupport
import lerna.log.AppLogging
import lerna.util.trace.{ RequestContext, TraceId }
import lerna.util.tenant.Tenant
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import scala.concurrent.Future
import scala.concurrent.duration._

class MyHttp(val system :ActorSystem) extends HttpRequestLoggingSupport with AppLogging {
  override val scope: String = "my-http"
}

val system = ActorSystem("system")
val myHttp = new MyHttp(system)
val req = HttpRequest()
val timeout: FiniteDuration = 1.second
implicit val ctx = new RequestContext {
  override def traceId: TraceId = TraceId.unknown
  override def tenant: Tenant = new Tenant { override def id = "" }
}
val response: Future[HttpResponse] = myHttp.httpSingleRequestWithAroundLogWithTimeout(req, timeout, useProxy = false, maskLog = identity)
```

### HttpRequestProxySupport
`HttpRequestProxySupport` is a trait that provides generating HTTP proxy settings
The proxy settings can be configured by `com.typesafe.config.Config`.
You should configure the settings if you use an HTTP proxy.
This trait is used by `HttpRequestLoggingSupport`.
