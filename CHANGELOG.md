# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
[Unreleased]: https://github.com/lerna-stack/lerna-app-library/compare/v1.0.0...main

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
  - Improve documentation
  - Provide [migration guide](doc/migration-guide.md)
- Update to `ScalaTest 3.1.4` from `ScalaTest 3.0.9`

## [v1.0.0] - 2020-12-22
[v1.0.0]: https://github.com/lerna-stack/lerna-app-library/tree/v1.0.0

- Initial release
