package ir.buddy.mint.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class ProtectionSupport {

    private static final String BYPASS_PERMISSION = "mint.bypass.protection";

    public boolean canBuild(Player player, Location location) {
        if (player == null || location == null) {
            return true;
        }
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }

        return canBuildWithWorldGuard(player, location)
                && canBuildWithGriefPrevention(player, location)
                && canBuildWithTowny(player, location)
                && canBuildWithBentoBox(player, location);
    }

    private boolean canBuildWithWorldGuard(Player player, Location location) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (!(plugin instanceof WorldGuardPlugin wgPlugin)) {
            return true;
        }

        try {
            LocalPlayer localPlayer = wgPlugin.wrapPlayer(player);
            return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .testBuild(BukkitAdapter.adapt(location), localPlayer, Flags.BUILD);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean canBuildWithGriefPrevention(Player player, Location location) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (plugin == null || !plugin.isEnabled() || GriefPrevention.instance == null) {
            return true;
        }

        try {
            return GriefPrevention.instance.allowBuild(player, location) == null;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean canBuildWithTowny(Player player, Location location) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Towny");
        if (plugin == null || !plugin.isEnabled()) {
            return true;
        }

        try {
            Class<?> actionTypeClass = Class.forName("com.palmergames.bukkit.towny.object.TownyPermission$ActionType");
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) actionTypeClass;
            Enum<?> buildAction = Enum.valueOf(enumClass, "BUILD");

            Class<?> playerCacheUtilClass = Class.forName("com.palmergames.bukkit.towny.utils.PlayerCacheUtil");
            Method method = playerCacheUtilClass.getMethod(
                    "getCachePermission",
                    Player.class,
                    Location.class,
                    Integer.class,
                    byte.class,
                    actionTypeClass
            );

            byte data = 0;
            Object result = method.invoke(null, player, location, null, data, buildAction);
            return result instanceof Boolean permitted && permitted;
        } catch (NoSuchMethodException ignored) {
            try {
                Class<?> actionTypeClass = Class.forName("com.palmergames.bukkit.towny.object.TownyPermission$ActionType");
                @SuppressWarnings("unchecked")
                Class<? extends Enum> enumClass = (Class<? extends Enum>) actionTypeClass;
                Enum<?> buildAction = Enum.valueOf(enumClass, "BUILD");

                Class<?> playerCacheUtilClass = Class.forName("com.palmergames.bukkit.towny.utils.PlayerCacheUtil");
                Method legacyMethod = playerCacheUtilClass.getMethod(
                        "getCachePermission",
                        Player.class,
                        Location.class,
                        Integer.class,
                        actionTypeClass
                );

                Object result = legacyMethod.invoke(null, player, location, null, buildAction);
                return result instanceof Boolean permitted && permitted;
            } catch (Throwable ignoredLegacy) {
                return true;
            }
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean canBuildWithBentoBox(Player player, Location location) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BentoBox");
        if (plugin == null || !plugin.isEnabled()) {
            return true;
        }

        try {
            Class<?> bentoBoxClass = Class.forName("world.bentobox.bentobox.BentoBox");
            Method getInstance = bentoBoxClass.getMethod("getInstance");
            Object bentoBox = getInstance.invoke(null);
            if (bentoBox == null) {
                return true;
            }

            Method getIslands = bentoBoxClass.getMethod("getIslands");
            Object islandsManager = getIslands.invoke(bentoBox);
            if (islandsManager == null) {
                return true;
            }

            Method getIslandAt = islandsManager.getClass().getMethod("getIslandAt", Location.class);
            Object optionalIsland = getIslandAt.invoke(islandsManager, location);
            if (!(optionalIsland instanceof java.util.Optional<?> islandOptional) || islandOptional.isEmpty()) {
                return true;
            }

            Object island = islandOptional.get();
            Class<?> userClass = Class.forName("world.bentobox.bentobox.api.user.User");
            Method getUserInstance = userClass.getMethod("getInstance", Player.class);
            Object user = getUserInstance.invoke(null, player);
            if (user == null) {
                return true;
            }

            Class<?> flagsClass = Class.forName("world.bentobox.bentobox.lists.Flags");
            Object placeBlocksFlag;
            try {
                placeBlocksFlag = flagsClass.getField("PLACE_BLOCKS").get(null);
            } catch (NoSuchFieldException missingPlaceFlag) {
                placeBlocksFlag = flagsClass.getField("BLOCK_PLACE").get(null);
            }

            Method isAllowed = resolveBentoIsAllowedMethod(island.getClass(), userClass, placeBlocksFlag.getClass());
            if (isAllowed == null) {
                return true;
            }
            Object allowed = isAllowed.invoke(island, user, placeBlocksFlag);
            return allowed instanceof Boolean permitted && permitted;
        } catch (NoSuchMethodException ignored) {
            return true;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private Method resolveBentoIsAllowedMethod(Class<?> islandClass, Class<?> userClass, Class<?> flagClass) {
        for (Method method : islandClass.getMethods()) {
            if (!method.getName().equals("isAllowed") || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes[0].isAssignableFrom(userClass) && parameterTypes[1].isAssignableFrom(flagClass)) {
                return method;
            }
        }
        return null;
    }
}
