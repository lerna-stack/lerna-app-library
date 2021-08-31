package lerna.util.security

import lerna.util.LernaUtilBaseSpec

object SecretValSpec {
  final case class ExampleValue(password: String) extends AnyVal
  final case class ExampleCaseClass(token: String, password: String)
  final case class ExampleContainer(name: String, auth: SecretVal[ExampleValue])
}

class SecretValSpec extends LernaUtilBaseSpec {
  import SecretValSpec._
  import lerna.util.security.SecretVal._

  "SecretVal" should {

    "プリミティブがマスキングされる" in {
      expect {
        100.asSecret.toString === "***"
      }
    }

    "AnyVal がマスキングされる" in {
      expect {
        ExampleValue("password").asSecret.toString === "ExampleValue(********)"
      }
    }

    "case class がマスキングされる" in {
      expect {
        ExampleCaseClass("token", "password").asSecret.toString === "ExampleCaseClass(*****, ********)"
      }
    }

    "None は None のまま表示される" in {
      expect {
        None.asSecret.toString === "None"
      }
    }

    "Some は マスキングされる" in {
      expect {
        Option("password").asSecret.toString === "Some(********)"
      }
    }

    "機密情報のみマスキングされる" in {
      val masked = ExampleContainer(name = "_name_", ExampleValue("_password_").asSecret).toString
      expect(masked.contains("_name_"))
      expect(!masked.contains("_password_"))
    }
  }
}
