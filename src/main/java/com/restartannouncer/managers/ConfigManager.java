package com.restartannouncer.managers;

import com.restartannouncer.RestartAnnouncerPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    
    private final RestartAnnouncerPlugin plugin;
    private FileConfiguration config;
    
    public ConfigManager(RestartAnnouncerPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }
    
    public void reloadConfig() {
        loadConfig();
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
} 