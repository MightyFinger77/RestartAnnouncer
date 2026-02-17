package com.restartannouncer.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Checks if EssentialsX (or similar) backup is running via optional soft dependency.
 * Uses reflection so Essentials is not required at compile time.
 */
public class BackupChecker {

    private final Plugin plugin;
    private Object essentialsApi;
    private Method getBackupMethod;
    private Method getTaskLockMethod;
    private boolean resolved;

    public BackupChecker(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolve Essentials API and Backup.getTaskLock() via reflection. Safe to call multiple times.
     */
    private void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess == null || !ess.isEnabled()) {
                return;
            }
            // IEssentials essApi = (IEssentials) ess;  -> get Backup via getBackup() on IEssentials
            Class<?> iEssentialsClass = Class.forName("net.ess3.api.IEssentials");
            if (!iEssentialsClass.isInstance(ess)) {
                return;
            }
            essentialsApi = ess;
            // IEssentials has getBackup() in some versions; in EssentialsX it's on the plugin
            try {
                getBackupMethod = ess.getClass().getMethod("getBackup");
            } catch (NoSuchMethodException e) {
                getBackupMethod = iEssentialsClass.getMethod("getBackup");
            }
            Object backup = getBackupMethod.invoke(essentialsApi);
            if (backup == null) {
                getBackupMethod = null;
                return;
            }
            getTaskLockMethod = backup.getClass().getMethod("getTaskLock");
        } catch (Throwable t) {
            plugin.getLogger().fine("BackupChecker: Essentials not available or no Backup API: " + t.getMessage());
            essentialsApi = null;
            getBackupMethod = null;
            getTaskLockMethod = null;
        }
    }

    /**
     * Returns true if a backup appears to be running (e.g. EssentialsX backup).
     * Returns false if Essentials is not present or backup is not running.
     */
    public boolean isBackupRunning() {
        resolve();
        if (getBackupMethod == null || getTaskLockMethod == null) {
            return false;
        }
        try {
            Object backup = getBackupMethod.invoke(essentialsApi);
            if (backup == null) {
                return false;
            }
            Object future = getTaskLockMethod.invoke(backup);
            if (future instanceof CompletableFuture) {
                return !((CompletableFuture<?>) future).isDone();
            }
            return false;
        } catch (Throwable t) {
            plugin.getLogger().fine("BackupChecker: " + t.getMessage());
            return false;
        }
    }
}
