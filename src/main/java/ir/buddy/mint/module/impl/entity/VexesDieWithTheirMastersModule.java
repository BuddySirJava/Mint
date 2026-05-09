package ir.buddy.mint.module.impl.entity;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VexesDieWithTheirMastersModule implements Module, Listener {

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, Set<UUID>> ownerToVexes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> vexToOwner = new ConcurrentHashMap<>();

    public VexesDieWithTheirMastersModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Vexes Die With Their Masters";
    }

    @Override
    public String getConfigPath() {
        return "modules.vexes-die-with-their-masters";
    }

    @Override
    public String getDescription() {
        return "Vexes are removed when their summoner dies.";
    }

    @Override
    public boolean isServerScoped() {
        return true;
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        ownerToVexes.clear();
        vexToOwner.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVexSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Vex vex)) {
            return;
        }
        LivingEntity summoner = resolveSummoner(vex);
        if (summoner == null) {
            return;
        }
        UUID vexId = vex.getUniqueId();
        UUID ownerId = summoner.getUniqueId();
        vexToOwner.put(vexId, ownerId);
        ownerToVexes.computeIfAbsent(ownerId, unused -> ConcurrentHashMap.newKeySet()).add(vexId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        UUID deadEntityId = event.getEntity().getUniqueId();

        if (event.getEntity() instanceof Vex deadVex) {
            removeTrackedVex(deadVex.getUniqueId());
            return;
        }

        Set<UUID> vexIds = ownerToVexes.remove(deadEntityId);
        if (vexIds == null || vexIds.isEmpty()) {
            return;
        }
        for (UUID vexId : vexIds) {
            vexToOwner.remove(vexId);
            Entity entity = event.getEntity().getWorld().getEntity(vexId);
            if (entity instanceof Vex vex) {
                FoliaScheduler.runEntity(plugin, vex, () -> {
                    if (vex.isValid() && !vex.isDead()) {
                        vex.remove();
                    }
                });
            }
        }
    }

    private void removeTrackedVex(UUID vexId) {
        UUID ownerId = vexToOwner.remove(vexId);
        if (ownerId == null) {
            return;
        }
        Set<UUID> vexIds = ownerToVexes.get(ownerId);
        if (vexIds == null) {
            return;
        }
        vexIds.remove(vexId);
        if (vexIds.isEmpty()) {
            ownerToVexes.remove(ownerId, vexIds);
        }
    }

    private LivingEntity resolveSummoner(Vex vex) {
        LivingEntity summoner = invokeSummonerMethod(vex, "getSummoner");
        if (summoner != null) {
            return summoner;
        }
        return invokeSummonerMethod(vex, "getOwner");
    }

    private LivingEntity invokeSummonerMethod(Vex vex, String methodName) {
        try {
            Method method = vex.getClass().getMethod(methodName);
            Object value = method.invoke(vex);
            if (value instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
            if (value instanceof Entity entity && entity instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        } catch (ReflectiveOperationException ignored) {
            
        }
        return null;
    }
}
