# RestartAnnouncer

A simple and easy-to-use Minecraft server restart announcement plugin for Paper servers.

**Version:** 1.0.1

## Features

- **Server Shutdown**: Shuts down your server at the end of the countdown NOTE: Plugin does not handle restart, users start script or watchdog script must handle the restart!
- **Restart Annoucing**: Sets a time until restart and announces time left to warn server players
- **Simple Commands**: Easy-to-use commands with tab completion
- **Customizable Messages**: All messages can be edited in `messages.yml`
- **Flexible Timing**: Set restart time and announcement intervals
- **In-Game Configuration**: Everything can be managed through commands
- **This is useful for server owners that want to schedule restarts and warn their players so they can get to a safe spot**

## Commands

**`/announcer start <time> [interval] [display]`** - Start a restart countdown
- **time**: How long until restart (e.g., `10m`, `30m`, `1h`)
- **interval**: How often to send announcements (e.g., `60s`, `2m`) - optional, defaults to 60 seconds
- **display**: Where to show the announcements (`chat`, `bossbar`, `title`) - optional, defaults to `chat`

**`/announcer stop`** - Cancel the current restart countdown

**`/announcer status`** - Check if a restart is currently running and see time remaining

**`/announcer reload`** - Reload the plugin configuration and messages

**`/announcer toggle`** - Toggle the execute-shutdown setting. When disabled, the plugin will only send announcements without stopping the server

**`/announcer set message <message>`** - Set the restart announcement message

**`/announcer help`** - Show help information

**Examples:**
- `/announcer start 10m` - Restart in 10 minutes, announce every 60 seconds in chat
- `/announcer start 30m 2m bossbar` - Restart in 30 minutes, announce every 2 minutes on the boss bar
- `/announcer start 30s 2s title` - Restart in 30 seconds, announce every 2 seconds as title

## Permissions

- `announcer.start` - Permission to start restarts
- `announcer.stop` - Permission to stop restarts  
- `announcer.status` - Permission to check status
- `announcer.reload` - Permission to reload plugin

## Configuration

### config.yml
```yaml
# Default settings
defaults:
  # Default restart time if not specified (in minutes)
  restart-time: 10
  # Default announcement interval if not specified (in seconds)
  announcement-interval: 60

# Shutdown method
# Options: "shutdown" (uses Bukkit.shutdown()), "stop" (uses /stop command), "restart" (uses /restart command)
shutdown-method: "shutdown"

# Execute shutdown
# Set to false to only send announcements without stopping the server
# If set to false, your batch/bash start script or a separate watchdog script must handle the server reboot
execute-shutdown: true

# Permissions
permissions:
  start: "announcer.start"
  stop: "announcer.stop"
  status: "announcer.status"
  reload: "announcer.reload"
```

### messages.yml
```yaml
# Main restart message - use %time% for time remaining
restart-message: "<red><bold>Server will restart in <yellow>%time%<red>!"

# Command messages
commands:
  no-permission: "<red>You don't have permission to use this command."
  player-only: "This command can only be used by players."
  
  # Start command
  start:
    usage: "<red>Usage: /announcer start <time> <interval>"
    example: "<gray>Example: /announcer start 10m 60s"
    already-running: "<red>A restart is already in progress!"
    success: "<green>Restart scheduled in %time% with announcements every %interval%"
    invalid-time: "<red>Invalid time format. Use: 5m, 10m, 30m, 1h, etc."
    invalid-interval: "<red>Invalid interval format. Use: 30s, 60s, 2m, etc."
  
  # Stop command
  stop:
    no-permission: "<red>You don't have permission to stop restarts."
    not-running: "<blue>No restart is currently running."
    success: "<green>Restart cancelled."
  
  # Status command
  status:
    running: "<blue>Restart in progress: %time% remaining"
    not-running: "<blue>No restart is currently running."
  
  # Help command
  help:
    header: "<blue>RestartAnnouncer Commands:"
    start: "  /announcer start <time> <interval> - Start a restart countdown"
    stop: "  /announcer stop - Cancel the current restart"
    status: "  /announcer status - Check restart status"
    help: "  /announcer help - Show this help"
  
  # Reload command
  reload:
    no-permission: "<red>You don't have permission to reload the plugin."
    success: "<green>Plugin reloaded successfully."
```

## Installation

1. Download the latest JAR file
2. Place it in your server's `plugins` folder
3. Start/restart your server
4. The plugin will create `config.yml` and `messages.yml` files
5. Edit the configuration files as needed
6. Use `/announcer help` to see available commands

### ⚠️ Important: Config Updates

**When updating the plugin to a new version, it's recommended to:**
1. Stop your server
2. Delete the `RestartAnnouncer` folder from your `plugins` directory
3. Replace the JAR file in the `plugins` folder with the new one
4. Start your server to regenerate fresh config files
5. Reconfigure your settings in the new `config.yml` and `messages.yml` files

This ensures that any new configuration options are properly added and prevents issues with config migration. Save a backup of your messages.yml to avoid having to retype any changes made previously.

## Time Formats

The plugin supports various time formats:

- **Seconds**: `30s`, `60s`
- **Minutes**: `5m`, `10m`, `30m`
- **Hours**: `1h`, `2h`, `6h`

## Shutdown Methods

The plugin supports different ways to stop the server when the countdown reaches zero:

- **`shutdown`** (default): Uses `Bukkit.shutdown()` - the most reliable method
- **`stop`**: Uses the `/stop` command - standard Minecraft command (also uses Bukkit.shutdown(), recommended to use default 'shutdown') 
- **`restart`**: Uses the `/restart` command - may not work on all servers, use with caution

You can configure this in `config.yml`:
```yaml
shutdown-method: "shutdown"  # Options: shutdown, stop, restart
```

## Announcement-Only Mode

You can configure the plugin to only send announcements without actually stopping the server if you want to execute the restart manually:

```yaml
execute-shutdown: false  # Set to false for announcements only
```

When `execute-shutdown` is set to `false`:
- The plugin will send all restart announcements as normal
- When the countdown reaches zero, it will **not** stop the server

## Color Codes

The plugin supports MiniMessage color codes in messages:

- `<red>`, `<blue>`, `<green>`, `<yellow>`, etc.
- `<bold>`, `<italic>`, `<underline>`
- `<reset>` to reset formatting

## Support

If you need help or have suggestions, please open an issue on the GitHub repository. 

