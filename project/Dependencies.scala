import sbt._

// The dependencies for libraries
object Dependencies {

  object Versions {
    val accord                = "0.7.6"
    val airframe              = "20.9.0"
    val akka                  = "2.6.8"
    val akkaHTTP              = "10.2.4"
    val cassandraDriverCore   = "3.11.0"
    val kamonCore             = "2.1.18"
    val kamonSystemMetrics    = "2.1.18"
    val logbackClassic        = "1.2.3"
    val scalaTest             = "3.1.4"
    val scalactic             = "3.1.4"
    val scalaXML              = "1.2.0"
    val scalaCollectionCompat = "2.4.4"
    val slf4jAPI              = "1.7.30"
    val typesafeConfig        = "1.4.0"
    val wireMock              = "2.30.1"
  }

  object Typesafe {
    lazy val config = "com.typesafe" % "config" % Versions.typesafeConfig
  }

  object Akka {
    lazy val actor                = "com.typesafe.akka" %% "akka-actor"                 % Versions.akka
    lazy val actorTyped           = "com.typesafe.akka" %% "akka-actor-typed"           % Versions.akka
    lazy val stream               = "com.typesafe.akka" %% "akka-stream"                % Versions.akka
    lazy val slf4j                = "com.typesafe.akka" %% "akka-slf4j"                 % Versions.akka
    lazy val testKit              = "com.typesafe.akka" %% "akka-testkit"               % Versions.akka
    lazy val actorTestKitTyped    = "com.typesafe.akka" %% "akka-actor-testkit-typed"   % Versions.akka
    lazy val streamTestKit        = "com.typesafe.akka" %% "akka-stream-testkit"        % Versions.akka
    lazy val serializationJackson = "com.typesafe.akka" %% "akka-serialization-jackson" % Versions.akka
  }

  object AkkaHTTP {
    val http        = "com.typesafe.akka" %% "akka-http"            % Versions.akkaHTTP
    val sprayJson   = "com.typesafe.akka" %% "akka-http-spray-json" % Versions.akkaHTTP
    val httpTestKit = "com.typesafe.akka" %% "akka-http-testkit"    % Versions.akkaHTTP
  }

  object Kamon {
    val core          = "io.kamon" %% "kamon-core"           % Versions.kamonCore
    val systemMetrics = "io.kamon" %% "kamon-system-metrics" % Versions.kamonSystemMetrics
  }

  object SLF4J {
    lazy val api = "org.slf4j" % "slf4j-api" % Versions.slf4jAPI
  }

  object Logback {
    lazy val classic = "ch.qos.logback" % "logback-classic" % Versions.logbackClassic
  }

  object Scalactic {
    lazy val scalactic = "org.scalactic" %% "scalactic" % Versions.scalactic
  }

  object ScalaLang {
    // これを依存に追加しないと Scalactic の Requirements でランタイムエラーになる
    lazy val scalaXml = "org.scala-lang.modules" %% "scala-xml" % Versions.scalaXML
    // For cross building
    lazy val scalaCollectionCompat =
      "org.scala-lang.modules" %% "scala-collection-compat" % Versions.scalaCollectionCompat
  }

  object ScalaTest {
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest
  }

  object Airframe {
    val airframe = "org.wvlet.airframe" %% "airframe" % Versions.airframe
  }

  object DataStax {
    // TODO DataStaxを直接使用しない or DataStax 4.x に移行する。
    val cassandraDriverCore = "com.datastax.cassandra" % "cassandra-driver-core" % Versions.cassandraDriverCore
  }

  object Accord {
    val core = "com.wix" %% "accord-core" % Versions.accord
  }

  object WireMock {
    val wireMock = "com.github.tomakehurst" % "wiremock-jre8" % Versions.wireMock
  }

  object WartRemover {
    val core = "org.wartremover" %% "wartremover" % wartremover.Wart.PluginVersion
  }

}

// The dependencies which is used only for internal tests.
// We can use an unstable version for test purposes.
object TestDependencies {
  object Versions {
    val expecty                 = "0.14.1"
    val scalaTestPlusScalaCheck = "3.1.4.0"
  }

  object Expecty {
    lazy val expecty = "com.eed3si9n.expecty" %% "expecty" % Versions.expecty
  }

  object ScalaTestPlus {
    val scalaCheck = "org.scalatestplus" %% "scalacheck-1-14" % Versions.scalaTestPlusScalaCheck
  }

}
