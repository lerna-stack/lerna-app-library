addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")

addSbtPlugin("org.duhemm" % "sbt-errors-summary" % "0.6.3")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.10")

addSbtPlugin("org.wartremover" % "sbt-wartremover-contrib" % "1.3.1")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.8.1")

// ScalaPB
addSbtPlugin("com.thesamet"                    % "sbt-protoc"     % "1.0.0-RC3")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.8"

// Documentation
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "2.0.0")
addSbtPlugin("com.github.tkawachi"               % "sbt-doctest"      % "0.9.7")
addSbtPlugin("com.eed3si9n"                      % "sbt-unidoc"       % "0.4.3")
addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.2.12")

// publish jar
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")
addSbtPlugin("com.jsuereth"   % "sbt-pgp"      % "2.0.1")

// Compatibility check
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")
addSbtPlugin("com.dwijnand" % "sbt-dynver"      % "4.1.1")
