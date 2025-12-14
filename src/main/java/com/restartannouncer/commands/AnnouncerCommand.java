package com.restartannouncer.commands;

import com.restartannouncer.RestartAnnouncerPlugin;
import com.restartannouncer.managers.RestartManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnnouncerCommand implements CommandExecutor, TabCompleter {
    
    private final RestartAnnouncerPlugin plugin;
    
    public AnnouncerCommand(RestartAnnouncerPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                sendHelp((Player) sender);
            } else {
                sendConsoleHelp(sender);
            }
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        // Handle start and stop commands for both console and players
        if (subcommand.equals("start")) {
            handleStart(sender, args);
            return true;
        }
        
        if (subcommand.equals("stop")) {
            handleStop(sender);
            return true;
        }
        
        // All commands now work for both console and players
        switch (subcommand) {
            case "stop":
                handleStop(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "update":
                handleUpdate(sender);
                break;
            case "toggle":
                handleToggle(sender);
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "help":
                if (sender instanceof Player) {
                    sendHelp((Player) sender);
                } else {
                    sendConsoleHelp(sender);
                }
                break;
            default:
                if (sender instanceof Player) {
                    plugin.getMessageManager().sendError((Player) sender, "Unknown subcommand: " + subcommand);
                    sendHelp((Player) sender);
                } else {
                    sender.sendMessage(plugin.getMessageManager().formatMessage("§cUnknown subcommand: " + subcommand));
                    sendConsoleHelp(sender);
                }
                break;
        }
        
        return true;
    }
    
        private void handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission(plugin.getConfigManager().getStartPermission())) {
            sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("start", "no-permission")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("start", "usage")));
            sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("start", "example")));
            return;
        }

        // Parse restart time
        int restartSeconds;
        try {
            restartSeconds = RestartManager.parseTime(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("start", "invalid-time")));
            return;
        }

        // Parse announcement interval
        int intervalSeconds = plugin.getConfigManager().getDefaultAnnouncementInterval();
        if (args.length >= 3) {
            try {
                intervalSeconds = RestartManager.parseTime(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("start", "invalid-interval")));
                return;
            }
        }

        // Parse display type
        String displayType = "chat"; // Default to chat
        if (args.length >= 4) {
            displayType = args[3].toLowerCase();
            if (!isValidDisplayType(displayType)) {
                sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("start", "invalid-display")));
                return;
            }
        }

        // Check if restart is already running
        if (plugin.getRestartManager().isRunning()) {
            sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("start", "already-running")));
            return;
        }

        // Start the restart
        if (plugin.getRestartManager().startRestart(restartSeconds, intervalSeconds, displayType)) {
            Map<String, String> placeholders = plugin.getMessageManager().createPlaceholders(
                "time", formatTime(restartSeconds),
                "interval", formatTime(intervalSeconds),
                "display", displayType
            );
            sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("start", "success", placeholders)));
        } else {
            sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("start", "already-running")));
        }
    }

        private boolean isValidDisplayType(String displayType) {
        return displayType.equals("chat") ||
               displayType.equals("bossbar") ||
               displayType.equals("title");
    }
    
    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfigManager().getStopPermission())) {
            sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("stop", "no-permission")));
            return;
        }
        
        if (!plugin.getRestartManager().isRunning()) {
            sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("stop", "not-running")));
            return;
        }
        
        plugin.getRestartManager().stopRestart();
        sender.sendMessage(plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("stop", "success")));
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfigManager().getStatusPermission())) {
            String message = plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("status", "no-permission"));
            if (sender instanceof Player) {
                plugin.getMessageManager().sendError((Player) sender, plugin.getMessageManager().getCommandMessage("status", "no-permission"));
            } else {
                sender.sendMessage(message);
            }
            return;
        }
        
        if (plugin.getRestartManager().isRunning()) {
            Map<String, String> placeholders = plugin.getMessageManager().createPlaceholders(
                "time", plugin.getRestartManager().getTimeRemainingFormatted()
            );
            String message = plugin.getMessageManager().getCommandMessage("status", "running", placeholders);
            if (sender instanceof Player) {
                plugin.getMessageManager().sendInfo((Player) sender, message);
            } else {
                sender.sendMessage(plugin.getMessageManager().formatMessage(message));
            }
        } else {
            String message = plugin.getMessageManager().getCommandMessage("status", "not-running");
            if (sender instanceof Player) {
                plugin.getMessageManager().sendInfo((Player) sender, message);
            } else {
                sender.sendMessage(plugin.getMessageManager().formatMessage(message));
            }
        }
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfigManager().getReloadPermission())) {
            String message = plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("reload", "no-permission"));
            if (sender instanceof Player) {
                plugin.getMessageManager().sendError((Player) sender, plugin.getMessageManager().getCommandMessage("reload", "no-permission"));
            } else {
                sender.sendMessage(message);
            }
            return;
        }
        
        plugin.getConfigManager().reloadConfig();
        plugin.getMessageManager().reloadMessages();
        
        String message = plugin.getMessageManager().getCommandMessage("reload", "success");
        if (sender instanceof Player) {
            plugin.getMessageManager().sendSuccess((Player) sender, message);
        } else {
            sender.sendMessage(plugin.getMessageManager().formatMessage(message));
        }
    }
    
    private void handleUpdate(CommandSender sender) {
        if (!sender.hasPermission("announcer.update")) {
            String message = "§cYou don't have permission to use this command!";
            if (sender instanceof Player) {
                plugin.getMessageManager().sendError((Player) sender, message);
            } else {
                sender.sendMessage(message);
            }
            return;
        }
        
        if (sender instanceof Player) {
            plugin.checkForUpdatesManually((Player) sender);
        } else {
            plugin.checkForUpdatesManually();
            sender.sendMessage("Checking for updates...");
        }
    }

    private void handleToggle(CommandSender sender) {
        if (!sender.hasPermission(plugin.getConfigManager().getReloadPermission())) {
            String message = plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("reload", "no-permission"));
            if (sender instanceof Player) {
                plugin.getMessageManager().sendError((Player) sender, plugin.getMessageManager().getCommandMessage("reload", "no-permission"));
            } else {
                sender.sendMessage(message);
            }
            return;
        }

        boolean currentSetting = plugin.getConfigManager().shouldExecuteShutdown();
        boolean newSetting = !currentSetting;
        
        plugin.getConfig().set("execute-shutdown", newSetting);
        plugin.saveConfig();
        
        String status = newSetting ? "enabled" : "disabled";
        String toggleMessage = "§aExecute shutdown " + status + ". Run §e/announcer reload§a to apply changes.";
        if (sender instanceof Player) {
            plugin.getMessageManager().sendSuccess((Player) sender, toggleMessage);
        } else {
            sender.sendMessage(plugin.getMessageManager().formatMessage(toggleMessage));
        }
    }
    
    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission(plugin.getConfigManager().getReloadPermission())) {
            String message = plugin.getMessageManager().formatMessage(plugin.getMessageManager().getCommandMessage("reload", "no-permission"));
            if (sender instanceof Player) {
                plugin.getMessageManager().sendError((Player) sender, plugin.getMessageManager().getCommandMessage("reload", "no-permission"));
            } else {
                sender.sendMessage(message);
            }
            return;
        }
        
        if (args.length < 3) {
            String errorMsg = "§cUsage: /announcer set message <message>";
            if (sender instanceof Player) {
                plugin.getMessageManager().sendError((Player) sender, errorMsg);
            } else {
                sender.sendMessage(plugin.getMessageManager().formatMessage(errorMsg));
            }
            return;
        }
        
        if (!args[1].equalsIgnoreCase("message")) {
            String errorMsg = "§cUsage: /announcer set message <message>";
            if (sender instanceof Player) {
                plugin.getMessageManager().sendError((Player) sender, errorMsg);
            } else {
                sender.sendMessage(plugin.getMessageManager().formatMessage(errorMsg));
            }
            return;
        }
        
        // Join all arguments after "set message" to form the complete message
        String newMessage = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        
        if (newMessage.trim().isEmpty()) {
            String errorMsg = "§cMessage cannot be empty!";
            if (sender instanceof Player) {
                plugin.getMessageManager().sendError((Player) sender, errorMsg);
            } else {
                sender.sendMessage(plugin.getMessageManager().formatMessage(errorMsg));
            }
            return;
        }
        
        if (plugin.getMessageManager().setRestartMessage(newMessage)) {
            String successMsg = "§aRestart message updated successfully!";
            String infoMsg = "§7New message: " + plugin.getMessageManager().formatMessage(newMessage);
            if (sender instanceof Player) {
                plugin.getMessageManager().sendSuccess((Player) sender, successMsg);
                plugin.getMessageManager().sendInfo((Player) sender, infoMsg);
            } else {
                sender.sendMessage(plugin.getMessageManager().formatMessage(successMsg));
                sender.sendMessage(plugin.getMessageManager().formatMessage(infoMsg));
            }
        } else {
            String errorMsg = "§cFailed to update restart message. Check console for errors.";
            if (sender instanceof Player) {
                plugin.getMessageManager().sendError((Player) sender, errorMsg);
            } else {
                sender.sendMessage(plugin.getMessageManager().formatMessage(errorMsg));
            }
        }
    }
    
    private void sendHelp(Player player) {
        plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "header"));
        
        // Only show help for commands the player has permission to use
        if (player.hasPermission(plugin.getConfigManager().getStartPermission())) {
            plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "start"));
        }
        if (player.hasPermission(plugin.getConfigManager().getStopPermission())) {
            plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "stop"));
        }
        if (player.hasPermission(plugin.getConfigManager().getStatusPermission())) {
            plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "status"));
        }
        if (player.hasPermission(plugin.getConfigManager().getReloadPermission())) {
            plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "toggle"));
            plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "set"));
        }
        if (player.hasPermission("announcer.update")) {
            plugin.getMessageManager().sendInfo(player, "§e/announcer update §7- Check for plugin updates");
        }
        // Help is always available
        plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "help"));
    }

    private void sendConsoleHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "header"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "start"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "stop"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "status"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "toggle"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "set"));
        sender.sendMessage("§e/announcer update §7- Check for plugin updates");
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "help"));
    }
    
    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            int hours = seconds / 3600;
            return hours + " hour" + (hours != 1 ? "s" : "");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }
        
        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            
            // Only show commands the player has permission to use
            if (player.hasPermission(plugin.getConfigManager().getStartPermission())) {
                subcommands.add("start");
            }
            if (player.hasPermission(plugin.getConfigManager().getStopPermission())) {
                subcommands.add("stop");
            }
            if (player.hasPermission(plugin.getConfigManager().getStatusPermission())) {
                subcommands.add("status");
            }
            if (player.hasPermission(plugin.getConfigManager().getReloadPermission())) {
                subcommands.add("reload");
                subcommands.add("toggle");
                subcommands.add("set");
            }
            if (player.hasPermission("announcer.update")) {
                subcommands.add("update");
            }
            // Help is always available
            subcommands.add("help");
            
            for (String subcommand : subcommands) {
                if (subcommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            
            if ("start".equals(subcommand) && player.hasPermission(plugin.getConfigManager().getStartPermission())) {
                completions.addAll(Arrays.asList("5m", "10m", "15m", "30m", "1h", "2h"));
            } else if ("set".equals(subcommand) && player.hasPermission(plugin.getConfigManager().getReloadPermission())) {
                completions.add("message");
            }
        } else if (args.length == 3) {
            String subcommand = args[0].toLowerCase();
            
            if ("start".equals(subcommand) && player.hasPermission(plugin.getConfigManager().getStartPermission())) {
                completions.addAll(Arrays.asList("30s", "60s", "2m", "5m"));
            } else if ("set".equals(subcommand) && player.hasPermission(plugin.getConfigManager().getReloadPermission())) {
                // Show current restart message as tab completion
                String currentMessage = plugin.getMessageManager().getMessage("restart-message");
                if (currentMessage != null && !currentMessage.isEmpty()) {
                    completions.add(currentMessage);
                }
            }
        } else if (args.length == 4) {
            String subcommand = args[0].toLowerCase();
            
            if ("start".equals(subcommand) && player.hasPermission(plugin.getConfigManager().getStartPermission())) {
                completions.addAll(Arrays.asList("chat", "bossbar", "title"));
            }
        }
        
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
} 