package lerna.util.encryption

import com.typesafe.config.{ Config, ConfigFactory }
import lerna.util.LernaUtilBaseSpec

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
class AesEncryptorSpec extends LernaUtilBaseSpec {
  val config: Config                                      = ConfigFactory.parseString("""
                                           |lerna.util.encryption {
                                           | base64-key = "v5LCFG4V1CbJxxPg+WTd8w=="
                                           | base64-iv = "46A7peszgqN3q/ww4k8lWg=="
                                           |}
                                           |""".stripMargin)
  implicit private val encryptionConfig: EncryptionConfig = EncryptionConfig(config)

  "AesEncryptor" should {
    "暗号化・復号できる" in {
      val src       = "abcdefg123456\\[]@\"!"
      val encrypted = AesEncryptor.encrypt(src)
      val decrypted = AesEncryptor.decrypt(encrypted)
      expect(decrypted === src)
    }
  }
}
