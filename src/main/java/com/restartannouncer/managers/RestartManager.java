package com.restartannouncer.managers;

import com.restartannouncer.RestartAnnouncerPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class RestartManager {
    
    private final RestartAnnouncerPlugin plugin;
    private BukkitTask restartTask;
    private BukkitTask announcementTask;
    private int timeRemaining;
    private int initialTimeRemaining; // Store initial time for progress calculation
    private int announcementInterval;
    private String displayType;
    private BossBar bossBar;
    private boolean isRunning;
    
    public RestartManager(RestartAnnouncerPlugin plugin) {
        this.plugin = plugin;
        this.isRunning = false;
    }
    
    public boolean startRestart(int totalSeconds, int intervalSeconds, String displayType) {
        if (isRunning) {
            return false;
        }
        
        this.timeRemaining = totalSeconds; // Use seconds directly
        this.initialTimeRemaining = this.timeRemaining; // Store initial time
        this.announcementInterval = intervalSeconds;
        this.displayType = displayType;
        this.isRunning = true;
        
        // Start the main countdown
        restartTask = new BukkitRunnable() {
            @Override
            public void run() {
                timeRemaining--;
                
                if (timeRemaining <= 0) {
                    // Time to restart
                    plugin.getMessageManager().broadcastMessage("§c§lServer is restarting now!");
                    
                    if (plugin.getConfigManager().shouldExecuteShutdown()) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            executeShutdown();
                        }, 20L); // 1 second delay
                    } else {
                        plugin.getLogger().info("Restart countdown completed. Server shutdown was disabled in config.");
                    }
                    
                    stopRestart();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second
        
        // Start the announcement task
        startAnnouncements();
        
        return true;
    }
    
    private void startAnnouncements() {
        // Send initial announcement immediately
        sendAnnouncement(timeRemaining);
        
        // Then start the periodic announcements with dynamic timing
        scheduleNextAnnouncement();
    }
    
    private void scheduleNextAnnouncement() {
        if (!isRunning) {
            return;
        }
        
        // Calculate next announcement interval based on remaining time
        int nextInterval = getNextAnnouncementInterval(timeRemaining);
        
        // Smart scheduling: if the next announcement would be after emergency countdown should start,
        // schedule it earlier to ensure emergency countdown activates
        if (timeRemaining > 60 && nextInterval > (timeRemaining - 60)) {
            // Schedule announcement to happen just before emergency countdown starts
            nextInterval = Math.max(1, timeRemaining - 60);
            plugin.getLogger().info("Smart scheduling: adjusted interval to " + nextInterval + "s to ensure emergency countdown activation");
        }
        
        // Log the scheduling
        plugin.getLogger().info("Scheduling next announcement in " + nextInterval + "s (time remaining: " + timeRemaining + "s)");
        
        announcementTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRunning) {
                    return;
                }
                
                // Send announcement
                sendAnnouncement(timeRemaining);
                
                // Schedule the next announcement (this will recalculate the interval)
                scheduleNextAnnouncement();
            }
        }.runTaskLater(plugin, nextInterval * 20L);
    }
    
    private int getNextAnnouncementInterval(int timeLeft) {
        int interval;
        
        // Under 1 minute: emergency countdown takes precedence
        if (timeLeft <= 60) {
            if (timeLeft <= 10) {
                // Under 10 seconds: announce every 1 second
                interval = 1;
            } else if (timeLeft <= 30) {
                // Under 30 seconds: announce every 5 seconds
                interval = 5;
            } else {
                // 31-60 seconds: announce every 10 seconds
                interval = 10;
            }
            
            // Log emergency countdown activation
            plugin.getLogger().info("Emergency countdown activated at " + timeLeft + "s - announcing every " + interval + "s");
        } else {
            // Over 1 minute: use the user's specified interval
            interval = announcementInterval;
        }
        
        return interval;
    }
    
    private void sendAnnouncement(int timeLeft) {
        String timeString = formatTime(timeLeft);
        String message = plugin.getMessageManager().getRestartMessage(timeString);
        
        switch (displayType) {
            case "bossbar":
                sendBossBarMessage(message);
                break;
            case "title":
                sendTitleMessage(message);
                break;
            case "chat":
            default:
                plugin.getMessageManager().broadcastMessage(message);
                break;
        }
    }
    
    private void sendBossBarMessage(String message) {
        // Remove old boss bar if it exists
        if (bossBar != null) {
            bossBar.removeAll();
        }
        
        // Convert MiniMessage to legacy format
        String legacyMessage = plugin.getMessageManager().formatMessage(message);
        
        // Create new boss bar
        String plainMessage = LegacyComponentSerializer.legacySection().serialize(
            LegacyComponentSerializer.legacySection().deserialize(legacyMessage)
        );
        
        bossBar = Bukkit.createBossBar(plainMessage, BarColor.RED, BarStyle.SOLID);
        
        // Calculate progress percentage (1.0 = full, 0.0 = empty)
        double progress = 1.0;
        if (initialTimeRemaining > 0) {
            progress = Math.max(0.0, Math.min(1.0, (double) timeRemaining / initialTimeRemaining));
        }
        bossBar.setProgress(progress);
        
        // Add all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
    }
    
    private void sendTitleMessage(String message) {
        // Convert MiniMessage to legacy format
        String legacyMessage = plugin.getMessageManager().formatMessage(message);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(legacyMessage, "", 10, 60, 10);
        }
    }
    
    public void stopRestart() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (restartTask != null) {
            restartTask.cancel();
            restartTask = null;
        }
        
        if (announcementTask != null) {
            announcementTask.cancel();
            announcementTask = null;
        }
        
        // Clean up boss bar if it exists
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public int getTimeRemaining() {
        return timeRemaining;
    }
    
    public String getTimeRemainingFormatted() {
        return formatTime(timeRemaining);
    }
    
    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            if (remainingSeconds == 0) {
                return minutes + " minute" + (minutes != 1 ? "s" : "");
            } else {
                return minutes + " minute" + (minutes != 1 ? "s" : "") + " " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "");
            }
        } else {
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            if (minutes == 0) {
                return hours + " hour" + (hours != 1 ? "s" : "");
            } else {
                return hours + " hour" + (hours != 1 ? "s" : "") + " " + minutes + " minute" + (minutes != 1 ? "s" : "");
            }
        }
    }
    
    public static int parseTime(String timeString) {
        timeString = timeString.toLowerCase().trim();
        
        if (timeString.endsWith("s")) {
            return Integer.parseInt(timeString.substring(0, timeString.length() - 1));
        } else if (timeString.endsWith("m")) {
            return Integer.parseInt(timeString.substring(0, timeString.length() - 1)) * 60;
        } else if (timeString.endsWith("h")) {
            return Integer.parseInt(timeString.substring(0, timeString.length() - 1)) * 3600;
        } else {
            // Assume minutes if no suffix
            return Integer.parseInt(timeString) * 60;
        }
    }
    
    private void executeShutdown() {
        String method = plugin.getConfigManager().getShutdownMethod();
        
        switch (method.toLowerCase()) {
            case "stop":
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
                break;
            case "restart":
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
                break;
            case "shutdown":
            default:
                Bukkit.shutdown();
                break;
        }
    }
} 
