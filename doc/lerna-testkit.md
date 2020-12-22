# Lerna TestKit

*Lerna TestKit* library provides testkits for
- [Akka Classic](https://doc.akka.io/docs/akka/current/index-classic.html)
- [Airframe](https://wvlet.org/airframe/)
- [WireMock](http://wiremock.org/)


## A TestKit for *Akka Classic TestKit*

If you use *Akka TestKit* related features, You need to add `akka-testkit` into `libraryDependencies` like the following.
```sbt
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.6.8" % Test
```

### ScalaTestWithClassicActorTestKit

`ScalaTestWithClassicActorTestKit` provides integration of *ScalaTest* and *Akka Classic TestKit*.
This class provides similar features provided by [ScalaTestWithActorTestKit](https://doc.akka.io/api/akka/2.6/akka/actor/testkit/typed/scaladsl/ScalaTestWithActorTestKit.html) of *Akka Typed*.
You can use this class like below.

```scala mdoc:reset
import akka.actor._
import akka.testkit._
import org.scalatest._
import lerna.testkit.akka.ScalaTestWithClassicActorTestKit

final class MySpec
    extends ScalaTestWithClassicActorTestKit(ActorSystem("my-spec"))
    with WordSpecLike with Matchers {
  "ScalaTestWithClassicActorTestKit" should {
    "provide the ActorSystem in a test" in {
      val actor = system.actorOf(TestActors.echoActorProps)
      actor ! "hello"
      expectMsg("hello")
    }
  }
}
```


## A TestKit for *Airframe*

If you use *Airframe* related features, You need to add `airframe` into `libraryDependencies` like the following.
```sbt
libraryDependencies += "org.wvlet.airframe" %% "airframe" % "20.9.0" % Test
```

### DISessionSupport

`DISessionSupport` provides an Airframe DI session in a test suit.
You can use this trait like below.

```scala mdoc:reset
import org.scalatest._
import wvlet.airframe._
import lerna.testkit.airframe.DISessionSupport

class ExampleComponent() {
  def echo(msg: String): String = msg
}
final class MySpec extends WordSpecLike with Matchers with DISessionSupport {
  override val diDesign: Design = newDesign
    .bind[ExampleComponent].toSingleton

  "ExampleComponent" should {
    val component = diSession.build[ExampleComponent]
    "echo the message back" in {
      component.echo("hello") shouldBe "hello"
    }
  }
}
```

## WireMock
If you use *WireMock* related features, You need to add `wiremock-jre8` into `libraryDependencies` like the following.
```sbt
libraryDependencies += "com.github.tomakehurst" % "wiremock-jre8" % "2.27.2" % Test
```

### ExternalServiceMock

`ExternalServiceMock` provides a WireMockServer.
You should take care of closing the instance of `ExternalSercviceMock` if you don't need it anymore.
Since `ExternalServiceMock` extends `java.lang.AutoCloseable`, you can use it with a Loan Pattern, or [Airframe Life Cycle](https://wvlet.org/airframe/docs/airframe#life-cycle).

The below code shows how to use `ExternalServiceMock`.

```scala mdoc:compile-only
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse}
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.{ get, ok, urlEqualTo }
import scala.concurrent.Future
import lerna.testkit.wiremock.ExternalServiceMock

class MyService extends ExternalServiceMock {
  override protected lazy val https: Boolean    = false
  override protected lazy val host: String      = "127.0.0.1"
  override protected lazy val port: Option[Int] = Option(9000)
}

// Create a mock server
val service = new MyService()

// Import a stub into the mock server
val ping: MappingBuilder = get(urlEqualTo("/ping")).willReturn(ok("pong"))
service.importStubs(ping)

// Send a request
implicit val system = ActorSystem("mock-server-test")
val request        = HttpRequest(uri = "http://127.0.0.1:9000/ping")
val responseFuture: Future[HttpResponse] = Http().singleRequest(request)

// Close the mock server
service.close()
```
