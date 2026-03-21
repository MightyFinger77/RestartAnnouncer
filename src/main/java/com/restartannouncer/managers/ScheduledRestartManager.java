package com.restartannouncer.managers;

import com.restartannouncer.RestartAnnouncerPlugin;
import com.restartannouncer.schedule.ScheduledRestartSpec;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Handles scheduled restarts using system clock.
 * Sends a chat reminder every N hours; when 1hr away, starts the normal countdown (10m intervals, then emergency).
 */
public class ScheduledRestartManager {

    private static final long CHECK_INTERVAL_TICKS = 20L * 60; // every minute
    private static final int ONE_HOUR_SECONDS = 3600;
    private static final int TEN_MINUTES_SECONDS = 600;

    private final RestartAnnouncerPlugin plugin;
    private BukkitTask checkTask;
    private int lastReminderHour = -1;

    public ScheduledRestartManager(RestartAnnouncerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (checkTask != null) {
            return;
        }
        if (ScheduledRestartSpec.fromConfig(plugin.getConfig(), plugin.getLogger()) == null) {
            plugin.getLogger().warning("Scheduled restart disabled: fix scheduled-restart settings in config.yml.");
            return;
        }

        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfigManager().isScheduledRestartEnabled()) {
                    cancel();
                    checkTask = null;
                    return;
                }
                tick();
            }
        }.runTaskTimer(plugin, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        lastReminderHour = -1;
    }

    private void tick() {
        ScheduledRestartSpec s = ScheduledRestartSpec.fromConfig(plugin.getConfig(), plugin.getLogger());
        if (s == null) {
            plugin.getLogger().warning("Scheduled restart config is invalid; stopping the scheduled-restart task.");
            stop();
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRestart = s.nextOccurrenceAfter(now);
        long secondsUntil = ChronoUnit.SECONDS.between(now, nextRestart);
        if (secondsUntil < 0) {
            secondsUntil = 0;
        }

        if (plugin.getRestartManager().isRunning()) {
            return;
        }

        if (secondsUntil <= ONE_HOUR_SECONDS && secondsUntil > 0) {
            String when = s.formatReminderTime(nextRestart);
            plugin.getLogger().info("Scheduled restart at " + when + " – starting 1hr countdown (in " + secondsUntil + "s)");
            plugin.getRestartManager().startRestart((int) secondsUntil, TEN_MINUTES_SECONDS, "chat", true);
            stop();
            return;
        }

        int reminderHours = plugin.getConfigManager().getScheduledRestartReminderIntervalHours();
        if (reminderHours <= 0) {
            reminderHours = 4;
        }
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        if (secondsUntil > ONE_HOUR_SECONDS && currentMinute == 0 && (currentHour % reminderHours == 0) && currentHour != lastReminderHour) {
            lastReminderHour = currentHour;
            String timeStr = s.formatReminderTime(nextRestart);
            String timezone = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("z"));
            String message = plugin.getMessageManager().getMessage("scheduled-restart.reminder", "§eNext scheduled restart: §f%time% §7(%timezone%)")
                .replace("%time%", timeStr)
                .replace("%timezone%", timezone);
            plugin.getMessageManager().broadcastMessage(message);
        }
    }
}
