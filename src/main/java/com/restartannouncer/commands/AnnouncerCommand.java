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
        
        // Other commands require a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("commands.player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        switch (subcommand) {
            case "start":
                handleStart(player, args);
                break;
            case "stop":
                handleStop(player);
                break;
            case "status":
                handleStatus(player);
                break;
            case "reload":
                handleReload(player);
                break;
            case "toggle":
                handleToggle(player);
                break;
            case "set":
                handleSet(player, args);
                break;
            case "help":
                sendHelp(player);
                break;
            default:
                plugin.getMessageManager().sendError(player, "Unknown subcommand: " + subcommand);
                sendHelp(player);
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

    private void handleStop(Player player) {
        if (!player.hasPermission(plugin.getConfigManager().getStopPermission())) {
            plugin.getMessageManager().sendError(player, plugin.getMessageManager().getCommandMessage("stop", "no-permission"));
            return;
        }
        
        if (!plugin.getRestartManager().isRunning()) {
            plugin.getMessageManager().sendError(player, plugin.getMessageManager().getCommandMessage("stop", "not-running"));
            return;
        }
        
        plugin.getRestartManager().stopRestart();
        plugin.getMessageManager().sendSuccess(player, plugin.getMessageManager().getCommandMessage("stop", "success"));
    }
    
    private void handleStatus(Player player) {
        if (!player.hasPermission(plugin.getConfigManager().getStatusPermission())) {
            plugin.getMessageManager().sendError(player, plugin.getMessageManager().getCommandMessage("status", "no-permission"));
            return;
        }
        
        if (plugin.getRestartManager().isRunning()) {
            Map<String, String> placeholders = plugin.getMessageManager().createPlaceholders(
                "time", plugin.getRestartManager().getTimeRemainingFormatted()
            );
            plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("status", "running", placeholders));
        } else {
            plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("status", "not-running"));
        }
    }
    
    private void handleReload(Player player) {
        if (!player.hasPermission(plugin.getConfigManager().getReloadPermission())) {
            plugin.getMessageManager().sendError(player, plugin.getMessageManager().getCommandMessage("reload", "no-permission"));
            return;
        }
        
        plugin.getConfigManager().reloadConfig();
        plugin.getMessageManager().reloadMessages();
        plugin.getMessageManager().sendSuccess(player, plugin.getMessageManager().getCommandMessage("reload", "success"));
    }

    private void handleToggle(Player player) {
        if (!player.hasPermission(plugin.getConfigManager().getReloadPermission())) {
            plugin.getMessageManager().sendError(player, plugin.getMessageManager().getCommandMessage("reload", "no-permission"));
            return;
        }

        boolean currentSetting = plugin.getConfigManager().shouldExecuteShutdown();
        boolean newSetting = !currentSetting;
        
        plugin.getConfig().set("execute-shutdown", newSetting);
        plugin.saveConfig();
        
        String status = newSetting ? "enabled" : "disabled";
        plugin.getMessageManager().sendSuccess(player, "§aExecute shutdown " + status + ". Run §e/announcer reload§a to apply changes.");
    }
    
    private void handleSet(Player player, String[] args) {
        if (!player.hasPermission(plugin.getConfigManager().getReloadPermission())) {
            plugin.getMessageManager().sendError(player, plugin.getMessageManager().getCommandMessage("reload", "no-permission"));
            return;
        }
        
        if (args.length < 3) {
            plugin.getMessageManager().sendError(player, "§cUsage: /announcer set message <message>");
            return;
        }
        
        if (!args[1].equalsIgnoreCase("message")) {
            plugin.getMessageManager().sendError(player, "§cUsage: /announcer set message <message>");
            return;
        }
        
        // Join all arguments after "set message" to form the complete message
        String newMessage = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        
        if (newMessage.trim().isEmpty()) {
            plugin.getMessageManager().sendError(player, "§cMessage cannot be empty!");
            return;
        }
        
        if (plugin.getMessageManager().setRestartMessage(newMessage)) {
            plugin.getMessageManager().sendSuccess(player, "§aRestart message updated successfully!");
            plugin.getMessageManager().sendInfo(player, "§7New message: " + plugin.getMessageManager().formatMessage(newMessage));
        } else {
            plugin.getMessageManager().sendError(player, "§cFailed to update restart message. Check console for errors.");
        }
    }
    
    private void sendHelp(Player player) {
        plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "header"));
        plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "start"));
        plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "stop"));
        plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "status"));
        plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "toggle"));
        plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "set"));
        plugin.getMessageManager().sendInfo(player, plugin.getMessageManager().getCommandMessage("help", "help"));
    }

    private void sendConsoleHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "header"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "start"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "stop"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "status"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "toggle"));
        sender.sendMessage(plugin.getMessageManager().getCommandMessage("help", "set"));
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
            List<String> subcommands = Arrays.asList("start", "stop", "status", "reload", "toggle", "set", "help");
            
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