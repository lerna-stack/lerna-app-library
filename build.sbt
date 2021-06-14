import sbt.Def

import scala.util.Try

name := "lerna-app-library"

lazy val scala212               = "2.12.12"
lazy val scala213               = "2.13.4"
lazy val supportedScalaVersions = List(scala213, scala212)

lazy val `root` = (project in file("."))
  .settings(
    crossScalaVersions := Nil,
    publish / skip := true,
  )
  .enablePlugins(ScalaUnidocPlugin)
  .settings(unidocSettings)
  .disablePlugins(ProtocPlugin)
  .aggregate(
    lernaDocs,
    lernaHTTP,
    lernaLog,
    lernaManagement,
    lernaTestKit,
    lernaTests,
    lernaUtil,
    lernaUtilAkka,
    lernaUtilSequence,
    lernaValidation,
    lernaWartCore,
  )
  .settings(
    inThisBuild(
      List(
        version := "1.0.0",
        scalaVersion := scala213,
        scalacOptions ++= Seq(
          "-deprecation",
          "-feature",
          "-unchecked",
          "-Xlint",
        ),
        crossScalaVersions := supportedScalaVersions,
        scalacOptions in lernaWartCore in Test -= "-Xlint", // test:compile が未使用チェックに引っかかるため無効化
        scalacOptions ++= sys.env
          .get("SBT_SCALAC_STRICT_WARNINGS").filter(_ == "true").map(_ => "-Xfatal-warnings").toSeq,
        fork in run := sys.props
          .get("fork")
          .flatMap(s => Try(s.toBoolean).toOption)
          .getOrElse(false),
        fork in Test := true,                   // ~test で繰り返しテストできるようにするため
        javaOptions in Test ++= sbtJavaOptions, // fork先にはシステムプロパティが引き継がれないため
        // forkプロセスのstdoutをこのプロセスのstdout,stderrをこのプロセスのstderrに転送する
        // デフォルトのLoggedOutputでは、airframeやkamonが標準エラーに出力するログが[error]とプリフィクスがつき、紛らわしいためです。
        outputStrategy := Some(StdoutOutput),
        mimaPreviousArtifacts := Set.empty, // default
        mimaReportSignatureProblems := true,// check also generic parameters
      ),
    ),
  )

lazy val wartremoverSettings = Def.settings(
  wartremoverClasspaths ++= {
    (fullClasspath in (lernaWartCore, Compile)).value.map(_.data.toURI.toString)
  },
  // Warts.Unsafe をベースにカスタマイズ
  wartremoverErrors in (Compile, compile) := Seq(
    // Wart.Any,                        // Warts.Unsafe: Akka の API で Any が使われるため
    Wart.AsInstanceOf,            // Warts.Unsafe
    Wart.EitherProjectionPartial, // Warts.Unsafe
    Wart.IsInstanceOf,            // Warts.Unsafe
    // Wart.NonUnitStatements,          // Warts.Unsafe: 誤検知が多く、回避しようとすると煩雑なコードが必要になる
    Wart.Null,          // Warts.Unsafe
    Wart.OptionPartial, // Warts.Unsafe
    Wart.Product,       // Warts.Unsafe
    Wart.Return,        // Warts.Unsafe
    Wart.Serializable,  // Warts.Unsafe
    Wart.StringPlusAny, // Warts.Unsafe
    // Wart.Throw,                      // Warts.Unsafe: Future を失敗させるときに使うことがある
    Wart.TraversableOps,         // Warts.Unsafe
    Wart.TryPartial,             // Warts.Unsafe
    Wart.Var,                    // Warts.Unsafe
    Wart.ArrayEquals,            // Array の比較は sameElements を使う
    Wart.AnyVal,                 // 異なる型のオブジェクトを List などに入れない
    Wart.Equals,                 // == の代わりに === を使う
    Wart.ExplicitImplicitTypes,  // implicit val には明示的に型を指定する
    Wart.FinalCaseClass,         // case class は継承しない
    Wart.JavaConversions,        // scala.collection.JavaConverters を使う
    Wart.OptionPartial,          // Option#get は使わずに fold などの代替を使う
    Wart.Recursion,              // 単純な再帰処理は使わずに末尾最適化して @tailrec を付けるかループ処理を使う
    Wart.TraversableOps,         // head の代わりに headOption など例外を出さないメソッドを使う
    Wart.TryPartial,             // Success と Failure の両方をハンドリングする
    ContribWart.MissingOverride, // ミックスインしたトレイトと同じメソッドやプロパティを宣言するときは必ず override をつける
    ContribWart.OldTime,         // Java 8 の新しい Date API を使う
    ContribWart.SomeApply,       // Some(...) の代わりに Option(...) を使う
    CustomWart.Awaits,
    CustomWart.CyclomaticComplexity,
    CustomWart.NamingClass,
    CustomWart.NamingDef,
    CustomWart.NamingObject,
    CustomWart.NamingPackage,
    CustomWart.NamingVal,
    CustomWart.NamingVar,
  ),
  wartremoverErrors in (Test, compile) := (wartremoverErrors in (Compile, compile)).value,
  wartremoverErrors in (Test, compile) --= Seq(
    CustomWart.CyclomaticComplexity,
  ),
)

def sbtJavaOptions: Seq[String] =
  sys.props
    .filterNot { case (key, _) => excludeSbtJavaOptions.exists(key.startsWith) }.map { case (k, v) => s"-D$k=$v" }.toSeq

// 前方一致で除外
lazy val excludeSbtJavaOptions = Set(
  "os.",
  "user.",
  "java.",
  "sun.",
  "awt.",
  "jline.",
  "jna.",
  "jnidispatch.",
  "sbt.",
)

//
// Library Module
// TODO Make own wartremover settings

lazy val lernaCoverageSettings = Def.settings(
  coverageMinimumStmtTotal := 80,
  coverageFailOnMinimum := false,
  coverageExcludedPackages := Seq(
    // Exclude auto generated code by ScalaPB
    """scalapb\..*""",
    """lerna\.util\.akka\.protobuf\.msg\..*""",
  ).mkString(";"),
)

// Unidoc
lazy val unidocSettings = Seq(
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(lernaTests),
)

// DocTest Settings
//   CI is failed if omit the `doctestScalaTestVersion`.
doctestScalaTestVersion := Some(Dependencies.Versions.scalaTest)
doctestTestFramework := com.github.tkawachi.doctest.DoctestPlugin.DoctestTestFramework.ScalaTest
val doctestSettings = Seq(
  libraryDependencies ++= Seq(
    Dependencies.ScalaTest.scalaTest          % Test,
    TestDependencies.ScalaTestPlus.scalaCheck % Test,
  ),
  // Exclude managed source from WartRemover targets
  wartremoverExcluded += sourceManaged.value,
)

// ScalaPB ( for Google Protocol Buffer support )
lazy val scalapbSettings = Seq(
  PB.targets in Compile := Seq(
    scalapb.gen(flatPackage = true, lenses = false, grpc = false) -> (sourceManaged in Compile).value / "scalapb",
  ),
  wartremoverExcluded += sourceManaged.value,
)

// Mima Previous Artifacts
lazy val lernaMimaPreviousArtifacts: Def.Initialize[Set[ModuleID]] = Def.setting {
  val previousStableVersionOpt =
    if (VersionNumber(scalaVersion.value).matchesSemVer(SemanticSelector(">=2.13"))) {
      previousStableVersion.value.filter(VersionNumber(_).matchesSemVer(SemanticSelector(">=2.0.0")))
    } else {
      previousStableVersion.value
    }
  previousStableVersionOpt.map(organization.value %% moduleName.value % _).toSet
}

def lernaModule(name: String): Project =
  Project(id = name, base = file(name))
    .settings(
      testOptions += Tests.Argument(
        TestFrameworks.ScalaTest,
        "-u",
        (crossTarget.value / "test-reports").getPath,
      ),
    )

// Lerna Document
lazy val lernaDocs = lernaModule("lerna-docs")
  .settings(publish / skip := true)
  .enablePlugins(MdocPlugin)
  .disablePlugins(ProtocPlugin)
  .dependsOn(
    lernaHTTP,
    lernaLog,
    lernaManagement,
    lernaTestKit,
    lernaUtil,
    lernaUtilAkka,
    lernaUtilSequence,
    lernaValidation,
    lernaWartCore,
  )
  .settings(
    mdocIn := {
      baseDirectory.value.toPath.resolve("../doc").toFile
    },
    // Define own scala compiler options for writing docs easily
    scalacOptions := Seq(),
    libraryDependencies ++= Seq(
      Dependencies.Akka.testKit,
      Dependencies.Akka.actorTestKitTyped,
      Dependencies.Akka.streamTestKit,
      Dependencies.AkkaHTTP.httpTestKit,
      Dependencies.Airframe.airframe,
      Dependencies.WireMock.wireMock,
    ),
  )

// Lerna Custom Wart of WartRemover
lazy val lernaWartCore = lernaModule("lerna-wart-core")
  .settings(mimaPreviousArtifacts := lernaMimaPreviousArtifacts.value)
  .settings(lernaCoverageSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.WartRemover.core,
      Dependencies.ScalaTest.scalaTest % Test,
    ),
  )

// Lerna Testkit Library
lazy val lernaTestKit = lernaModule("lerna-testkit")
  .settings(mimaPreviousArtifacts := lernaMimaPreviousArtifacts.value)
  .disablePlugins(ProtocPlugin)
  .settings(wartremoverSettings, lernaCoverageSettings, doctestSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.ScalaTest.scalaTest,
      Dependencies.Scalactic.scalactic,
      Dependencies.ScalaLang.scalaXml,
      Dependencies.Akka.testKit           % Optional,
      Dependencies.Akka.actorTestKitTyped % Optional,
      Dependencies.Airframe.airframe      % Optional,
      Dependencies.WireMock.wireMock      % Optional,
      Dependencies.Akka.stream            % Test,
      Dependencies.AkkaHTTP.http          % Test,
      Dependencies.AkkaHTTP.httpTestKit   % Test,
      TestDependencies.Expecty.expecty    % Test,
    ),
  )

// Lerna Test Library (Internal Use Only)
// This project is intended for internal use.
lazy val lernaTests = lernaModule("lerna-tests")
  .settings(publish / skip := true)
  .disablePlugins(ProtocPlugin)
  .dependsOn(
    lernaTestKit,
  )
  .settings(wartremoverSettings, lernaCoverageSettings)
  .settings(
    libraryDependencies ++= Seq(
      TestDependencies.Expecty.expecty,
      Dependencies.Akka.testKit,
    ),
  )

lazy val lernaManagement = lernaModule("lerna-management")
  .settings(mimaPreviousArtifacts := lernaMimaPreviousArtifacts.value)
  .disablePlugins(ProtocPlugin)
  .dependsOn(
    lernaTests % Test,
    lernaLog,
    lernaUtil,
  )
  .settings(wartremoverSettings, lernaCoverageSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Akka.actorTyped,
      Dependencies.Kamon.core,
      Dependencies.Kamon.systemMetrics,
      Dependencies.Akka.actorTestKitTyped % Test,
    ),
  )

// Lerna Log Library
lazy val lernaLog = lernaModule("lerna-log")
  .settings(mimaPreviousArtifacts := lernaMimaPreviousArtifacts.value)
  .disablePlugins(ProtocPlugin)
  .dependsOn(
    lernaTests % Test,
  )
  .settings(wartremoverSettings, lernaCoverageSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.ScalaLang.scalaCollectionCompat,
      Dependencies.SLF4J.api,
      Dependencies.Akka.slf4j,
      Dependencies.Akka.actorTyped % Optional,
      Dependencies.Logback.classic % Optional,
    ),
  )

// Lerna Util Library
lazy val lernaUtil = lernaModule("lerna-util")
  .settings(mimaPreviousArtifacts := lernaMimaPreviousArtifacts.value)
  .disablePlugins(ProtocPlugin)
  .dependsOn(
    lernaTests % Test,
    lernaLog,
  )
  .settings(wartremoverSettings, lernaCoverageSettings, doctestSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Scalactic.scalactic,
      Dependencies.ScalaLang.scalaXml,
      Dependencies.Typesafe.config,
    ),
  )

// Lerna Akka Util Library
lazy val lernaUtilAkka = lernaModule("lerna-util-akka")
  .settings(mimaPreviousArtifacts := lernaMimaPreviousArtifacts.value)
  .dependsOn(
    lernaTests % Test,
    lernaUtil,
    lernaLog,
  )
  .settings(wartremoverSettings, lernaCoverageSettings, scalapbSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Akka.actor,
      Dependencies.Akka.actorTyped,
      Dependencies.Akka.stream,
      Dependencies.Akka.testKit              % Test,
      Dependencies.Akka.actorTestKitTyped    % Test,
      Dependencies.Akka.streamTestKit        % Test,
      Dependencies.Akka.serializationJackson % Test,
    ),
  )

// Lerna Sequence Factory Library
lazy val lernaUtilSequence = lernaModule("lerna-util-sequence")
  .settings(mimaPreviousArtifacts := lernaMimaPreviousArtifacts.value)
  .disablePlugins(ProtocPlugin)
  .dependsOn(
    lernaTests % Test,
    lernaUtil,
    lernaLog,
  )
  .settings(wartremoverSettings, lernaCoverageSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.DataStax.cassandraDriverCore,
      Dependencies.Akka.actor,
      Dependencies.Akka.testKit % Test,
    ),
  )

// Lerna HTTP Library
lazy val lernaHTTP = lernaModule("lerna-http")
  .settings(mimaPreviousArtifacts := lernaMimaPreviousArtifacts.value)
  .disablePlugins(ProtocPlugin)
  .dependsOn(
    lernaTests % Test,
    lernaLog,
    lernaUtil,
  )
  .settings(wartremoverSettings, lernaCoverageSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Akka.stream,
      Dependencies.AkkaHTTP.http,
      Dependencies.AkkaHTTP.sprayJson,
      Dependencies.Akka.testKit         % Test,
      Dependencies.AkkaHTTP.httpTestKit % Test,
    ),
  )

lazy val lernaValidation = lernaModule("lerna-validation")
  .settings(mimaPreviousArtifacts := lernaMimaPreviousArtifacts.value)
  .disablePlugins(ProtocPlugin)
  .dependsOn(
    lernaTests % Test,
    lernaUtil,
  )
  .settings(wartremoverSettings, lernaCoverageSettings, doctestSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Accord.core,
    ),
  )

addCommandAlias("take-test-coverage", "clean;coverage;test:compile;test;coverageReport;coverageAggregate;")
