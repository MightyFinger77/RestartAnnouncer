package com.restartannouncer.managers;

import com.restartannouncer.RestartAnnouncerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    
    private final RestartAnnouncerPlugin plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    
    public MessageManager(RestartAnnouncerPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }
    
    public void reloadMessages() {
        loadMessages();
    }
    
    public boolean setRestartMessage(String newMessage) {
        try {
            messagesConfig.set("restart-message", newMessage);
            messagesConfig.save(messagesFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save restart message: " + e.getMessage());
            return false;
        }
    }
    
    public String getMessage(String path) {
        return getMessage(path, "");
    }
    
    public String getMessage(String path, String defaultValue) {
        String message = messagesConfig.getString(path, defaultValue);
        if (message == null || message.isEmpty()) {
            return defaultValue;
        }
        return message;
    }
    
    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return message;
    }
    
    // Convenience methods
    public String getCommandMessage(String command, String type) {
        return getMessage("commands." + command + "." + type);
    }
    
    public String getCommandMessage(String command, String type, Map<String, String> placeholders) {
        return getMessage("commands." + command + "." + type, placeholders);
    }
    
    public String getRestartMessage(String timeRemaining) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("time", timeRemaining);
        return getMessage("restart-message", placeholders);
    }
    
    // Helper method to create placeholder map
    public Map<String, String> createPlaceholders(String... keyValuePairs) {
        Map<String, String> placeholders = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i + 1 < keyValuePairs.length) {
                placeholders.put(keyValuePairs[i], keyValuePairs[i + 1]);
            }
        }
        return placeholders;
    }
    
    // Message sending methods
    public void sendMessage(Player player, String message) {
        String formattedMessage = formatMessage(message);
        player.sendMessage(formattedMessage);
    }
    
    public void broadcastMessage(String message) {
        String formattedMessage = formatMessage(message);
        Bukkit.broadcastMessage(formattedMessage);
    }
    
    public void sendError(Player player, String message) {
        sendMessage(player, message);
    }
    
    public void sendSuccess(Player player, String message) {
        sendMessage(player, message);
    }
    
    public void sendInfo(Player player, String message) {
        sendMessage(player, message);
    }
    
    public String formatMessage(String message) {
        // Convert MiniMessage tags to legacy format
        return message
            .replace("<red>", "§c")
            .replace("<dark_red>", "§4")
            .replace("<blue>", "§b")
            .replace("<dark_blue>", "§1")
            .replace("<aqua>", "§b")
            .replace("<dark_aqua>", "§3")
            .replace("<green>", "§a")
            .replace("<dark_green>", "§2")
            .replace("<yellow>", "§e")
            .replace("<gold>", "§6")
            .replace("<purple>", "§d")
            .replace("<dark_purple>", "§5")
            .replace("<white>", "§f")
            .replace("<gray>", "§7")
            .replace("<dark_gray>", "§8")
            .replace("<black>", "§0")
            .replace("<bold>", "§l")
            .replace("<italic>", "§o")
            .replace("<underline>", "§n")
            .replace("<strikethrough>", "§m")
            .replace("<reset>", "§r");
    }
} 