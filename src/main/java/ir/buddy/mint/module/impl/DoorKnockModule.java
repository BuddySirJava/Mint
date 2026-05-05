package ir.buddy.mint.module.impl;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public class DoorKnockModule implements Module, Listener {

    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long BREAK_THRESHOLD_MS = 150;
    private final JavaPlugin plugin;

    public DoorKnockModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Door Knock";
    }

    @Override
    public String getConfigPath() {
        return "modules.door-knock";
    }

    @Override
    public String getDescription() {
        return "Left-click doors to play a knock sound.";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }


@EventHandler(priority = EventPriority.LOWEST)
public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
    if (!ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer())) return;

    Block clickedBlock = event.getClickedBlock();
    if (clickedBlock == null) return;
    if (!Tag.DOORS.isTagged(clickedBlock.getType())) return;

    UUID uuid = event.getPlayer().getUniqueId();
    long now = System.currentTimeMillis();
    Long last = lastClickTime.get(uuid);


    if (last != null && (now - last) < BREAK_THRESHOLD_MS) {
        lastClickTime.remove(uuid);
        return;
    }

    lastClickTime.put(uuid, now);

    clickedBlock.getWorld().playSound(
            clickedBlock.getLocation().add(0.5, 0.5, 0.5),
            Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
            0.5f, 1.2f
    );
}


}
