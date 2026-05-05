package ir.buddy.mint.module;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.impl.AutoRefillModule;
import ir.buddy.mint.module.impl.AutoToolModule;
import ir.buddy.mint.module.impl.BedrockBridgingModule;
import ir.buddy.mint.module.impl.BlockDecorationModule;
import ir.buddy.mint.module.impl.BoatImprovementsModule;
import ir.buddy.mint.module.impl.CarpetGeometryModule;
import ir.buddy.mint.module.impl.ChickenGlideModule;
import ir.buddy.mint.module.impl.DoorKnockModule;
import ir.buddy.mint.module.impl.DoubleDoorModule;
import ir.buddy.mint.module.impl.FastLadderModule;
import ir.buddy.mint.module.impl.InvisibleFrameModule;
import ir.buddy.mint.module.impl.LadderPlaceModule;
import ir.buddy.mint.module.impl.LeashDecorationModule;
import ir.buddy.mint.module.impl.MinecartImprovementsModule;
import ir.buddy.mint.module.impl.MixedSlabModule;
import ir.buddy.mint.module.impl.PaintingScrollModule;
import ir.buddy.mint.module.impl.ShiftRightClickSortModule;
import ir.buddy.mint.module.impl.SlabBreakerModule;
import ir.buddy.mint.module.impl.SprintRetentionModule;
import ir.buddy.mint.module.impl.VerticalSlabModule;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ScheduledTaskHandle;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class ModuleManager {

    private final MintPlugin plugin;
    private final List<Module> modules = new ArrayList<>();
    private final Set<String> activeModules = new HashSet<>();
    private final Deque<PendingEnable> enableQueue = new ArrayDeque<>();
    private final Map<String, Long> pendingEnableTokens = new HashMap<>();
    private long enableTokenCounter = 0L;
   private ScheduledTaskHandle enablePumpTask = null;
   private boolean isInitialLoad = true;

   private record PendingEnable(Module module, long token) {}

    public ModuleManager(MintPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerModules() {
       modules.clear();


       modules.add(new BedrockBridgingModule(plugin));
       modules.add(new BoatImprovementsModule(plugin));
       modules.add(new MinecartImprovementsModule(plugin));
       modules.add(new FastLadderModule(plugin));
       modules.add(new ShiftRightClickSortModule(plugin));
       modules.add(new SprintRetentionModule(plugin));
       modules.add(new DoubleDoorModule(plugin));
       modules.add(new BlockDecorationModule(plugin));
       modules.add(new CarpetGeometryModule(plugin));
       modules.add(new AutoToolModule(plugin));
       modules.add(new DoorKnockModule(plugin));
       modules.add(new LadderPlaceModule(plugin));
       modules.add(new InvisibleFrameModule(plugin));
       modules.add(new LeashDecorationModule(plugin));
       modules.add(new ChickenGlideModule(plugin));
       modules.add(new PaintingScrollModule(plugin));
       modules.add(new SlabBreakerModule(plugin));
       modules.add(new MixedSlabModule(plugin));
       modules.add(new VerticalSlabModule(plugin));
       modules.add(new AutoRefillModule(plugin));
   }

   public void registerRecipes() {
       for (Module module : modules) {
           if (module instanceof CarpetGeometryModule carpetModule) {
               try {
                   carpetModule.registerRecipesEarly();
               } catch (Exception e) {
                   plugin.getLogger().warning("Failed to register recipes for " + module.getName() + ": " + e.getMessage());
               }
           }
       }
   }

   public void enableModules() {
       isInitialLoad = true;
       refreshActiveModules();
       isInitialLoad = false;
   }

    public void disableModules() {
       clearPendingEnables();
       for (Module module : modules) {
           if (isModuleActive(module)) {
               try {
                   module.disable();
               } catch (Exception ex) {
                   plugin.getLogger().warning(
                       "Error disabling module " + module.getName() + ": " + ex.getMessage()
                   );
               }
           }
       }
       activeModules.clear();
    }

    /**
     * Reconciles which modules have listeners/register global hooks versus {@code enabled} in config.
     * Per-player on/off lives in {@link ir.buddy.mint.player.PlayerModulePreferences}; it does not
     * unload modules from the whole server here.
     */
    public void refreshActiveModules() {
        for (Module module : modules) {
            refreshModuleActivity(module);
        }
    }

    public void refreshModuleActivity(Module module) {
        boolean shouldBeActive = shouldBeActive(module, plugin.getConfig());
        boolean isActive = isModuleActive(module);

        if (shouldBeActive && !isActive) {
            requestModuleEnable(module);
            return;
        }

        if (!shouldBeActive) {
            cancelPendingEnable(module);
            if (isActive) {
                module.disable();
                activeModules.remove(module.getConfigPath());
                plugin.getLogger().info("Disabled module: " + module.getName());
            }
        }
    }

    public void reloadChangedModules(FileConfiguration previousConfig) {
        FileConfiguration currentConfig = plugin.getConfig();

        for (Module module : modules) {
            Map<String, Object> oldSnapshot = moduleSnapshot(previousConfig, module);
            Map<String, Object> newSnapshot = moduleSnapshot(currentConfig, module);
            boolean changed = !Objects.equals(oldSnapshot, newSnapshot);

            if (!changed) {
                continue;
            }

            cancelPendingEnable(module);
            if (isModuleActive(module)) {
                module.disable();
                activeModules.remove(module.getConfigPath());
            }

            if (shouldBeActive(module, currentConfig)) {
                requestModuleEnable(module);
                plugin.getLogger().info("Reloaded changed module: " + module.getName());
            } else {
                plugin.getLogger().info("Reloaded changed module as inactive: " + module.getName());
            }
        }
    }

    private boolean shouldBeActive(Module module, FileConfiguration config) {
        return module.isEnabledByConfig(config);
    }

    private boolean isModuleActive(Module module) {
        return activeModules.contains(module.getConfigPath());
    }

    private void requestModuleEnable(Module module) {
       String key = module.getConfigPath();
       if (activeModules.contains(key) || pendingEnableTokens.containsKey(key)) {
           return;
       }


       if (isInitialLoad) {
           try {
               if (module.supportsAsyncPreparation()) {
                   module.prepareEnable();
               }
               module.enable();
               activeModules.add(key);
               plugin.getLogger().info("Enabled module: " + module.getName());
           } catch (Exception ex) {
               plugin.getLogger().warning(
                   "Failed to enable module " + module.getName() + ": " + ex.getMessage()
               );
               ex.printStackTrace();
           }
           return;
       }

       long token = ++enableTokenCounter;
       pendingEnableTokens.put(key, token);

       if (module.supportsAsyncPreparation()) {
           if (!plugin.isEnabled()) {
               pendingEnableTokens.remove(key);
               return;
           }
           FoliaScheduler.runAsync(plugin, () -> {
               try {
                   module.prepareEnable();
                   enqueueEnable(module, token);
               } catch (Exception ex) {
                   if (plugin.isEnabled()) {
                       FoliaScheduler.runGlobal(plugin, () -> {
                           if (tokenMatches(module, token)) {
                               pendingEnableTokens.remove(key);
                           }
                           plugin.getLogger().warning(
                                   "Async preparation failed for module " + module.getName() + ": " + ex.getMessage()
                           );
                       });
                   }
               }
           });
           return;
        }

        enqueueEnable(module, token);
    }

    private void enqueueEnable(Module module, long token) {
       Runnable enqueue = () -> {
           if (!plugin.isEnabled()) {
               return;
           }
           if (!tokenMatches(module, token)) {
               return;
           }
           enableQueue.offerLast(new PendingEnable(module, token));
           ensureEnablePumpRunning();
       };

       if (!plugin.isEnabled()) {
           return;
       }
       FoliaScheduler.runGlobal(plugin, enqueue);
   }

    private void ensureEnablePumpRunning() {
       if (enablePumpTask != null) {
           return;
       }
       if (!plugin.isEnabled()) {
           plugin.getLogger().warning("Cannot start enable pump - plugin is not enabled");
           return;
       }
       enablePumpTask = FoliaScheduler.runGlobalAtFixedRate(plugin, 1L, 1L, this::processEnableQueue);
   }

    private void processEnableQueue() {
        PendingEnable pending = enableQueue.pollFirst();
        if (pending == null) {
            stopEnablePumpIfIdle();
            return;
        }

        Module module = pending.module();
        long token = pending.token();
        String key = module.getConfigPath();

        if (!tokenMatches(module, token)) {
            stopEnablePumpIfIdle();
            return;
        }

        if (!shouldBeActive(module, plugin.getConfig())) {
            pendingEnableTokens.remove(key);
            stopEnablePumpIfIdle();
            return;
        }

        if (activeModules.contains(key)) {
            pendingEnableTokens.remove(key);
            stopEnablePumpIfIdle();
            return;
        }

        try {
            module.enable();
            activeModules.add(key);
            plugin.getLogger().info("Enabled module: " + module.getName());
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to enable module " + module.getName() + ": " + ex.getMessage());
        } finally {
            pendingEnableTokens.remove(key);
        }

        stopEnablePumpIfIdle();
    }

    private void cancelPendingEnable(Module module) {
        String key = module.getConfigPath();
        if (pendingEnableTokens.remove(key) == null) {
            return;
        }
        enableQueue.removeIf(pending -> pending.module().getConfigPath().equals(key));
        stopEnablePumpIfIdle();
    }

    private void clearPendingEnables() {
        pendingEnableTokens.clear();
        enableQueue.clear();
        stopEnablePump();
    }

    private void stopEnablePumpIfIdle() {
        if (!enableQueue.isEmpty()) {
            return;
        }
        stopEnablePump();
    }

    private void stopEnablePump() {
        if (enablePumpTask == null) {
            return;
        }
        enablePumpTask.cancel();
        enablePumpTask = null;
    }

    private boolean tokenMatches(Module module, long token) {
        Long currentToken = pendingEnableTokens.get(module.getConfigPath());
        return currentToken != null && currentToken == token;
    }

    private Map<String, Object> moduleSnapshot(FileConfiguration config, Module module) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("enabled-effective", module.isEnabledByConfig(config));

        ConfigurationSection section = config.getConfigurationSection(module.getConfigPath());
        if (section != null) {
            snapshot.putAll(section.getValues(true));
        }

        return snapshot;
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public Optional<Module> getModule(String name) {
        return modules.stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public Optional<Module> findModuleByInput(String input) {
        String normalizedInput = normalizeModuleInput(input);
        for (Module module : modules) {
            String configKey = getModuleKey(module);
            if (normalizeModuleInput(module.getName()).equals(normalizedInput)
                    || normalizeModuleInput(configKey).equals(normalizedInput)) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    public String getModuleKey(Module module) {
        return module.getConfigPath().replaceFirst("^modules\\.", "");
    }

    public static String normalizeModuleInput(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
