# Changelog

## 1.1.0-Dev1a (2026-03-19)

### Added
- Scheduled restart **recurrence**: `DAILY` (default) and `WEEKLY` with optional `interval-weeks` (e.g. biweekly) and `week-anchor-date`. See `config.yml` under `scheduled-restart`.
- Added Folia metadata and compatibility baseline in this version line.
- Created a dedicated versioned copy to preserve previous latest release behavior.

### Changed
- Standardized release metadata and version identifiers for this Folia-targeted build.
- Aligned update notes/docs structure for consistent release management.

### Notes
- This release is intended as a safe Folia-forward branch and avoids destructive rewrites in legacy branches.

---

All notable changes to the RestartAnnouncer plugin will be documented in this file.

## [1.0.4] - 12/14/2025

### Changed
- Improved dev-to-dev version comparison logic (e.g., Dev1a → Dev1b, Dev3b → Dev4a)
- Updated plugin.yml to use `${project.version}` placeholder for automatic version management

## [1.0.3] - 12/14/2025

### Added
- Config migration system to preserve user settings when updating
- Update checker system with dev version support
- Version tracking for config and messages files

## [1.0.2] - TBD

### Added
- Enhanced MiniMessage gradient support for Purpur/Paper servers
- Component-based message broadcasting for better gradient rendering

### Changed
- Updated to use Paper API natively for improved Adventure API integration
- Improved message sending to use Components directly instead of legacy format conversion
- Enhanced debugging output for MiniMessage parsing

### Technical
- Switched from Spigot API to Paper API dependency
- Added individual player messaging for chat display type to preserve gradient information
- Enhanced MessageManager with Component-based methods

## [1.0.1] - 2025-08-08

### Added
- **Tiered Announcement Timing**: Implemented dynamic announcement intervals based on remaining time:
  - Over 1 minute: Uses the original user-specified interval
  - Under 1 minute: Announces every 10 seconds
  - Under 30 seconds: Announces every 5 seconds  
  - Under 10 seconds: Announces every 1 second

### Changed
- Updated version to 1.0.1 for maintenance/public release
- Improved build process and dependency management
- Enhanced announcement system for better user experience during 'critical' countdown periods

### Fixed
- **BossBar Progress Calculation**: Fixed NaN error when calculating boss bar progress for very short countdowns
- **Time Parsing**: Fixed issue where seconds-based times (like "30s") were not being parsed correctly
- **Tiered Timing Logic**: Ensured proper interval switching under 10 seconds for user intervals input lower than 'critical' time

### Technical
- Cleaned up build artifacts
- Updated to Paper API for better performance and features
- Verified compatibility with Paper 1.20.4+
- Ensured proper Maven shade plugin configuration

## [1.0.0] - Private Release

### Added
- Initial version of RestartAnnouncer plugin
- Configurable restart announcements
- Multiple announcement methods (chat, bossbar, title)
- In-game configuration management
- Flexible timing options
- Permission-based access control
