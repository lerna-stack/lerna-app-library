package lerna.util.encryption

import com.typesafe.config.ConfigFactory
import lerna.util.LernaUtilBaseSpec

import scala.util.Failure

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
class EncryptionConfigSpec extends LernaUtilBaseSpec {

  "EncryptionConfig" should {

    "正常系" in {
      val encryptionConfig: EncryptionConfig =
        EncryptionConfig(ConfigFactory.parseString("""
           |lerna.util.encryption {
           | base64-key = "v5LCFG4V1CbJxxPg+WTd8w=="
           | base64-iv = "46A7peszgqN3q/ww4k8lWg=="
           |}
           """.stripMargin))
      val checkResult = encryptionConfig.validate()
      expect(checkResult.isSuccess)
    }
    "位数check" in {
      val encryptionConfig: EncryptionConfig =
        EncryptionConfig(ConfigFactory.parseString("""
           |lerna.util.encryption {
           | base64-key = "12+/ty1=="
           | base64-iv = "46A7peszgqN3q/ww4k8lWg=="
           |}
           """.stripMargin))
      val checkResult = encryptionConfig.validate()
      inside(checkResult) {
        case Failure(exception) =>
          expect {
            exception.getMessage === "Illegal base64-key or base64-iv"
          }
      }
    }
    "類型check" in {
      val encryptionConfig: EncryptionConfig =
        EncryptionConfig(ConfigFactory.parseString("""
           |lerna.util.encryption {
           | base64-key = "v5LCFG4V1CbJxxPg+WTd8w=="
           | base64-iv = "!@#/<>?:?}=="
           |}
           """.stripMargin))
      val checkResult = encryptionConfig.validate()
      inside(checkResult) {
        case Failure(exception) =>
          expect {
            exception.getMessage === "Illegal base64-key or base64-iv"
          }
      }
    }
    "=位置check" in {
      val encryptionConfig: EncryptionConfig =
        EncryptionConfig(ConfigFactory.parseString("""
           |lerna.util.encryption {
           | base64-key = "==12QW++==//"
           | base64-iv = "==12QW++==//"
           |}
           """.stripMargin))
      val checkResult = encryptionConfig.validate()
      inside(checkResult) {
        case Failure(exception) =>
          expect {
            exception.getMessage === "Illegal base64-key or base64-iv"
          }
      }
    }

    "validate the length of the base64-key is 128 bits" in {
      val encryptionConfig: EncryptionConfig =
        EncryptionConfig(ConfigFactory.parseString("""
                                                     |lerna.util.encryption {
                                                     | base64-key = "v5=="
                                                     | base64-iv = "46A7peszgqN3q/ww4k8lWg=="
                                                     |}
           """.stripMargin))
      val checkResult = encryptionConfig.validate()
      inside(checkResult) {
        case Failure(exception) =>
          expect {
            exception.getMessage === "Illegal base64-key: v5=="
          }
      }
    }

    "validate the length of the base64-value is 128 bits" in {
      val encryptionConfig: EncryptionConfig =
        EncryptionConfig(ConfigFactory.parseString("""
                                                     |lerna.util.encryption {
                                                     | base64-key = "v5LCFG4V1CbJxxPg+WTd8w=="
                                                     | base64-iv = "46=="
                                                     |}
           """.stripMargin))
      val checkResult = encryptionConfig.validate()
      inside(checkResult) {
        case Failure(exception) =>
          expect {
            exception.getMessage === "Illegal base64-iv: 46=="
          }
      }
    }

  }
}
