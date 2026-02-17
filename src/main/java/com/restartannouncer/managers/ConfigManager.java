package com.restartannouncer.managers;

import com.restartannouncer.RestartAnnouncerPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class ConfigManager {
    
    private final RestartAnnouncerPlugin plugin;
    private FileConfiguration config;
    
    public ConfigManager(RestartAnnouncerPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        migrateConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }
    
    /**
     * Migrate config.yml to add missing options from newer versions
     * Preserves user values while adding new options and comments
     */
    private void migrateConfig() {
        try {
            // Get the actual config file
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            
            if (!configFile.exists()) {
                return; // No migration needed if file doesn't exist
            }
            
            // Load the default config from the jar resource (get it twice - once for text, once for YAML)
            InputStream defaultConfigTextStream = plugin.getResource("config.yml");
            if (defaultConfigTextStream == null) {
                plugin.getLogger().warning("Could not load default config.yml from jar for migration");
                return;
            }
            
            // Read default config as text to preserve formatting
            List<String> currentLines = new ArrayList<>(Files.readAllLines(configFile.toPath()));
            List<String> defaultLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(defaultConfigTextStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    defaultLines.add(line);
                }
            }
            
            // Also load as YAML to check what's missing (get resource again - getResource returns new stream)
            InputStream defaultConfigYamlStream = plugin.getResource("config.yml");
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigYamlStream));
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // Check config version - get default version
            int defaultVersion = defaultConfig.getInt("config_version", 1);
            int currentVersion = currentConfig.getInt("config_version", 0); // 0 means old config without version
            
            // If versions match and config has version field, no migration needed
            if (currentVersion == defaultVersion && currentConfig.contains("config_version")) {
                return; // Config is up to date
            }
            
            // Simple merge approach: Use default config structure/comments, replace values with user's where they exist
            // This preserves all comments and formatting from default, while keeping user's custom values
            // Deprecated keys (in user config but not in default) are automatically removed
            List<String> mergedLines = mergeConfigs(defaultLines, currentConfig, defaultConfig);
            
            // Check for and log deprecated keys that were removed
            Set<String> deprecatedKeys = findDeprecatedKeys(currentConfig, defaultConfig);
            if (!deprecatedKeys.isEmpty()) {
                plugin.getLogger().info("Removed deprecated config keys: " + String.join(", ", deprecatedKeys));
            }
            
            // Update config version
            updateConfigVersion(mergedLines, defaultVersion, defaultLines, "config_version");
            
            // Write merged config
            Files.write(configFile.toPath(), mergedLines, StandardCharsets.UTF_8);
            
            plugin.getLogger().info("Config migration completed - merged with default config, preserving user values and all comments");
            
            // Reload config
            plugin.reloadConfig();
        } catch (Exception e) {
            plugin.getLogger().warning("Error during config migration: " + e.getMessage());
            // Don't fail plugin startup if migration has issues
            e.printStackTrace();
        }
    }
    
    public void reloadConfig() {
        loadConfig();
    }
    
    public FileConfiguration getConfig() {
        return config;
    }
    
    // Default settings
    public int getDefaultRestartTime() {
        return config.getInt("defaults.restart-time", 10);
    }
    
    public int getDefaultAnnouncementInterval() {
        return config.getInt("defaults.announcement-interval", 60);
    }
    
    // Permissions
    public String getStartPermission() {
        return config.getString("permissions.start", "announcer.start");
    }
    
    public String getStopPermission() {
        return config.getString("permissions.stop", "announcer.stop");
    }
    
    public String getStatusPermission() {
        return config.getString("permissions.status", "announcer.status");
    }
    
    public String getReloadPermission() {
        return config.getString("permissions.reload", "announcer.reload");
    }
    
    public String getShutdownMethod() {
        return config.getString("shutdown-method", "shutdown");
    }
    
    public boolean shouldExecuteShutdown() {
        return config.getBoolean("execute-shutdown", true);
    }
    
    // Scheduled restart (system time, 24hr format)
    public boolean isScheduledRestartEnabled() {
        return config.getBoolean("scheduled-restart.enabled", false);
    }
    
    /** Time in 24hr format, 4 digits HHmm (e.g. 0400 = 4:00 AM, 1600 = 4:00 PM). Accepts number (400) or string ("0400"). */
    public String getScheduledRestartTime() {
        Object v = config.get("scheduled-restart.time");
        if (v == null) {
            return "0400";
        }
        if (v instanceof Number) {
            return String.format("%04d", ((Number) v).intValue());
        }
        String s = v.toString().trim().replace(":", "");
        if (s.isEmpty()) {
            return "0400";
        }
        int n = Integer.parseInt(s);
        return String.format("%04d", n);
    }
    
    public int getScheduledRestartReminderIntervalHours() {
        return config.getInt("scheduled-restart.reminder-interval-hours", 4);
    }
    
    public boolean shouldWaitForBackup() {
        return config.getBoolean("scheduled-restart.wait-for-backup", true);
    }

    /** Seconds to wait after countdown hits 0 before first backup check (default 60). Only used when wait-for-backup is true. */
    public int getWaitForBackupDelaySeconds() {
        return Math.max(1, config.getInt("scheduled-restart.wait-for-backup-delay", 60));
    }

    /**
     * Merge default config (with comments) with user config (with values)
     * Simple approach: Use default structure/comments, replace values with user's where they exist
     */
    private List<String> mergeConfigs(List<String> defaultLines, YamlConfiguration userConfig, YamlConfiguration defaultConfig) {
        List<String> merged = new ArrayList<>();
        
        // Track current path for nested keys - store both name and indent level
        Stack<Pair<String, Integer>> pathStack = new Stack<>();
        
        for (int i = 0; i < defaultLines.size(); i++) {
            String line = defaultLines.get(i);
            String trimmed = line.trim();
            int currentIndent = line.length() - trimmed.length();
            
            // Always preserve comments and blank lines
            if (trimmed.isEmpty() || line.startsWith("#")) {
                merged.add(line);
                continue;
            }
            
            // Pop sections we've left (based on indent level)
            while (!pathStack.isEmpty() && currentIndent <= pathStack.peek().getValue()) {
                pathStack.pop();
            }
            
            // Check if this is a list item (starts with -)
            if (trimmed.startsWith("-")) {
                // This is a list item - preserve as-is (lists are handled at the parent key level)
                merged.add(line);
                continue;
            }
            
            // Check if this is a key=value line
            if (trimmed.contains(":") && !trimmed.startsWith("#")) {
                int colonIndex = trimmed.indexOf(':');
                String keyPart = trimmed.substring(0, colonIndex).trim();
                String valuePart = trimmed.substring(colonIndex + 1).trim();
                
                // Build full path for nested keys
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
                
                // Check if this is a section (value is empty and next line is indented or is a list)
                boolean isSection = valuePart.isEmpty();
                boolean isList = false;
                if (isSection && i + 1 < defaultLines.size()) {
                    // Look ahead to see if next non-comment line is indented or is a list item
                    for (int j = i + 1; j < defaultLines.size() && j < i + 10; j++) {
                        String nextLine = defaultLines.get(j);
                        String nextTrimmed = nextLine.trim();
                        if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                            continue;
                        }
                        int nextIndent = nextLine.length() - nextTrimmed.length();
                        if (nextTrimmed.startsWith("-")) {
                            // This is a list
                            isList = true;
                            isSection = true;
                            break;
                        } else if (nextIndent > currentIndent) {
                            isSection = true;
                            break;
                        } else {
                            // Next line is at same or less indent - not a section
                            break;
                        }
                    }
                }
                
                if (isSection) {
                    // This is a section - check if user has values for it
                    if (userConfig.contains(fullPath)) {
                        Object userValue = userConfig.get(fullPath);
                        
                        // If it's a list, we need to handle it specially
                        if (isList && userValue instanceof List) {
                            // Add the key line
                            merged.add(line);
                            // Add list items from user config
                            List<?> userList = (List<?>) userValue;
                            for (Object item : userList) {
                                String itemStr = formatYamlValue(item);
                                // Remove quotes if they were added (lists often don't need them)
                                if (itemStr.startsWith("\"") && itemStr.endsWith("\"")) {
                                    itemStr = itemStr.substring(1, itemStr.length() - 1);
                                }
                                merged.add(" ".repeat(currentIndent + 2) + "- " + itemStr);
                            }
                            // Skip the default list items and their comments - we've already added user's
                            // Skip until we're out of the list (next line at same or less indent)
                            while (i + 1 < defaultLines.size()) {
                                String nextLine = defaultLines.get(i + 1);
                                String nextTrimmed = nextLine.trim();
                                int nextIndent = nextLine.length() - nextTrimmed.length();
                                
                                // If it's a comment or blank line within the list, skip it
                                if (nextTrimmed.isEmpty() || nextLine.startsWith("#")) {
                                    if (nextIndent > currentIndent) {
                                        i++; // Skip comment/blank within list
                                    } else {
                                        break; // Comment at section level or above - end of list
                                    }
                                } else if (nextTrimmed.startsWith("-") && nextIndent > currentIndent) {
                                    i++; // Skip this list item
                                } else {
                                    break; // End of list
                                }
                            }
                            pathStack.push(new Pair<>(keyPart, currentIndent));
                            continue;
                        } else {
                            // Regular section - add it and push to path stack
                            merged.add(line);
                            pathStack.push(new Pair<>(keyPart, currentIndent));
                        }
                    } else {
                        // User doesn't have this section - use default (with all comments and list items)
                        merged.add(line);
                        pathStack.push(new Pair<>(keyPart, currentIndent));
                    }
                } else {
                    // This is a key=value line
                    // Skip version keys - they're handled separately by updateConfigVersion
                    if (keyPart.equals("config_version") || keyPart.equals("messages_version") || keyPart.equals("gui_version")) {
                        // Use default version line - it will be updated by updateConfigVersion
                        merged.add(line);
                    } else if (userConfig.contains(fullPath)) {
                        // User has this key - use their value but keep default's formatting
                        Object userValue = userConfig.get(fullPath);
                        String userValueStr = formatYamlValue(userValue);
                        
                        // Preserve inline comment if present
                        String inlineComment = "";
                        int commentIndex = valuePart.indexOf('#');
                        if (commentIndex >= 0) {
                            inlineComment = " " + valuePart.substring(commentIndex);
                        }
                        
                        // Replace value while preserving indentation and inline comment
                        merged.add(" ".repeat(currentIndent) + keyPart + ": " + userValueStr + inlineComment);
                    } else {
                        // User doesn't have this key - use default (with default value and comments)
                        merged.add(line);
                    }
                }
            } else {
                // Not a key=value line - preserve as-is
                merged.add(line);
            }
        }
        
        return merged;
    }
    
    /**
     * Find deprecated keys that exist in user config but not in default config
     * These keys will be removed during migration
     */
    private Set<String> findDeprecatedKeys(YamlConfiguration userConfig, YamlConfiguration defaultConfig) {
        Set<String> deprecated = new HashSet<>();
        findDeprecatedKeysRecursive(userConfig, defaultConfig, "", deprecated);
        return deprecated;
    }
    
    /**
     * Recursively find deprecated keys
     */
    private void findDeprecatedKeysRecursive(YamlConfiguration userConfig, YamlConfiguration defaultConfig, 
                                             String basePath, Set<String> deprecated) {
        for (String key : userConfig.getKeys(false)) {
            String fullPath = basePath.isEmpty() ? key : basePath + "." + key;
            
            // Skip version keys - they're handled separately
            if (key.equals("config_version") || key.equals("messages_version") || key.equals("gui_version")) {
                continue;
            }
            
            if (!defaultConfig.contains(fullPath)) {
                // This key doesn't exist in default config - it's deprecated
                deprecated.add(fullPath);
            } else if (userConfig.isConfigurationSection(key) && defaultConfig.isConfigurationSection(fullPath)) {
                // Both are sections - recursively check nested keys
                findDeprecatedKeysRecursive(
                    userConfig.getConfigurationSection(key),
                    defaultConfig.getConfigurationSection(fullPath),
                    fullPath,
                    deprecated
                );
            }
        }
    }
    
    /**
     * Recursive helper for configuration sections
     */
    private void findDeprecatedKeysRecursive(org.bukkit.configuration.ConfigurationSection userSection,
                                            org.bukkit.configuration.ConfigurationSection defaultSection,
                                            String basePath, Set<String> deprecated) {
        for (String key : userSection.getKeys(false)) {
            String fullPath = basePath.isEmpty() ? key : basePath + "." + key;
            
            // Skip version keys - they're handled separately
            if (key.equals("config_version") || key.equals("messages_version") || key.equals("gui_version")) {
                continue;
            }
            
            if (!defaultSection.contains(key)) {
                // This key doesn't exist in default config - it's deprecated
                deprecated.add(fullPath);
            } else if (userSection.isConfigurationSection(key) && defaultSection.isConfigurationSection(key)) {
                // Both are sections - recursively check nested keys
                findDeprecatedKeysRecursive(
                    userSection.getConfigurationSection(key),
                    defaultSection.getConfigurationSection(key),
                    fullPath,
                    deprecated
                );
            }
        }
    }
    
    // Simple Pair class for path tracking
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
    
    /**
     * Format a YAML value as a string
     */
    private String formatYamlValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            // Check if it needs quotes
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
            // Format list
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "[]";
            }
            // For lists, we'll just return the first approach - inline if simple
            if (list.size() == 1 && (list.get(0) instanceof String || list.get(0) instanceof Number)) {
                return "[" + formatYamlValue(list.get(0)) + "]";
            }
            // Multi-line list - return as inline for now, could be improved
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
    
    /**
     * Update config version in the merged lines
     */
    private void updateConfigVersion(List<String> lines, int newVersion, List<String> defaultLines, String versionKey) {
        // Look for version line and update it, or add it if missing
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // Check if this is the version line
            if (trimmed.startsWith(versionKey + ":") || trimmed.startsWith(versionKey + " ")) {
                // Update the version value, preserving indentation and any inline comments
                int indent = line.length() - trimmed.length();
                String restOfLine = "";
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex >= 0 && colonIndex + 1 < trimmed.length()) {
                    String afterColon = trimmed.substring(colonIndex + 1).trim();
                    // Check if there's an inline comment
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
        
        // If not found, add it after the header comment (usually line 2-3)
        if (!found) {
            // Extract comment from default config
            String commentLine = "# Config version - do not modify";
            for (int i = 0; i < defaultLines.size(); i++) {
                String line = defaultLines.get(i);
                String trimmed = line.trim();
                // Look for version key in default config
                if (trimmed.startsWith(versionKey + ":") || trimmed.startsWith(versionKey + " ")) {
                    // Check if there's a comment line before it
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
            // Find a good place to insert - after first comment block, before first section
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                // Stop at first non-comment, non-blank line that's not the version key
                if (!trimmed.isEmpty() && !line.startsWith("#") && !trimmed.startsWith(versionKey)) {
                    insertIndex = i;
                    break;
                }
            }
            // Insert version line with comment from default config
            lines.add(insertIndex, commentLine);
            lines.add(insertIndex + 1, versionKey + ": " + newVersion);
            // Add blank line after if needed
            if (insertIndex + 2 < lines.size() && !lines.get(insertIndex + 2).trim().isEmpty()) {
                lines.add(insertIndex + 2, "");
            }
        }
    }
} 