# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Not applicable

### Changed
- Correct XML errors in French translation.

### Removed
- Not applicable

## [1.1.8] 2017-09-12
### Changed
- Build changed to compile dependency rather than import jar file.

## [1.1.7] - 2017-09-08
### Added
- Add French translation. Thanks to @Massedil

### Changed
- Move change log into separate file.
- Update build tools and gradle

## [1.1.6] - 2016-08-10
### Added
- Now supports backup/export of new/changed data in addition to full backup/export.

### Changed
- New database schema (probably want to backup/export existing data before upgrading just in case).
- Various other internal changes and fixes to work toward goal of supporting other types of RF sources for position estimation.

## [1.1.5] - 2016-07-30
### Changed
- Add permission to write to external storage so export data will work.

## [1.1.4] - 2016-06-23
### Changed
- Fix calculation cache to miss less often.

## [1.1.3] - 2016-06-23
### Changed
- Improve performance on often used distance calculations

## [1.1.2] - 2016-06-22
### Changed
- Refactor some files and logic. Should be no user discernible change in operation.

## [1.1.1] - 2016-05-13
### Changed
- Fix divide by zero on minimum signal strength.

## [1.1.0] - 2016-05-05
### Changed
- Change import/export format to comma separated value (CSV) format.

## [1.0.2] - 2016-03-23
### Changed
- Update for revised UnifiedNlp with aging of reports.

## [1.0.0] - 2016-01-06
### Added
- Thanks to @pejakm, update Serbian translation

## [0.9.9] - 2016-01-16
### Added
- Thanks to @UnknownUntilNow, new UI, refactored code, import and export of WiFi AP location information, support for Marshmallow

## [0.17.0] 2015-08-21
### Changed
- |21Aug2015|Increase location uncertainty if no position found.

## [0.6.1]
### Changed
- Fix up Android Studio/Gradle build environment

## [0.6.0]
### Added
- Configurable settings for data collection and use.

### Changed
- Some improvements in performance

## [0.1.0]
### Added
- Initial version
