package lerna.util.encryption

import java.util.Base64

import javax.crypto.Cipher
import javax.crypto.spec.{ IvParameterSpec, SecretKeySpec }

/** An object that provides AES encryption/decryption features
  */
object AesEncryptor {
  private final val CIPHER_ALGORITHM = "AES"
  private final val CIPHER_MODE      = "CBC"
  private final val CIPHER_PADDING   = "PKCS5Padding"
  private final val TRANSFORMATION   = String.format("%s/%s/%s", CIPHER_ALGORITHM, CIPHER_MODE, CIPHER_PADDING)

  /** Encrypt the given string and return an encrypted string
    *
    * ==Warnings==
    * This method doesn't check whether the given configuration is valid or not.
    * This method may behave unexpectedly if you pass the invalid configuration.
    *
    * @param param The string to be encrypted
    * @param config The encryption configuration
    * @return The encrypted string
    */
  def encrypt(param: String)(implicit config: EncryptionConfig): String = {
    val cipher: Cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(
      Cipher.ENCRYPT_MODE,
      new SecretKeySpec(Base64.getDecoder.decode(config.key), CIPHER_ALGORITHM),
      new IvParameterSpec(Base64.getDecoder.decode(config.iv)),
    )
    Base64.getEncoder.encodeToString(cipher.doFinal(param.getBytes()))
  }

  /** Decrypt the given encrypted string and return a decrypted string
    *
    * ==Warnings==
    * This method doesn't check whether the given configuration is valid or not.
    * This method may behave unexpectedly if you pass the invalid configuration.
    *
    * @param param The encrypted string to be decrypted
    * @param config The decryption configuration
    * @return The decrypted string
    */
  def decrypt(param: String)(implicit config: EncryptionConfig): String = {
    val cipher: Cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(
      Cipher.DECRYPT_MODE,
      new SecretKeySpec(Base64.getDecoder.decode(config.key), CIPHER_ALGORITHM),
      new IvParameterSpec(Base64.getDecoder.decode(config.iv)),
    )
    new String(cipher.doFinal(Base64.getDecoder.decode(param.getBytes())))
  }
}
