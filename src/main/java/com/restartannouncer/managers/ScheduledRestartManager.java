package com.restartannouncer.managers;

import com.restartannouncer.RestartAnnouncerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Handles scheduled restarts using system time (24hr format).
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
        String timeStr = plugin.getConfigManager().getScheduledRestartTime();
        if (timeStr.length() < 4) {
            timeStr = String.format("%04d", Integer.parseInt(timeStr));
        }
        int hour = Integer.parseInt(timeStr.substring(0, 2));
        int minute = Integer.parseInt(timeStr.substring(2, 4));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            plugin.getLogger().warning("Scheduled restart time invalid (use 4-digit 24hr format HHmm, e.g. 0400 for 4:00 AM, 1600 for 4:00 PM). Disabling scheduled restart.");
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
                tick(hour, minute);
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

    private void tick(int targetHour, int targetMinute) {
        LocalTime now = LocalTime.now();
        LocalTime target = LocalTime.of(targetHour, targetMinute);

        long secondsUntil = secondsUntil(now, target);
        if (secondsUntil < 0) {
            secondsUntil += 86400; // next day
        }

        // Already in countdown or restart done
        if (plugin.getRestartManager().isRunning()) {
            return;
        }

        // Within final hour: start countdown (1hr-style: announce every 10m, then emergency under 1m)
        if (secondsUntil <= ONE_HOUR_SECONDS && secondsUntil > 0) {
            String timeStr = formatScheduledTime(targetHour, targetMinute);
            plugin.getLogger().info("Scheduled restart at " + timeStr + " – starting 1hr countdown (in " + secondsUntil + "s)");
            plugin.getRestartManager().startRestart((int) secondsUntil, TEN_MINUTES_SECONDS, "chat", true);
            stop(); // stop the scheduler; countdown handles the rest
            return;
        }

        // Every N hours: remind of scheduled time (only when more than 1hr away)
        int reminderHours = plugin.getConfigManager().getScheduledRestartReminderIntervalHours();
        if (reminderHours <= 0) {
            reminderHours = 4;
        }
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        if (secondsUntil > ONE_HOUR_SECONDS && currentMinute == 0 && (currentHour % reminderHours == 0) && currentHour != lastReminderHour) {
            lastReminderHour = currentHour;
            String timeStr = formatScheduledTime(targetHour, targetMinute);
            String timezone = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("z"));
            String message = plugin.getMessageManager().getMessage("scheduled-restart.reminder", "§eScheduled server restart at §f%time% §e%timezone%.")
                .replace("%time%", timeStr)
                .replace("%timezone%", timezone);
            plugin.getMessageManager().broadcastMessage(message);
        }
    }

    private long secondsUntil(LocalTime now, LocalTime target) {
        return ChronoUnit.SECONDS.between(now, target);
    }

    private static String formatScheduledTime(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }
}
