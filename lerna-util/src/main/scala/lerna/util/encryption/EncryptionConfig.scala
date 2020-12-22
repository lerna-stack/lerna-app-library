package lerna.util.encryption

import java.util.Base64

import com.typesafe.config.Config
import lerna.util.lang.Equals._

import scala.util.{ Failure, Success, Try }

/** The encryption configuration of [[AesEncryptor]]
  *
  * @param rootConfig The configuration instance
  */
final case class EncryptionConfig(private val rootConfig: Config) {
  private val config = rootConfig.getConfig("lerna.util.encryption")

  /** The symmetric key (128bit) that is represented as a base64 string
    */
  val key: String = config.getString("base64-key")

  /** The initial vector (128bit) that is represented as a base64 string
    */
  val iv: String = config.getString("base64-iv")

  /** Validate this instance contains valid `key` and `iv`
    *
    * @return [[scala.util.Success]] if the instance is valid, [[scala.util.Failure]] containing error information otherwise.
    */
  def validate(): Try[Unit] = {
    base64Check(key) match {
      case true if base64Check(iv) =>
        key match {
          case _ if Base64.getDecoder.decode(key.getBytes()).length !== 16 =>
            Failure(new IllegalStateException(s"Illegal base64-key: $key"))
          case _ if Base64.getDecoder.decode(iv.getBytes()).length !== 16 =>
            Failure(new IllegalStateException(s"Illegal base64-iv: $iv"))
          case _ => Success(())
        }
      case _ =>
        Failure(new IllegalStateException(s"Illegal base64-key or base64-iv"))
    }

  }

  private def base64Check(str: String): Boolean = {
    val base64Pattern = "^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)$"
    str.matches(base64Pattern)
  }
}
