package com.restartannouncer.managers;

import com.restartannouncer.RestartAnnouncerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

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
        
        migrateMessages();
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }
    
    /**
     * Migrate messages.yml to add missing options from newer versions
     * Preserves user values while adding new options and comments
     */
    private void migrateMessages() {
        try {
            // Get the actual messages file
            File configFile = new File(plugin.getDataFolder(), "messages.yml");
            
            if (!configFile.exists()) {
                return; // No migration needed if file doesn't exist
            }
            
            // Load the default config from the jar resource
            InputStream defaultConfigTextStream = plugin.getResource("messages.yml");
            if (defaultConfigTextStream == null) {
                plugin.getLogger().warning("Could not load default messages.yml from jar for migration");
                return;
            }
            
            // Read default config as text to preserve formatting
            List<String> defaultLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(defaultConfigTextStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    defaultLines.add(line);
                }
            }
            
            // Load as YAML to get user values
            InputStream defaultConfigYamlStream = plugin.getResource("messages.yml");
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigYamlStream));
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // Check config version - get default version
            String versionKey = "messages_version";
            int defaultVersion = defaultConfig.getInt(versionKey, 1);
            int currentVersion = currentConfig.getInt(versionKey, 0); // 0 means old config without version
            
            // If versions match and config has version field, no migration needed
            if (currentVersion == defaultVersion && currentConfig.contains(versionKey)) {
                return; // Config is up to date
            }
            
            // Simple merge: Use default structure/comments, replace values with user's where they exist
            // Deprecated keys (in user config but not in default) are automatically removed
            List<String> mergedLines = mergeConfigs(defaultLines, currentConfig, defaultConfig);
            
            // Check for and log deprecated keys that were removed
            Set<String> deprecatedKeys = findDeprecatedKeys(currentConfig, defaultConfig);
            if (!deprecatedKeys.isEmpty()) {
                plugin.getLogger().info("Removed deprecated keys from messages.yml: " + String.join(", ", deprecatedKeys));
            }
            
            // Update config version
            updateConfigVersion(mergedLines, defaultVersion, defaultLines, versionKey);
            
            // Write merged config
            Files.write(configFile.toPath(), mergedLines, StandardCharsets.UTF_8);
            
            plugin.getLogger().info("Messages migration completed - merged with default, preserving user values and all comments");
        } catch (Exception e) {
            plugin.getLogger().warning("Error migrating messages.yml: " + e.getMessage());
            // Don't fail plugin startup if migration has issues
            e.printStackTrace();
        }
    }
    
    /**
     * Merge default config (with comments) with user config (with values)
     */
    private List<String> mergeConfigs(List<String> defaultLines, YamlConfiguration userConfig, YamlConfiguration defaultConfig) {
        List<String> merged = new ArrayList<>();
        Stack<Pair<String, Integer>> pathStack = new Stack<>();
        
        for (int i = 0; i < defaultLines.size(); i++) {
            String line = defaultLines.get(i);
            String trimmed = line.trim();
            int currentIndent = line.length() - trimmed.length();
            
            if (trimmed.isEmpty() || line.startsWith("#")) {
                merged.add(line);
                continue;
            }
            
            while (!pathStack.isEmpty() && currentIndent <= pathStack.peek().getValue()) {
                pathStack.pop();
            }
            
            if (trimmed.startsWith("-")) {
                merged.add(line);
                continue;
            }
            
            if (trimmed.contains(":") && !trimmed.startsWith("#")) {
                int colonIndex = trimmed.indexOf(':');
                String keyPart = trimmed.substring(0, colonIndex).trim();
                String valuePart = trimmed.substring(colonIndex + 1).trim();
                
                StringBuilder fullPathBuilder = new StringBuilder();
                for (Pair<String, Integer> pathEntry : pathStack) {
                    if (fullPathBuilder.length() > 0) {
                        fullPathBuilder.append(".");
                    }
                    fullPathBuilder.append(pathEntry.getKey());
                }
                if (fullPathBuilder.length() > 0) {
                    fullPathBuilder.append(".");
                }
                fullPathBuilder.append(keyPart);
                String fullPath = fullPathBuilder.toString();
                
                boolean isSection = valuePart.isEmpty();
                boolean isList = false;
                if (isSection && i + 1 < defaultLines.size()) {
                    for (int j = i + 1; j < defaultLines.size() && j < i + 10; j++) {
                        String nextLine = defaultLines.get(j);
                        String nextTrimmed = nextLine.trim();
                        if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                            continue;
                        }
                        int nextIndent = nextLine.length() - nextTrimmed.length();
                        if (nextTrimmed.startsWith("-")) {
                            isList = true;
                            isSection = true;
                            break;
                        } else if (nextIndent > currentIndent) {
                            isSection = true;
                            break;
                        } else {
                            break;
                        }
                    }
                }
                
                if (isSection) {
                    if (userConfig.contains(fullPath)) {
                        Object userValue = userConfig.get(fullPath);
                        if (isList && userValue instanceof List) {
                            merged.add(line);
                            List<?> userList = (List<?>) userValue;
                            for (Object item : userList) {
                                String itemStr = formatYamlValue(item);
                                if (itemStr.startsWith("\"") && itemStr.endsWith("\"")) {
                                    itemStr = itemStr.substring(1, itemStr.length() - 1);
                                }
                                merged.add(" ".repeat(currentIndent + 2) + "- " + itemStr);
                            }
                            while (i + 1 < defaultLines.size()) {
                                String nextLine = defaultLines.get(i + 1);
                                String nextTrimmed = nextLine.trim();
                                int nextIndent = nextLine.length() - nextTrimmed.length();
                                if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                                    if (nextIndent > currentIndent) {
                                        i++;
                                    } else {
                                        break;
                                    }
                                } else if (nextTrimmed.startsWith("-") && nextIndent > currentIndent) {
                                    i++;
                                } else {
                                    break;
                                }
                            }
                            pathStack.push(new Pair<>(keyPart, currentIndent));
                            continue;
                        } else {
                            merged.add(line);
                            pathStack.push(new Pair<>(keyPart, currentIndent));
                        }
                    } else {
                        merged.add(line);
                        pathStack.push(new Pair<>(keyPart, currentIndent));
                    }
                } else {
                    if (keyPart.equals("config_version") || keyPart.equals("messages_version") || keyPart.equals("gui_version")) {
                        merged.add(line);
                    } else if (userConfig.contains(fullPath)) {
                        Object userValue = userConfig.get(fullPath);
                        String userValueStr = formatYamlValue(userValue);
                        String inlineComment = "";
                        int commentIndex = valuePart.indexOf('#');
                        if (commentIndex >= 0) {
                            inlineComment = " " + valuePart.substring(commentIndex);
                        }
                        merged.add(" ".repeat(currentIndent) + keyPart + ": " + userValueStr + inlineComment);
                    } else {
                        merged.add(line);
                    }
                }
            } else {
                merged.add(line);
            }
        }
        
        return merged;
    }
    
    private Set<String> findDeprecatedKeys(YamlConfiguration userConfig, YamlConfiguration defaultConfig) {
        Set<String> deprecated = new HashSet<>();
        findDeprecatedKeysRecursive(userConfig, defaultConfig, "", deprecated);
        return deprecated;
    }
    
    private void findDeprecatedKeysRecursive(YamlConfiguration userConfig, YamlConfiguration defaultConfig, 
                                             String basePath, Set<String> deprecated) {
        for (String key : userConfig.getKeys(false)) {
            String fullPath = basePath.isEmpty() ? key : basePath + "." + key;
            if (key.equals("config_version") || key.equals("messages_version") || key.equals("gui_version")) {
                continue;
            }
            if (!defaultConfig.contains(fullPath)) {
                deprecated.add(fullPath);
            } else if (userConfig.isConfigurationSection(key) && defaultConfig.isConfigurationSection(fullPath)) {
                findDeprecatedKeysRecursive(
                    userConfig.getConfigurationSection(key),
                    defaultConfig.getConfigurationSection(fullPath),
                    fullPath,
                    deprecated
                );
            }
        }
    }
    
    private void findDeprecatedKeysRecursive(org.bukkit.configuration.ConfigurationSection userSection,
                                            org.bukkit.configuration.ConfigurationSection defaultSection,
                                            String basePath, Set<String> deprecated) {
        for (String key : userSection.getKeys(false)) {
            String fullPath = basePath.isEmpty() ? key : basePath + "." + key;
            if (key.equals("config_version") || key.equals("messages_version") || key.equals("gui_version")) {
                continue;
            }
            if (!defaultSection.contains(key)) {
                deprecated.add(fullPath);
            } else if (userSection.isConfigurationSection(key) && defaultSection.isConfigurationSection(key)) {
                findDeprecatedKeysRecursive(
                    userSection.getConfigurationSection(key),
                    defaultSection.getConfigurationSection(key),
                    fullPath,
                    deprecated
                );
            }
        }
    }
    
    private static class Pair<K, V> {
        private final K key;
        private final V value;
        
        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }
        
        public K getKey() { return key; }
        public V getValue() { return value; }
    }
    
    private String formatYamlValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            String str = (String) value;
            if (str.contains(":") || str.contains("#") || str.trim().isEmpty() || 
                str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false") || 
                str.equalsIgnoreCase("null") || str.matches("^-?\\d+$")) {
                return "\"" + str.replace("\"", "\\\"") + "\"";
            }
            return str;
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "[]";
            }
            if (list.size() == 1 && (list.get(0) instanceof String || list.get(0) instanceof Number)) {
                return "[" + formatYamlValue(list.get(0)) + "]";
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatYamlValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else {
            return value.toString();
        }
    }
    
    private void updateConfigVersion(List<String> lines, int newVersion, List<String> defaultLines, String versionKey) {
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.startsWith(versionKey + ":") || trimmed.startsWith(versionKey + " ")) {
                int indent = line.length() - trimmed.length();
                String restOfLine = "";
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex >= 0 && colonIndex + 1 < trimmed.length()) {
                    String afterColon = trimmed.substring(colonIndex + 1).trim();
                    int commentIndex = afterColon.indexOf('#');
                    if (commentIndex >= 0) {
                        restOfLine = " #" + afterColon.substring(commentIndex + 1);
                    }
                }
                lines.set(i, " ".repeat(indent) + versionKey + ": " + newVersion + restOfLine);
                found = true;
                break;
            }
        }
        
        if (!found) {
            String commentLine = "# Messages version - do not modify";
            for (int i = 0; i < defaultLines.size(); i++) {
                String line = defaultLines.get(i);
                String trimmed = line.trim();
                if (trimmed.startsWith(versionKey + ":") || trimmed.startsWith(versionKey + " ")) {
                    if (i > 0) {
                        String prevLine = defaultLines.get(i - 1);
                        if (prevLine.trim().startsWith("#")) {
                            commentLine = prevLine;
                        }
                    }
                    break;
                }
            }
            
            int insertIndex = 0;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !line.startsWith("#") && !trimmed.startsWith(versionKey)) {
                    insertIndex = i;
                    break;
                }
            }
            lines.add(insertIndex, commentLine);
            lines.add(insertIndex + 1, versionKey + ": " + newVersion);
            if (insertIndex + 2 < lines.size() && !lines.get(insertIndex + 2).trim().isEmpty()) {
                lines.add(insertIndex + 2, "");
            }
        }
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