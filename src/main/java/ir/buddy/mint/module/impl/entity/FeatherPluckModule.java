package ir.buddy.mint.module.impl.entity;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ScheduledTaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FeatherPluckModule implements Module, Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey cooldownUntilKey;
    private final Map<UUID, Long> activeCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> cooldownWorlds = new ConcurrentHashMap<>();
    private ScheduledTaskHandle cooldownTask = ScheduledTaskHandle.NOOP;

    public FeatherPluckModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cooldownUntilKey = new NamespacedKey(plugin, "feather_pluck_cooldown_until");
    }

    @Override
    public String getName() {
        return "Feather Pluck";
    }

    @Override
    public String getConfigPath() {
        return "modules.feather-pluck";
    }

    @Override
    public String getDescription() {
        return "Pluck feathers from chickens with shears.";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startCooldownTask();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        cooldownTask.cancel();
        cooldownTask = ScheduledTaskHandle.NOOP;
        activeCooldowns.clear();
        cooldownWorlds.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractChicken(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Chicken chicken)) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
            return;
        }
        if (!ModuleAccess.canBuild(plugin, player, chicken.getLocation())) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.SHEARS) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = readCooldownUntil(chicken);
        if (cooldownUntil > now) {
            event.setCancelled(true);
            return;
        }

        int dropAmount = ThreadLocalRandom.current().nextInt(getMinFeathers(), getMaxFeathers() + 1);
        chicken.getWorld().dropItemNaturally(chicken.getLocation().add(0, 0.6, 0), new ItemStack(Material.FEATHER, dropAmount));
        chicken.getWorld().playSound(chicken.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1.0f, 1.1f);
        if (!player.getGameMode().isInvulnerable()) {
            damageShears(hand);
        }

        long newCooldownUntil = now + (getCooldownSeconds() * 1000L);
        writeCooldownUntil(chicken, newCooldownUntil);
        activeCooldowns.put(chicken.getUniqueId(), newCooldownUntil);
        cooldownWorlds.put(chicken.getUniqueId(), chicken.getWorld().getUID());
        applyRedHurtState(chicken);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChickenDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken)) {
            return;
        }
        UUID chickenId = chicken.getUniqueId();
        activeCooldowns.remove(chickenId);
        cooldownWorlds.remove(chickenId);
    }

 

    private void damageShears(ItemStack shears) {
        if (!(shears.getItemMeta() instanceof Damageable damageable)) {
            return;
        }
        damageable.setDamage(damageable.getDamage() + 1);
        shears.setItemMeta(damageable);
        if (damageable.getDamage() >= shears.getType().getMaxDurability()) {
            shears.setAmount(0);
        }
    }

    private void startCooldownTask() {
        cooldownTask = FoliaScheduler.runGlobalAtFixedRate(plugin, 8L, 8L, this::tickCooldowns);
    }

    private void tickCooldowns() {
        for (Map.Entry<UUID, Long> entry : activeCooldowns.entrySet()) {
            UUID uuid = entry.getKey();
            UUID worldId = cooldownWorlds.get(uuid);
            if (worldId == null) {
                activeCooldowns.remove(uuid);
                continue;
            }
            World world = Bukkit.getWorld(worldId);
            if (world == null) {
                activeCooldowns.remove(uuid);
                cooldownWorlds.remove(uuid);
                continue;
            }
            Entity entity = world.getEntity(uuid);
            if (!(entity instanceof Chicken chicken)) {
                activeCooldowns.remove(uuid);
                cooldownWorlds.remove(uuid);
                continue;
            }
            FoliaScheduler.runEntity(plugin, chicken, () -> {
                if (!chicken.isValid() || chicken.isDead()) {
                    activeCooldowns.remove(uuid);
                    cooldownWorlds.remove(uuid);
                    return;
                }
                long until = activeCooldowns.getOrDefault(uuid, 0L);
                if (until <= System.currentTimeMillis()) {
                    clearCooldown(chicken);
                    activeCooldowns.remove(uuid);
                    cooldownWorlds.remove(uuid);
                    return;
                }
                applyRedHurtState(chicken);
            });
        }
    }

    private void applyRedHurtState(Chicken chicken) {
        try {
            
            chicken.getClass().getMethod("playHurtAnimation", float.class).invoke(chicken, 0.0f);
        } catch (ReflectiveOperationException ignored) {
            
            chicken.playEffect(EntityEffect.HURT);
        }
    }

    private long readCooldownUntil(Chicken chicken) {
        PersistentDataContainer pdc = chicken.getPersistentDataContainer();
        Long value = pdc.get(cooldownUntilKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    private void writeCooldownUntil(Chicken chicken, long cooldownUntil) {
        chicken.getPersistentDataContainer().set(cooldownUntilKey, PersistentDataType.LONG, cooldownUntil);
    }

    private void clearCooldown(Chicken chicken) {
        chicken.getPersistentDataContainer().remove(cooldownUntilKey);
    }

    private int getCooldownSeconds() {
        return Math.max(1, plugin.getConfig().getInt(getConfigPath() + ".cooldown-seconds", 300));
    }

    private int getMinFeathers() {
        FileConfiguration config = plugin.getConfig();
        return Math.max(1, config.getInt(getConfigPath() + ".feather-drop-min", 1));
    }

    private int getMaxFeathers() {
        FileConfiguration config = plugin.getConfig();
        return Math.max(getMinFeathers(), config.getInt(getConfigPath() + ".feather-drop-max", 3));
    }
}
