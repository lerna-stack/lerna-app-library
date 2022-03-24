# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
[Unreleased]: https://github.com/lerna-stack/lerna-app-library/compare/v3.0.1...main

## [v3.0.1] - 2022-03-24
[v3.0.1]: https://github.com/lerna-stack/lerna-app-library/compare/v3.0.0...v3.0.1

### Fixed
- `lerna-util-sequence`
  - `logback.xml` is not loaded when `lerna-util-seuquence` is in dependencies [#77](https://github.com/lerna-stack/lerna-app-library/issues/77)

## [v3.0.0] - 2021-10-22
[v3.0.0]: https://github.com/lerna-stack/lerna-app-library/compare/v2.0.0...v3.0.0

### Changed
- Update `wiremock-jre8` to `2.30.1` from `2.27.2`
- `lerna-util-sequence`
    - Use [Alpakka Cassandra 2.0.2](https://doc.akka.io/docs/alpakka/2.0.2/cassandra.html)
      instead of [DataStax Java Driver for Cassandra 3.7.1](https://docs.datastax.com/en/developer/java-driver/3.7/).  
      This change includes the DataStax Java Driver upgrade to [4.6.1](https://docs.datastax.com/en/developer/java-driver/4.6/).  
      We have to migrate code and settings. See [migration guide](doc/migration-guide.md#300-from-200).  
      Note that the upgrade doesn't break already-persisted data since the Cassandra database schema to use is not changed.  
      Though we have to migrate code and settings, we can continue to use already-persisted data.

### Fixed
- `lerna-util-sequence`
    - Reserving an unnecessary extra sequence value when resetting [PR#65](https://github.com/lerna-stack/lerna-app-library/pull/65)
    - Can not generating sequence number correctly on corner cases [#49](https://github.com/lerna-stack/lerna-app-library/issues/49)
    - Reserving an unnecessary extra sequence value when there are not enough sequence values in stock [PR#57(comment)](https://github.com/lerna-stack/lerna-app-library/pull/57#discussion_r713544755)

## [v2.0.0] - 2021-07-16
[v2.0.0]: https://github.com/lerna-stack/lerna-app-library/compare/v1.0.0...v2.0.0

### Fixed
- `lerna-http`
    - Fixed an issue where HTTP Body was not logged
    - Fixed an issue where URL queries were not logged
- `lerna-log`
    - Fixed an issue where existing mdc was ignored when logging
    - Fixed an issue where existing mdc was deleted after log output

### Added
- `lerna-testkit`: Added testkit for TypedActor
- `lerna-log`: Added Logger for TypedActor
- `lerna-util-akka`: Added `AtLeastOnceDelivery` API for TypedActor 
- Java11 support

### Changed
- `lerna-management`
  - Update to `Kamon 2.1.8` from `Kamon 1.1.6`
  - Remove the `kamon-system-metrics` dependency as it is not required for everyone. If you are using it, you need to add a dependency.
  - Improve documentation
  - Provide [migration guide](doc/migration-guide.md)
- Update to `ScalaTest 3.1.4` from `ScalaTest 3.0.9`
- Deprecate API for classic Actor. The API for classic Actor will be removed in the next major version upgrade.

## [v1.0.0] - 2020-12-22
[v1.0.0]: https://github.com/lerna-stack/lerna-app-library/tree/v1.0.0

- Initial release
