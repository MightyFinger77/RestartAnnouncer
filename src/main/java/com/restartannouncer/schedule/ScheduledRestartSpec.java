package com.restartannouncer.schedule;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Parsed scheduled-restart configuration: when the next restart instant should occur.
 */
public final class ScheduledRestartSpec {

    public enum Recurrence {
        DAILY,
        WEEKLY
    }

    private static final DateTimeFormatter USER_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
    private static final DateTimeFormatter USER_DATE_TIME = DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm", Locale.getDefault());

    private final Recurrence recurrence;
    private final LocalTime time;
    /** Used when recurrence is {@link Recurrence#WEEKLY}. */
    private final DayOfWeek dayOfWeek;
    /** 1 = every week, 2 = every two weeks, ... Only for {@link Recurrence#WEEKLY}. */
    private final int intervalWeeks;
    /** Required when {@link #intervalWeeks} &gt; 1; must fall on {@link #dayOfWeek}. */
    private final LocalDate weekAnchor;

    private ScheduledRestartSpec(Recurrence recurrence, LocalTime time, DayOfWeek dayOfWeek,
                                 int intervalWeeks, LocalDate weekAnchor) {
        this.recurrence = recurrence;
        this.time = time;
        this.dayOfWeek = dayOfWeek;
        this.intervalWeeks = intervalWeeks;
        this.weekAnchor = weekAnchor;
    }

    public Recurrence getRecurrence() {
        return recurrence;
    }

    /**
     * Next restart instant strictly after {@code now} (same rules as "when does countdown start").
     */
    public LocalDateTime nextOccurrenceAfter(LocalDateTime now) {
        return switch (recurrence) {
            case DAILY -> nextDaily(now);
            case WEEKLY -> intervalWeeks <= 1 ? nextWeeklyEveryWeek(now) : nextWeeklyAnchored(now);
        };
    }

    public String formatReminderTime(LocalDateTime next) {
        if (recurrence == Recurrence.DAILY) {
            return USER_TIME.format(time);
        }
        return USER_DATE_TIME.format(next);
    }

    public static ScheduledRestartSpec fromConfig(FileConfiguration config, Logger log) {
        LocalTime time = parseTime(config, log);
        if (time == null) {
            return null;
        }

        Recurrence recurrence = parseRecurrence(config.getString("scheduled-restart.recurrence", "DAILY"), log);
        if (recurrence == null) {
            return null;
        }

        if (recurrence == Recurrence.DAILY) {
            return new ScheduledRestartSpec(Recurrence.DAILY, time, null, 1, null);
        }

        DayOfWeek dow = parseDayOfWeek(config.getString("scheduled-restart.day-of-week"), log);
        if (dow == null) {
            log.warning("scheduled-restart.day-of-week is required when recurrence is WEEKLY (e.g. SUNDAY, MON).");
            return null;
        }
        int intervalWeeks = Math.max(1, config.getInt("scheduled-restart.interval-weeks", 1));
        LocalDate anchor = null;
        if (intervalWeeks > 1) {
            String anchorStr = config.getString("scheduled-restart.week-anchor-date");
            if (anchorStr == null || anchorStr.isBlank()) {
                log.warning("scheduled-restart.week-anchor-date is required when interval-weeks is greater than 1 (YYYY-MM-DD, must match day-of-week).");
                return null;
            }
            try {
                anchor = LocalDate.parse(anchorStr.trim());
            } catch (DateTimeException e) {
                log.warning("scheduled-restart.week-anchor-date must be YYYY-MM-DD, got: " + anchorStr);
                return null;
            }
            if (!anchor.getDayOfWeek().equals(dow)) {
                log.warning("scheduled-restart.week-anchor-date (" + anchor + ") must be a " + dow + " to match day-of-week.");
                return null;
            }
        }
        return new ScheduledRestartSpec(Recurrence.WEEKLY, time, dow, intervalWeeks, anchor);
    }

    private static LocalTime parseTime(FileConfiguration config, Logger log) {
        Object v = config.get("scheduled-restart.time");
        if (v == null) {
            return LocalTime.of(4, 0);
        }
        String s;
        if (v instanceof Number) {
            s = String.format("%04d", ((Number) v).intValue());
        } else {
            s = v.toString().trim().replace(":", "");
        }
        if (s.isEmpty()) {
            return LocalTime.of(4, 0);
        }
        try {
            int n = Integer.parseInt(s);
            s = String.format("%04d", n);
        } catch (NumberFormatException e) {
            log.warning("scheduled-restart.time invalid (use 4-digit 24hr HHmm).");
            return null;
        }
        if (s.length() < 4) {
            log.warning("scheduled-restart.time invalid (use 4-digit 24hr HHmm).");
            return null;
        }
        int hour = Integer.parseInt(s.substring(0, 2));
        int minute = Integer.parseInt(s.substring(2, 4));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            log.warning("scheduled-restart.time invalid hour/minute.");
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    private static Recurrence parseRecurrence(String raw, Logger log) {
        if (raw == null || raw.isBlank()) {
            return Recurrence.DAILY;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if ("MONTHLY".equals(u)) {
            log.warning("scheduled-restart.recurrence MONTHLY is no longer supported; use DAILY or WEEKLY.");
            return null;
        }
        try {
            return Recurrence.valueOf(u);
        } catch (IllegalArgumentException e) {
            log.warning("scheduled-restart.recurrence must be DAILY or WEEKLY, got: " + raw);
            return null;
        }
    }

    static DayOfWeek parseDayOfWeek(String raw, Logger log) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return DayOfWeek.valueOf(t);
        } catch (IllegalArgumentException ignored) {
        }
        if (t.length() < 3) {
            log.warning("day-of-week too short: " + raw);
            return null;
        }
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.name().startsWith(t)) {
                return d;
            }
        }
        log.warning("Unrecognized day-of-week: " + raw);
        return null;
    }

    private LocalDateTime nextDaily(LocalDateTime now) {
        LocalDate d = now.toLocalDate();
        LocalDateTime next = LocalDateTime.of(d, time);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next;
    }

    private LocalDateTime nextWeeklyEveryWeek(LocalDateTime now) {
        LocalDate d = now.toLocalDate();
        int daysUntil = (dayOfWeek.getValue() - d.getDayOfWeek().getValue() + 7) % 7;
        LocalDate cand = d.plusDays(daysUntil);
        LocalDateTime dt = LocalDateTime.of(cand, time);
        if (!dt.isAfter(now)) {
            dt = dt.plusWeeks(1);
        }
        return dt;
    }

    private LocalDateTime nextWeeklyAnchored(LocalDateTime now) {
        LocalDateTime first = LocalDateTime.of(weekAnchor, time);
        if (first.isAfter(now)) {
            return first;
        }
        long periodDays = 7L * intervalWeeks;
        long daysBetween = ChronoUnit.DAYS.between(weekAnchor, now.toLocalDate());
        long periodsElapsed = daysBetween / periodDays;
        LocalDate candidate = weekAnchor.plusDays(periodsElapsed * periodDays);
        LocalDateTime dt = LocalDateTime.of(candidate, time);
        if (!dt.isAfter(now)) {
            candidate = candidate.plusDays(periodDays);
            dt = LocalDateTime.of(candidate, time);
        }
        return dt;
    }
}
