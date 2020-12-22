# Lerna Util

*Lerna Util* library provides some utilities like below.

- Encryption
- Typed Equals
- Security
- Date and Time

## Encryption

### AesEncryptor

`AesEncryptor` provides AES encryption/decryption features.  
We can use it with `EncryptionConfig` like below.

```scala mdoc:compile-only
import com.typesafe.config.{ Config, ConfigFactory }
import lerna.util.encryption.{ AesEncryptor, EncryptionConfig }

val config: Config= ConfigFactory.parseString("""|
                                               |lerna.util.encryption {
                                               | base64-key = "v5LCFG4V1CbJxxPg+WTd8w=="
                                               | base64-iv = "46A7peszgqN3q/ww4k8lWg=="
                                               |}
                                               |""".stripMargin)

implicit private val encryptionConfig: EncryptionConfig = EncryptionConfig(config)

val src: String = "abcdefg123456\\[]@\"!"
val encrypted: String = AesEncryptor.encrypt(src)
val decrypted: String = AesEncryptor.decrypt(encrypted)
```

## Typed Equals

`Equals` is a trait that provides a `CheckingEqualizer` for any type.
It just overrides some methods of `org.scalactic.TypeCheckedTripleEquals`.
You can use it like below.

```scala mdoc:reset
import lerna.util.lang.Equals._

val isTrue: Boolean = 1 === 1
val isFalse: Boolean = 1 === 2
// Cannot compile
// val cannotCompile: Boolean = 1 === 2.0
```

## Security

### SecretVal

`SecretVal` is a class that represents confidential information.
`toString` of this class returns a masked value.
It is useful for avoiding to log the confidential information accidentally.
We can use the class like below.

```scala mdoc:reset
import lerna.util.security.SecretVal
import lerna.util.security.SecretVal._

val secretValue1: SecretVal[String] = SecretVal("my-secret")
val secretValue2: SecretVal[String] = "123".asSecret

println(secretValue1)
// "*********" will be printed.
println(secretValue2)
// "***" will be printed.
```

## Date and Time

### DateTimeConverters
`DateTimeConverter` is an object that provides extension methods related to date-time API.
You can use it like below.

```scala mdoc:reset
import lerna.util.time.DateTimeConverters._
import java.sql.Timestamp
import java.time.LocalDateTime

val localDateTime: LocalDateTime = LocalDateTime.of(2020, 10, 27, 10, 57, 45, 123456789)
val timestamp: Timestamp = localDateTime.asTimestamp
```

### JavaDurationConverters
`JavaDurationConverters` is an object that provides extension methods related to `java.time.Duration` and `scala.concurrent.duration.Duration`.
You can convert between Java Duration and Scala Duration like below.

```scala mdoc:reset
import lerna.util.time.JavaDurationConverters._
import java.time.{ Duration => JavaDuration }
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

val javaDuration: JavaDuration = JavaDuration.ofNanos(1)
val scalaDurationFromJava: FiniteDuration = javaDuration.asScala

val scalaDuration: FiniteDuration = FiniteDuration(1, TimeUnit.NANOSECONDS)
val javaDurationFromScala: JavaDuration = scalaDuration.asJava

```

### LocalDateTimeFactory

`LocalDateTimeFactory` is a trait that provides factory methods of `LocalDateTime`.
We can use it like below.

```scala mdoc:reset
import lerna.util.time.LocalDateTimeFactory
import java.time.LocalDateTime

val factory = LocalDateTimeFactory()
val currentDateTime: LocalDateTime = factory.now()
```

`LocalDateTimeFactory` is useless in itself, but by using `FixedLocalDateTimeFactory` it is useful in tests.
`FixedLocalDateTimeFactory` is an implementation of `LocalDateTimeFactory` that returns fixed time based on the given clock.
Therefore, using `LocalDateTimeFactory` and `FixedLocalDateTimeFactory`, we can inject a fixed date and time in tests.

```scala mdoc:reset
import lerna.util.time.LocalDateTimeFactory
import lerna.util.time.FixedLocalDateTimeFactory

class Target(timeFacotry: LocalDateTimeFactory)

// In runtime
val factoryInRuntime: LocalDateTimeFactory = LocalDateTimeFactory()
val targetInRuntime = new Target(factoryInRuntime)

// In test
val factoryInTest: LocalDateTimeFactory = FixedLocalDateTimeFactory("2019-05-01T00:00:00Z")
val targetInTest = new Target(factoryInTest)

```
