package lerna.http

import akka.http.scaladsl.model.headers.{ ModeledCustomHeader, ModeledCustomHeaderCompanion }

import scala.util.Try

object SecretHeaderSpec {

  object ExampleSecretHeader extends ModeledCustomHeaderCompanion[ExampleSecretHeader] {
    override def name: String                                   = "Example-Secret"
    override def parse(value: String): Try[ExampleSecretHeader] = Try(new ExampleSecretHeader(value))
  }

  final class ExampleSecretHeader(secretValue: String)
      extends ModeledCustomHeader[ExampleSecretHeader]
      with SecretHeader {
    override def companion: ModeledCustomHeaderCompanion[ExampleSecretHeader] = ExampleSecretHeader
    override def value(): String                                              = secretValue
    override def renderInRequests(): Boolean                                  = true
    override def renderInResponses(): Boolean                                 = true
  }
}

class SecretHeaderSpec extends LernaHttpBaseSpec {
  import SecretHeaderSpec._

  "SecretHeader" should {

    "値だけがマスキングされる" in {

      expect {
        ExampleSecretHeader("password").toString === "Example-Secret: ********"
      }
    }
  }
}
