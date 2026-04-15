# Restart Announcer

Highly configurable **restart countdowns** for **Paper** and **Folia**: chat, boss bar, or title announcements, optional **scheduled** daily/weekly restarts with reminders, and **MiniMessage** in `messages.yml`.


**Command:** `/announcer` · **Aliases:** `/ra`, `/restartannouncer`

## Requirements

- **Minecraft** 1.20+ (Paper API)
- **Java 17+**
- **Paper** or a compatible fork (**Folia** supported — `folia-supported: true`)

## Features

- **Manual countdowns** — `/announcer start` with time, interval, and display mode (`chat`, `bossbar`, `title`)
- **Scheduled restarts** — wall-clock **DAILY** or **WEEKLY** restarts, optional hour-based reminders, optional **wait for backup** + delay after backup finishes
- **Shutdown control** — `execute-shutdown: true` stops the server at zero; `false` = announcements only (your script/watchdog must reboot)
- **Shutdown method** — `shutdown`, `stop`, or `restart` (see config comments; default `shutdown` is recommended)
- **In-game management** — toggle execute-shutdown, set restart message, reload config/messages
- **Update checker** — optional; `announcer.update` for `/announcer update`
- **Configurable permissions** — permission node strings in `config.yml` map to your permission plugin

## Commands

| Command | Description |
|--------|-------------|
| **`/announcer start <time> [interval] [display]`** | Start a countdown. **time** / **interval**: e.g. `10m`, `30s`, `1h`. **display**: `chat` (default), `bossbar`, `title`. |
| **`/announcer stop`** | Cancel the active countdown |
| **`/announcer status`** | Show whether a restart is running and time left |
| **`/announcer reload`** | Reload `config.yml`, `messages.yml`, and re-apply scheduled restart from config |
| **`/announcer toggle`** | Flip `execute-shutdown` in `config.yml` (reload afterward to fully sync behavior) |
| **`/announcer set message <message>`** | Set the main restart announcement (MiniMessage); persists to `messages.yml` |
| **`/announcer update`** | Check for plugin updates (requires `announcer.update`) |
| **`/announcer help`** | Context-sensitive help (players only see subcommands they can use) |

**Examples**

- `/announcer start 10m` — 10 minutes, default interval, chat  
- `/announcer start 30m 2m bossbar` — 30 minutes, announce every 2 minutes on boss bar  
- `/announcer start 30s 2s title` — 30 seconds, every 2 seconds as title  

## Permissions

Defined in `plugin.yml` (defaults shown). You can point config keys at custom nodes if your permission plugin uses different names.

| Permission | Default | Purpose |
|------------|---------|---------|
| `announcer.use` | `true` | Base command access |
| `announcer.start` | op | Start countdowns |
| `announcer.stop` | op | Stop countdowns |
| `announcer.status` | op | View status |
| `announcer.reload` | op | Reload, toggle, `set message` |
| `announcer.update` | op | `/announcer update` |
| `announcer.admin` | op | Parent node grouping the above |

`config.yml` also lists the strings used for start/stop/status/reload checks (defaults match the table).

## Configuration

### `config.yml`

- **`config_version`** — bumped when defaults change; keep your file when updating and merge new keys from the jar if needed.
- **`update-checker`** — enable/disable update checks on startup.
- **`defaults`** — `restart-time` (minutes), `announcement-interval` (seconds) when omitted from `/announcer start`.
- **`scheduled-restart`** — `enabled`, `time` (`HHmm` 24h), `recurrence` (`DAILY` / `WEEKLY`), `day-of-week`, `interval-weeks`, `week-anchor-date` (for multi-week weekly), `reminder-interval-hours`, `wait-for-backup`, `wait-for-backup-delay`.
- **`shutdown-method`** — `shutdown` | `stop` | `restart`
- **`execute-shutdown`** — `true` to stop server at end of countdown; `false` for announcements only.
- **`permissions`** — override permission node strings for start/stop/status/reload.

Example skeleton:

```yaml
config_version: 4

update-checker:
  enabled: true

defaults:
  restart-time: 10
  announcement-interval: 60

scheduled-restart:
  enabled: false
  time: "0400"
  recurrence: DAILY
  day-of-week: SUNDAY
  interval-weeks: 1
  week-anchor-date: "2026-01-04"
  reminder-interval-hours: 4
  wait-for-backup: true
  wait-for-backup-delay: 60

shutdown-method: "shutdown"
execute-shutdown: true

permissions:
  start: "announcer.start"
  stop: "announcer.stop"
  status: "announcer.status"
  reload: "announcer.reload"
```

### `messages.yml`

- **`messages_version`** — do not change unless you know what you’re doing.
- **`restart-message`** — `%time%` = time remaining.
- **`scheduled-restart.reminder`** — `%time%`, `%timezone%` for scheduled notices.
- **`scheduled-restart.backup-delayed`** — when restart waits on a backup.
- **`commands.*`** — MiniMessage strings for usage, errors, and help.

```yaml
messages_version: 3

restart-message: "<red><bold>Server will restart in <yellow>%time%<red>!"

scheduled-restart:
  reminder: "<yellow>Next scheduled restart: <white>%time% <gray>(%timezone%)"
  backup-delayed: "<yellow>Restart delayed – backup in progress. Will restart when backup completes."

commands:
  start:
    usage: "<red>Usage: /announcer start <time> <interval> [display]"
    example: "<gray>Example: /announcer start 10m 60s chat"
    invalid-display: "<red>Invalid display type. Use: chat, bossbar, title"
  # … stop, status, reload, help …
```

## Installation

1. Download the built **JAR** (`mvn package`) or a release artifact.  
2. Place it in the server’s `plugins` folder.  
3. Start the server once to generate `config.yml` and `messages.yml`.  
4. Edit config/messages, then `/announcer reload` (or restart).  
5. Use `/announcer help` in-game for allowed subcommands.

### Updating

- **Back up** `plugins/RestartAnnouncer/config.yml` and `messages.yml`.  
- Replace the JAR and start the server.  
- Compare your files with the defaults in the new jar; add any **new keys** (e.g. under `scheduled-restart` or `update-checker`) instead of deleting the whole folder unless you want a full reset.  
- After large jumps, verify `config_version` / `messages_version` and scheduled-restart fields match the new defaults.

## Time formats

Examples: `30s`, `60s`, `5m`, `10m`, `30m`, `1h`, `2h`, `6h` (used for start time and announcement interval).

## Shutdown methods

- **`shutdown`** (default) — `Bukkit.shutdown()`  
- **`stop`** — intended to align with `/stop`-style behavior (see in-jar comments)  
- **`restart`** — `/restart`; may not exist on all setups — use with care  

## Announcement-only mode

```yaml
execute-shutdown: false
```

Announces as usual; at **0** the server **does not** stop. Use an external script or panel to restart after players are warned.

## MiniMessage

`messages.yml` supports **MiniMessage** (e.g. `<red>`, `<yellow>`, `<bold>`, `<reset>`, gradients). Legacy `§` codes may still appear in a few hard-coded admin strings; prefer MiniMessage in YAML.

## Support

Issues and suggestions: use your project’s issue tracker or Modrinth/GitHub as applicable.

---

*Plugin does **not** restart the process by itself when the OS exits — pair with a start script, systemd, or watchdog that brings the server back up.*
