package ir.buddy.mint.module.impl;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class FastLadderModule implements Module, Listener {

    private final JavaPlugin plugin;


    private static final double MAX_CLIMB_SPEED = 0.32;
    private static final double MAX_DESCEND_SPEED = -0.42;

    public FastLadderModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Fast Ladders";
    }

    @Override
    public String getConfigPath() {
        return "modules.fast-ladders";
    }

    @Override
    public String getDescription() {
        return "Climb ladders faster and more smoothly using movement keys.";
    }

    @Override
    public void enable() {

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {

        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();


        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (!p.isClimbing()) return;
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, p)) return;


        if (event.getTo() == null) return;

        double yDiff = event.getTo().getY() - event.getFrom().getY();
        boolean sneaking = p.isSneaking();

        Vector v = p.getVelocity();
        boolean applied = false;

        if (sneaking) {

            return;
        }


        if (yDiff > 0.05 && yDiff < MAX_CLIMB_SPEED) {
            v.setY(MAX_CLIMB_SPEED);
            applied = true;
        }


        else if (yDiff < -0.05 && yDiff > MAX_DESCEND_SPEED) {
            v.setY(MAX_DESCEND_SPEED);
            applied = true;
        }

        if (applied) {
            p.setVelocity(v);
        }
    }


    private boolean isClimbable(Material m) {
        return switch (m) {
            case LADDER,
                 VINE,
                 SCAFFOLDING,
                 GLOW_LICHEN,
                 TWISTING_VINES,
                 TWISTING_VINES_PLANT,
                 WEEPING_VINES,
                 WEEPING_VINES_PLANT -> true;
            default -> false;
        };
    }
}
