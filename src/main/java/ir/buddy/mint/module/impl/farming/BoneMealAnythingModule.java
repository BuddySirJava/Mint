package ir.buddy.mint.module.impl.farming;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class BoneMealAnythingModule implements Module, Listener {

    private static final int VANILLA_DEFAULT_MAX_CACTUS_HEIGHT = 3;
    private static final int VANILLA_DEFAULT_MAX_SUGAR_CANE_HEIGHT = 3;

    private final JavaPlugin plugin;

    public BoneMealAnythingModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Bone Meal Anything";
    }

    @Override
    public String getConfigPath() {
        return "modules.bone-meal-anything";
    }

    @Override
    public String getDescription() {
        return "Allows bone meal to grow nether wart, sugar cane, and cactus with vanilla-like limits.";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerUseBoneMeal(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
            return;
        }
        if (!ModuleAccess.canBuild(plugin, player, clicked.getLocation())) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }

        ItemStack usedItem = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (usedItem.getType() != Material.BONE_MEAL) {
            return;
        }

        boolean grew = switch (clicked.getType()) {
            case NETHER_WART -> tryGrowNetherWart(clicked);
            case SUGAR_CANE -> tryGrowColumnPlant(clicked, Material.SUGAR_CANE, getConfiguredMaxSugarCaneHeight());
            case CACTUS -> tryGrowColumnPlant(clicked, Material.CACTUS, getConfiguredMaxCactusHeight());
            default -> false;
        };

        if (!grew) {
            return;
        }

        clicked.getWorld().playEffect(clicked.getLocation(), Effect.BONE_MEAL_USE, 0);
        if (player.getGameMode() != GameMode.CREATIVE) {
            usedItem.setAmount(usedItem.getAmount() - 1);
        }
        event.setCancelled(true);
    }

    private boolean tryGrowNetherWart(Block block) {
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable)) {
            return false;
        }
        if (ageable.getAge() >= ageable.getMaximumAge()) {
            return false;
        }

        int growthSteps = ThreadLocalRandom.current().nextInt(2, 6);
        int newAge = Math.min(ageable.getMaximumAge(), ageable.getAge() + growthSteps);
        ageable.setAge(newAge);
        block.setBlockData(ageable, false);
        return true;
    }

    private boolean tryGrowColumnPlant(Block block, Material material, int maxHeight) {
        if (maxHeight <= 0) {
            return false;
        }

        int currentHeight = 1;
        Block base = block;
        while (base.getRelative(0, -1, 0).getType() == material) {
            base = base.getRelative(0, -1, 0);
            currentHeight++;
        }

        Block top = block;
        while (top.getRelative(0, 1, 0).getType() == material) {
            top = top.getRelative(0, 1, 0);
            currentHeight++;
        }

        if (currentHeight >= maxHeight) {
            return false;
        }

        Block aboveTop = top.getRelative(0, 1, 0);
        if (!aboveTop.isEmpty()) {
            return false;
        }

        aboveTop.setType(material, false);
        return true;
    }

    private int getConfiguredMaxCactusHeight() {
        return Math.max(1, plugin.getConfig().getInt(getConfigPath() + ".max-cactus-height", VANILLA_DEFAULT_MAX_CACTUS_HEIGHT));
    }

    private int getConfiguredMaxSugarCaneHeight() {
        return Math.max(1, plugin.getConfig().getInt(getConfigPath() + ".max-sugar-cane-height", VANILLA_DEFAULT_MAX_SUGAR_CANE_HEIGHT));
    }
}
