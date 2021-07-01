# lerna-app-library

A library that is useful for building applications that run on Lerna Stack.

## Modules

- [**lerna-http**](doc/lerna-http.md)
    - *Lerna HTTP* library provides HTTP related features
- [**lerna-log**](doc/lerna-log.md)
    - *Lerna Log* library provides logging related features
- [**lerna-management**](doc/lerna-management.md)
    - *Lerna Management* library provides management related features
- [**lerna-testkit**](doc/lerna-testkit.md)
    - *Lerna TestKit* library provides testkits for [Akka], [Airframe], and [WireMock]
- [**lerna-util**](doc/lerna-util.md)
    - *Lerna Util* library provides some utilities for Encryption, Typed Equals, and so on
- [**lerna-util-akka**](doc/lerna-util-akka.md)
    - *Lerna Util Akka* library provides some utilities related [Akka Classic]
- [**lerna-util-sequence**](doc/lerna-util-sequence.md)
    - *Lerna Util Sequence* library provides an ID generator
- [**lerna-validation**](doc/lerna-validation.md)
    - *Lerna Validation* library provides custom validators for [Accord]
- [**lerna-wart-core**](doc/lerna-wart-core.md)
    - *Lerna Wart Core* library provides custom warts for [WartRemover]
    
The above modules are tested with OpenJDK8 and OpenJDK11.

[Accord]: http://wix.github.io/accord/
[Akka]: https://doc.akka.io/docs/akka/current/
[Akka Classic]: https://doc.akka.io/docs/akka/current/index-classic.html
[Airframe]: https://wvlet.org/airframe/
[WireMock]: http://wiremock.org/
[WartRemover]: https://www.wartremover.org/

## Getting Started

To use these library modules, you must add dependencies into your sbt project, add the following lines to your `build.sbt` file:

```scala
val LernaVersion = "1.0.0"

libraryDependencies += "com.lerna-stack" %% "lerna-http"          % LernaVersion
libraryDependencies += "com.lerna-stack" %% "lerna-log"           % LernaVersion
libraryDependencies += "com.lerna-stack" %% "lerna-management"    % LernaVersion
libraryDependencies += "com.lerna-stack" %% "lerna-testkit"       % LernaVersion
libraryDependencies += "com.lerna-stack" %% "lerna-util"          % LernaVersion
libraryDependencies += "com.lerna-stack" %% "lerna-util-akka"     % LernaVersion
libraryDependencies += "com.lerna-stack" %% "lerna-util-sequence" % LernaVersion
libraryDependencies += "com.lerna-stack" %% "lerna-validation"    % LernaVersion
libraryDependencies += "com.lerna-stack" %% "lerna-wart-core"     % LernaVersion
```

## Changelog

You can see all the notable changes in [CHANGELOG](CHANGELOG.md).

## Migration guide

You can see the migration guide in [migration-guide.md](doc/migration-guide.md).

## License

lerna-app-library is released under the terms of the [Apache License Version 2.0](LICENSE).

© 2020 TIS Inc.
