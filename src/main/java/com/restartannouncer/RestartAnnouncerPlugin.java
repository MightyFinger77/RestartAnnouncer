package com.restartannouncer;

import com.restartannouncer.commands.AnnouncerCommand;
import com.restartannouncer.managers.ConfigManager;
import com.restartannouncer.managers.RestartManager;
import com.restartannouncer.managers.MessageManager;
import com.restartannouncer.managers.ScheduledRestartManager;
import com.restartannouncer.util.BackupChecker;
import org.bukkit.plugin.java.JavaPlugin;

public class RestartAnnouncerPlugin extends JavaPlugin {

    private static RestartAnnouncerPlugin instance;
    private ConfigManager configManager;
    private RestartManager restartManager;
    private MessageManager messageManager;
    private BackupChecker backupChecker;
    private ScheduledRestartManager scheduledRestartManager;
    /** True when the current restart was started by scheduled restart (so we use backup delay if configured). */
    private boolean scheduledRestartActive;
    
    // Spigot resource ID for update checking
    private static final int SPIGOT_RESOURCE_ID = 127869;
    
    // Store update info for new players
    private String latestVersion = null;
    private boolean updateAvailable = false;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);
        this.restartManager = new RestartManager(this);
        this.backupChecker = new BackupChecker(this);

        // Load configuration
        configManager.loadConfig();
        messageManager.loadMessages();

        // Register commands
        AnnouncerCommand announcerCommand = new AnnouncerCommand(this);
        getCommand("announcer").setExecutor(announcerCommand);
        getCommand("announcer").setTabCompleter(announcerCommand);

        // Scheduled restart (if enabled)
        applyScheduledRestartFromConfig();

        // Check for updates if enabled
        if (configManager.getConfig().getBoolean("update-checker.enabled", true)) {
            checkForUpdates();
        }

        // Plugin is ready

        getLogger().info("RestartAnnouncer has been enabled!");
    }
    
    @Override
    public void onDisable() {
        if (scheduledRestartManager != null) {
            scheduledRestartManager.stop();
            scheduledRestartManager = null;
        }
        if (restartManager != null) {
            restartManager.stopRestart();
        }
        getLogger().info("RestartAnnouncer has been disabled!");
    }

    /**
     * Apply scheduled-restart.enabled from config. Stops the scheduler if disabled, starts it if enabled.
     * Called on enable and when /announcer reload is used.
     */
    public void applyScheduledRestartFromConfig() {
        if (scheduledRestartManager != null) {
            scheduledRestartManager.stop();
            scheduledRestartManager = null;
        }
        if (configManager.isScheduledRestartEnabled()) {
            scheduledRestartManager = new ScheduledRestartManager(this);
            scheduledRestartManager.start();
        }
    }
    
    public static RestartAnnouncerPlugin getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public RestartManager getRestartManager() {
        return restartManager;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }

    public void setScheduledRestartActive(boolean scheduledRestartActive) {
        this.scheduledRestartActive = scheduledRestartActive;
    }

    public boolean isScheduledRestartActive() {
        return scheduledRestartActive;
    }

    public boolean isBackupRunning() {
        return backupChecker != null && backupChecker.isBackupRunning();
    }

    /**
     * Public method to manually check for updates (can be called from commands)
     */
    public void checkForUpdatesManually() {
        checkForUpdatesManually(null);
    }
    
    /**
     * Public method to manually check for updates with player feedback
     */
    public void checkForUpdatesManually(org.bukkit.entity.Player player) {
        if (configManager.getConfig().getBoolean("update-checker.enabled", true)) {
            getLogger().info("Manually checking for updates...");
            checkForUpdates(player);
        } else {
            getLogger().info("Update checking is disabled in config");
            if (player != null) {
                player.sendMessage("§c[RestartAnnouncer] Update checking is disabled in config");
            }
        }
    }
    
    /**
     * Check for plugin updates using SpigotMC API
     */
    private void checkForUpdates() {
        checkForUpdates(null);
    }
    
    /**
     * Check for plugin updates using SpigotMC API with player feedback
     */
    private void checkForUpdates(org.bukkit.entity.Player player) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String url = "https://api.spigotmc.org/legacy/update.php?resource=" + SPIGOT_RESOURCE_ID;
                java.net.URLConnection connection = java.net.URI.create(url).toURL().openConnection();
                connection.setRequestProperty("User-Agent", "RestartAnnouncer-UpdateChecker");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                String latestVersion;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()))) {
                    latestVersion = reader.readLine();
                }
                
                String currentVersion = getDescription().getVersion();
                
                if (isDebugEnabled()) {
                    getLogger().info("[DEBUG] Update check - API returned: '" + latestVersion + "', Current: '" + currentVersion + "'");
                }
                
                if (isNewerVersion(latestVersion, currentVersion)) {
                    // Store update info for new players
                    this.latestVersion = latestVersion;
                    this.updateAvailable = true;
                    
                    getServer().getScheduler().runTask(this, () -> {
                        getLogger().info("§a[RestartAnnouncer] Update available: " + latestVersion);
                        getLogger().info("§a[RestartAnnouncer] Current version: " + currentVersion);
                        getLogger().info("§a[RestartAnnouncer] Download: https://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID);
                        
                        // Send update message to the player who requested the check
                        if (player != null) {
                            player.sendMessage("§a[RestartAnnouncer] §eUpdate available: §f" + latestVersion + " §7(current: " + currentVersion + ")");
                            player.sendMessage("§a[RestartAnnouncer] §7Download: §9https://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID);
                        }
                        
                        // Send update message to all online OP'd players with a delay to show after MOTD
                        getServer().getScheduler().runTaskLater(this, () -> {
                            for (org.bukkit.entity.Player onlinePlayer : getServer().getOnlinePlayers()) {
                                if (onlinePlayer.isOp() && (player == null || !onlinePlayer.equals(player))) {
                                    onlinePlayer.sendMessage("§a[RestartAnnouncer] §eUpdate available: §f" + latestVersion + " §7(current: " + currentVersion + ")");
                                    onlinePlayer.sendMessage("§a[RestartAnnouncer] §7Download: §9https://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID);
                                }
                            }
                        }, 100L); // 5 seconds delay (100 ticks = 5 seconds)
                    });
                } else {
                    // Plugin is up to date
                    if (isDebugEnabled()) {
                        getLogger().info("[DEBUG] Plugin is up to date (version " + currentVersion + ")");
                    }
                    
                    // Send "up to date" message to the player who requested the check
                    if (player != null) {
                        getServer().getScheduler().runTask(this, () -> {
                            player.sendMessage("§a[RestartAnnouncer] §aPlugin is up to date (version " + currentVersion + ")");
                        });
                    }
                }
            } catch (Exception e) {
                if (isDebugEnabled()) {
                    getLogger().warning("Could not check for updates: " + e.getMessage());
                }
                
                // Send error message to the player who requested the check
                if (player != null) {
                    getServer().getScheduler().runTask(this, () -> {
                        player.sendMessage("§c[RestartAnnouncer] Could not check for updates: " + e.getMessage());
                    });
                }
            }
        });
    }
    
    /**
     * Check if debug logging is enabled
     */
    private boolean isDebugEnabled() {
        if (configManager == null || configManager.getConfig() == null) {
            return false;
        }
        return configManager.getConfig().getBoolean("debug.enabled", false);
    }
    
    /**
     * Compare version strings to check if latest is newer
     * Handles dev versions (e.g., "1.1.4-Dev2a") by comparing base version numbers
     * Dev builds will be prompted to update to release versions of the same base version
     */
    private boolean isNewerVersion(String latest, String current) {
        if (latest == null || current == null) {
            if (isDebugEnabled()) {
                getLogger().info("[DEBUG] Version comparison failed - null values: latest=" + latest + ", current=" + current);
            }
            return false;
        }
        
        // Store original strings to check for dev/release status
        String originalLatest = latest.trim();
        String originalCurrent = current.trim();
        
        // Clean version strings - remove common prefixes like "Alpha", "Beta", "v", etc.
        String cleanLatest = originalLatest.replaceAll("^(v|version|alpha|beta|release)\\s*", "").trim();
        String cleanCurrent = originalCurrent.replaceAll("^(v|version|alpha|beta|release)\\s*", "").trim();
        
        // Also handle case variations
        cleanLatest = cleanLatest.replaceAll("^(Alpha|Beta|Release|V|Version)\\s*", "").trim();
        cleanCurrent = cleanCurrent.replaceAll("^(Alpha|Beta|Release|V|Version)\\s*", "").trim();
        
        // Check if versions have dev/build suffixes before removing them
        boolean latestIsDev = cleanLatest.matches(".*[-_](?i)(dev|snapshot|alpha|beta|rc|build|pre)[\\d\\w]*$");
        boolean currentIsDev = cleanCurrent.matches(".*[-_](?i)(dev|snapshot|alpha|beta|rc|build|pre)[\\d\\w]*$");
        
        // Remove dev/build suffixes for base version comparison
        String baseLatest = cleanLatest.replaceAll("[-_](?i)(dev|snapshot|alpha|beta|rc|build|pre)[\\d\\w]*$", "").trim();
        String baseCurrent = cleanCurrent.replaceAll("[-_](?i)(dev|snapshot|alpha|beta|rc|build|pre)[\\d\\w]*$", "").trim();
        
        if (isDebugEnabled()) {
            getLogger().info("[DEBUG] Comparing versions - Latest: '" + originalLatest + "' -> base: '" + baseLatest + "' (dev: " + latestIsDev + "), Current: '" + originalCurrent + "' -> base: '" + baseCurrent + "' (dev: " + currentIsDev + ")");
        }
        
        // Simple version comparison - you might want to use a proper version parser
        // This handles basic semantic versioning (1.0.5 vs 1.0.6)
        try {
            String[] latestParts = baseLatest.split("\\.");
            String[] currentParts = baseCurrent.split("\\.");
            
            int maxLength = Math.max(latestParts.length, currentParts.length);
            
            for (int i = 0; i < maxLength; i++) {
                // Extract numeric part from each version segment (handles "4-Dev2a" -> "4")
                String latestPartStr = i < latestParts.length ? latestParts[i].replaceAll("[^0-9].*$", "") : "0";
                String currentPartStr = i < currentParts.length ? currentParts[i].replaceAll("[^0-9].*$", "") : "0";
                
                int latestPart = latestPartStr.isEmpty() ? 0 : Integer.parseInt(latestPartStr);
                int currentPart = currentPartStr.isEmpty() ? 0 : Integer.parseInt(currentPartStr);
                
                if (isDebugEnabled()) {
                    getLogger().info("[DEBUG] Comparing part " + i + ": " + latestPart + " vs " + currentPart);
                }
                
                if (latestPart > currentPart) {
                    if (isDebugEnabled()) {
                        getLogger().info("[DEBUG] Latest version is newer at position " + i);
                    }
                    return true;
                } else if (latestPart < currentPart) {
                    if (isDebugEnabled()) {
                        getLogger().info("[DEBUG] Current version is newer at position " + i);
                    }
                    return false; // Current version is newer, don't prompt for update
                }
            }
            
            // Base versions are equal - check if we should update from dev to release or dev to dev
            if (baseLatest.equals(baseCurrent)) {
                // If latest is a release version and current is a dev version (same base), prompt for update
                if (!latestIsDev && currentIsDev) {
                    if (isDebugEnabled()) {
                        getLogger().info("[DEBUG] Base versions equal - latest is release, current is dev, prompting for update");
                    }
                    return true; // Dev build should update to release version
                } 
                // If both are dev versions, compare dev numbers and letters
                else if (latestIsDev && currentIsDev) {
                    // Extract dev version info (e.g., "Dev3b" -> number=3, letter='b')
                    int latestDevNum = extractDevNumber(cleanLatest);
                    int currentDevNum = extractDevNumber(cleanCurrent);
                    char latestDevLetter = extractDevLetter(cleanLatest);
                    char currentDevLetter = extractDevLetter(cleanCurrent);
                    
                    if (isDebugEnabled()) {
                        getLogger().info("[DEBUG] Both are dev versions - Latest: Dev" + latestDevNum + latestDevLetter + ", Current: Dev" + currentDevNum + currentDevLetter);
                    }
                    
                    // Compare dev numbers first
                    if (latestDevNum > currentDevNum) {
                        if (isDebugEnabled()) {
                            getLogger().info("[DEBUG] Latest dev number is higher, prompting for update");
                        }
                        return true; // Newer dev number
                    } else if (latestDevNum < currentDevNum) {
                        if (isDebugEnabled()) {
                            getLogger().info("[DEBUG] Current dev number is higher, no update needed");
                        }
                        return false; // Current dev number is higher
                    } else {
                        // Dev numbers are equal, compare letters (a < b)
                        if (latestDevLetter > currentDevLetter) {
                            if (isDebugEnabled()) {
                                getLogger().info("[DEBUG] Latest dev letter is higher, prompting for update");
                            }
                            return true; // Newer dev letter (e.g., b > a)
                        } else {
                            if (isDebugEnabled()) {
                                getLogger().info("[DEBUG] Current dev version is same or newer, no update needed");
                            }
                            return false; // Same or older dev letter
                        }
                    }
                } else {
                    if (isDebugEnabled()) {
                        getLogger().info("[DEBUG] Base versions equal - no update needed");
                    }
                    return false; // Both are release versions, or current is release and latest is dev (shouldn't happen)
                }
            }
            
            if (isDebugEnabled()) {
                getLogger().info("[DEBUG] Versions are equal");
            }
            return false; // Versions are equal
        } catch (NumberFormatException e) {
            if (isDebugEnabled()) {
                getLogger().warning("[DEBUG] Version parsing failed, using string comparison: " + e.getMessage());
            }
            // If version parsing fails, do simple string comparison
            boolean result = !baseLatest.equals(baseCurrent);
            if (isDebugEnabled()) {
                getLogger().info("[DEBUG] String comparison result: " + result);
            }
            return result;
        }
    }
    
    /**
     * Extract the dev number from a version string (e.g., "1.1.4-Dev3b" -> 3)
     * Returns 0 if no dev number is found
     */
    private int extractDevNumber(String version) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)[-_]dev(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(version);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Extract the dev letter from a version string (e.g., "1.1.4-Dev3b" -> 'b')
     * Returns 'a' if no dev letter is found (default)
     */
    private char extractDevLetter(String version) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)[-_]dev\\d+([a-z])");
        java.util.regex.Matcher matcher = pattern.matcher(version);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase().charAt(0);
        }
        return 'a'; // Default to 'a' if no letter found
    }

} 