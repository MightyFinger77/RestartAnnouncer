package com.restartannouncer;

import com.restartannouncer.commands.AnnouncerCommand;
import com.restartannouncer.managers.ConfigManager;
import com.restartannouncer.managers.RestartManager;
import com.restartannouncer.managers.MessageManager;
import org.bukkit.plugin.java.JavaPlugin;

public class RestartAnnouncerPlugin extends JavaPlugin {
    
    private static RestartAnnouncerPlugin instance;
    private ConfigManager configManager;
    private RestartManager restartManager;
    private MessageManager messageManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);
        this.restartManager = new RestartManager(this);
        
        // Load configuration
        configManager.loadConfig();
        messageManager.loadMessages();
        
        // Register commands
        AnnouncerCommand announcerCommand = new AnnouncerCommand(this);
        getCommand("announcer").setExecutor(announcerCommand);
        getCommand("announcer").setTabCompleter(announcerCommand);
        
        // Plugin is ready
        
        getLogger().info("RestartAnnouncer has been enabled!");
    }
    
    @Override
    public void onDisable() {
        if (restartManager != null) {
            restartManager.stopRestart();
        }
        getLogger().info("RestartAnnouncer has been disabled!");
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
    

} 