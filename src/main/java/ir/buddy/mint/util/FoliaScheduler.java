package ir.buddy.mint.util;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Schedules work for Folia (region/entity-bound) and plain Paper (main-thread Bukkit tasks).
 * <p>
 * Paper documents recommend using {@link org.bukkit.scheduler.BukkitScheduler} when the server is
 * not Folia; using only {@link GlobalRegionScheduler} / {@link RegionScheduler} for repeating work
 * can misbehave on standard Paper, so this class switches implementations based on runtime.
 */
public final class FoliaScheduler {

    private static final boolean FOLIA = hasClass("io.papermc.paper.threadedregions.RegionizedServer");

    private FoliaScheduler() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static GlobalRegionScheduler global() {
        return Bukkit.getServer().getGlobalRegionScheduler();
    }

    private static RegionScheduler region() {
        return Bukkit.getServer().getRegionScheduler();
    }

    private static AsyncScheduler async() {
        return Bukkit.getServer().getAsyncScheduler();
    }

    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        if (FOLIA) {
            global().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static ScheduledTaskHandle runGlobalLater(JavaPlugin plugin, long delayTicks, Runnable task) {
        if (FOLIA) {
            ScheduledTask st = global().runDelayed(plugin, unused -> task.run(), delayTicks);
            return wrapCancel(st);
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public static ScheduledTaskHandle runGlobalAtFixedRate(JavaPlugin plugin, long delayTicks, long periodTicks, Runnable task) {
        if (FOLIA) {
            ScheduledTask st = global().runAtFixedRate(plugin, unused -> task.run(), delayTicks, periodTicks);
            return wrapCancel(st);
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    public static void runRegion(JavaPlugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            region().execute(plugin, location, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static ScheduledTaskHandle runRegionLater(JavaPlugin plugin, Location location, long delayTicks, Runnable task) {
        if (FOLIA) {
            ScheduledTask st = region().runDelayed(plugin, location, unused -> task.run(), delayTicks);
            return wrapCancel(st);
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public static ScheduledTaskHandle runRegionAtFixedRate(
            JavaPlugin plugin,
            Location location,
            long initialDelayTicks,
            long periodTicks,
            Runnable task
    ) {
        if (FOLIA) {
            ScheduledTask st = region().runAtFixedRate(plugin, location, unused -> task.run(), initialDelayTicks, periodTicks);
            return wrapCancel(st);
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks));
    }

    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        if (FOLIA) {
            EntityScheduler scheduler = entity.getScheduler();
            scheduler.execute(plugin, task, null, 0L);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static ScheduledTaskHandle runEntityLater(JavaPlugin plugin, Entity entity, long delayTicks, Runnable task) {
        if (FOLIA) {
            ScheduledTask st = entity.getScheduler().runDelayed(plugin, unused -> task.run(), null, delayTicks);
            return wrapCancel(st);
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    /**
     * Repeating task bound to the entity (follows across regions on Folia).
     *
     * @param retired optional cleanup when the entity is retired/unloaded
     */
    public static ScheduledTaskHandle runEntityAtFixedRate(
            JavaPlugin plugin,
            Entity entity,
            long initialDelayTicks,
            long periodTicks,
            Runnable task,
            Runnable retired
    ) {
        if (FOLIA) {
            ScheduledTask st = entity.getScheduler().runAtFixedRate(
                    plugin,
                    unused -> task.run(),
                    retired,
                    initialDelayTicks,
                    periodTicks
            );
            return wrapCancel(st);
        }
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid()) {
                if (retired != null) {
                    retired.run();
                }
                self[0].cancel();
                return;
            }
            task.run();
        }, initialDelayTicks, periodTicks);
        return wrapBukkit(self[0]);
    }

    public static ScheduledTaskHandle runEntityAtFixedRate(
            JavaPlugin plugin,
            Entity entity,
            long initialDelayTicks,
            long periodTicks,
            Runnable task
    ) {
        return runEntityAtFixedRate(plugin, entity, initialDelayTicks, periodTicks, task, null);
    }

    /**
     * Off-thread work on Paper/Folia async scheduler (IO, CPU-heavy prep). Not for world/entity access.
     */
    public static void runAsync(JavaPlugin plugin, Runnable task) {
        if (FOLIA) {
            async().runNow(plugin, unused -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    private static ScheduledTaskHandle wrapCancel(ScheduledTask task) {
        return () -> task.cancel();
    }

    private static ScheduledTaskHandle wrapBukkit(BukkitTask task) {
        return () -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        };
    }

    private static boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
