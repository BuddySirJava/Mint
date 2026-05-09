package ir.buddy.mint;

import ir.buddy.mint.command.MintCommand;
import ir.buddy.mint.config.PluginConfigValidator;
import ir.buddy.mint.gui.ModuleToggleGui;
import ir.buddy.mint.util.DisplayBackendManager;
import ir.buddy.mint.util.DisplayEntityController;
import ir.buddy.mint.util.MintJdbcLibraryLoader;
import ir.buddy.mint.util.MintLang;
import ir.buddy.mint.util.MintMetricsBootstrap;
import ir.buddy.mint.util.MintRuntimeLibraries;
import ir.buddy.mint.integration.MintPlaceholders;
import ir.buddy.mint.integration.ProtectionSupport;
import ir.buddy.mint.module.ModuleManager;
import ir.buddy.mint.module.impl.building.CarpetGeometryModule;
import ir.buddy.mint.player.PlayerModuleActivityListener;
import ir.buddy.mint.player.PlayerModulePreferences;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class MintPlugin extends JavaPlugin {

    private ModuleManager moduleManager;
    private PlayerModulePreferences playerModulePreferences;
    private ProtectionSupport protectionSupport;
    private MintPlaceholders placeholders;
    private PlayerModuleActivityListener playerModuleActivityListener;
    private ModuleToggleGui moduleToggleGui;
    private FileConfiguration guiConfig;
    private FileConfiguration langConfig;
    private MintLang mintLang;
    private DisplayEntityController displayEntityController;
    private DisplayBackendManager displayBackendManager;
    private URLClassLoader runtimeLibrariesClassLoader;
    private final Object runtimeLibrariesLock = new Object();
    private volatile boolean bstatsHooked;

    @Override
   public void onEnable() {
       syncMainConfig();
       loadGuiConfig();
       loadLangConfig();
       this.mintLang = new MintLang(this);
       MintRuntimeLibraries.prepare(this, this::completeMintStartup);
    }

    


    private void completeMintStartup() {
        resetRuntimeLibrariesClassLoader();
        hookBstatsIfPossible();

        this.playerModulePreferences = new PlayerModulePreferences(this);
        this.protectionSupport = new ProtectionSupport();
        this.displayEntityController = new DisplayEntityController(this);
        this.displayEntityController.register();
        this.displayBackendManager = new DisplayBackendManager(this);
        this.displayBackendManager.initializeOrReload();
        this.moduleManager = new ModuleManager(this);
        this.moduleManager.registerModules();
        PluginConfigValidator.validateAndLog(this, moduleManager);

        getLogger().info("Registering module recipes...");
        this.moduleManager.registerRecipes();

        this.playerModulePreferences.preloadToggles(Bukkit.getOnlinePlayers(), this.moduleManager.getModules());
        this.moduleManager.enableModules();
        this.playerModuleActivityListener = new PlayerModuleActivityListener(this);
        Bukkit.getPluginManager().registerEvents(playerModuleActivityListener, this);
        this.moduleToggleGui = new ModuleToggleGui(this);
        Bukkit.getPluginManager().registerEvents(moduleToggleGui, this);
        moduleToggleGui.start();

        MintCommand command = new MintCommand(this);
        if (getCommand("mint") != null) {
            getCommand("mint").setExecutor(command);
            getCommand("mint").setTabCompleter(command);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholders = new MintPlaceholders(this);
            this.placeholders.register();
            getLogger().info("PlaceholderAPI hook enabled.");
        }

        getLogger().info("Mint enabled.");
    }

    private void hookBstatsIfPossible() {
        if (bstatsHooked) {
            return;
        }
        if (MintMetricsBootstrap.tryEnable(this, 31054)) {
            bstatsHooked = true;
        }
    }

    @Override
    public void onDisable() {
        CarpetGeometryModule.resetRecipeRegistrationState();
        if (moduleManager != null) {
            moduleManager.disableModules();
        }
        if (moduleToggleGui != null) {
            moduleToggleGui.stop();
            moduleToggleGui = null;
        }
        if (placeholders != null) {
            placeholders.unregister();
            placeholders = null;
        }
        if (playerModulePreferences != null) {
            playerModulePreferences.close();
            playerModulePreferences = null;
        }
        displayBackendManager = null;
        displayEntityController = null;
        resetRuntimeLibrariesClassLoader();
        getLogger().info("Mint disabled.");
    }

    public void reloadPlugin() {
        YamlConfiguration oldConfigSnapshot = new YamlConfiguration();
        getConfig().getValues(true).forEach(oldConfigSnapshot::set);
        if (playerModulePreferences != null) {
            playerModulePreferences.close();
        }
        resetRuntimeLibrariesClassLoader();
        reloadConfig();
        syncMainConfig();
        MintRuntimeLibraries.prepare(this, () -> {
            loadGuiConfig();
            loadLangConfig();
            if (playerModulePreferences != null) {
                playerModulePreferences.reload();
            } else {
                playerModulePreferences = new PlayerModulePreferences(this);
            }
            if (displayEntityController == null) {
                displayEntityController = new DisplayEntityController(this);
                displayEntityController.register();
            } else {
                displayEntityController.reload();
            }
            if (displayBackendManager == null) {
                displayBackendManager = new DisplayBackendManager(this);
            }
            displayBackendManager.initializeOrReload();
            playerModulePreferences.preloadToggles(Bukkit.getOnlinePlayers(), moduleManager.getModules());
            PluginConfigValidator.validateAndLog(this, moduleManager);
            moduleManager.refreshActiveModules();
            moduleManager.reloadChangedModules(oldConfigSnapshot);
            getLogger().info("Re-registering module recipes...");
            moduleManager.registerRecipes();
            if (moduleToggleGui != null) {
                moduleToggleGui.reload();
            }
            hookBstatsIfPossible();
        });
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public PlayerModulePreferences getPlayerModulePreferences() {
        return playerModulePreferences;
    }

    public ProtectionSupport getProtectionSupport() {
        return protectionSupport;
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    


    public void saveGuiConfig() {
        File file = new File(getDataFolder(), "gui.yml");
        try {
            guiConfig.save(file);
        } catch (IOException ex) {
            getLogger().warning("Failed to save gui.yml: " + ex.getMessage());
        }
    }

    


    public void reloadGuiFromDisk() {
        loadGuiConfig();
        if (moduleToggleGui != null) {
            moduleToggleGui.reload();
        }
    }

    


    public void onGuiConfigUpdatedInMemory() {
        if (moduleToggleGui != null) {
            moduleToggleGui.reload();
        }
    }

    public FileConfiguration getLangConfig() {
        return langConfig;
    }

    public MintLang getMintLang() {
        return mintLang;
    }

    public DisplayEntityController getDisplayEntityController() {
        return displayEntityController;
    }

    public DisplayBackendManager getDisplayBackendManager() {
        return displayBackendManager;
    }

    



    public ClassLoader runtimeLibrariesClassLoaderOrNull() {
        URLClassLoader existing = runtimeLibrariesClassLoader;
        if (existing != null) {
            return existing;
        }
        synchronized (runtimeLibrariesLock) {
            if (runtimeLibrariesClassLoader != null) {
                return runtimeLibrariesClassLoader;
            }
            runtimeLibrariesClassLoader = MintJdbcLibraryLoader.tryCreate(this);
            if (runtimeLibrariesClassLoader != null) {
                File libDir = new File(getDataFolder(), "lib");
                String[] names = libDir.list((dir, name) -> name.endsWith(".jar"));
                int count = names == null ? 0 : names.length;
                getLogger().info("Runtime libraries: " + count + " jar(s) in plugins/Mint/lib/");
            }
            return runtimeLibrariesClassLoader;
        }
    }

    void resetRuntimeLibrariesClassLoader() {
        synchronized (runtimeLibrariesLock) {
            URLClassLoader cl = runtimeLibrariesClassLoader;
            runtimeLibrariesClassLoader = null;
            if (cl != null) {
                try {
                    cl.close();
                } catch (IOException ignored) {
                    
                }
            }
        }
    }

    public ModuleToggleGui getModuleToggleGui() {
        return moduleToggleGui;
    }

    private void syncMainConfig() {
        saveDefaultConfig();

        try (InputStream defaultStream = getResource("config.yml")) {
            if (defaultStream == null) {
                getLogger().warning("Failed to load bundled config.yml defaults.");
                return;
            }

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );

            FileConfiguration config = getConfig();
            config.setDefaults(defaults);
            config.options().copyDefaults(true);

            config.set("config-version", MintVersion.plugin(this));
            saveConfig();
        } catch (IOException ex) {
            getLogger().warning("Failed to persist config.yml defaults: " + ex.getMessage());
        }
    }

    private void loadGuiConfig() {
        if (!new File(getDataFolder(), "gui.yml").exists()) {
            saveResource("gui.yml", false);
        }

        File file = new File(getDataFolder(), "gui.yml");
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        try (InputStream defaultStream = getResource("gui.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                );
                loaded.setDefaults(defaults);
                loaded.options().copyDefaults(true);
                loaded.set("gui-version", MintVersion.plugin(this));
                loaded.save(file);
            }
        } catch (IOException ex) {
            getLogger().warning("Failed to persist gui.yml defaults: " + ex.getMessage());
        }

        this.guiConfig = loaded;
    }

    private void loadLangConfig() {
        if (!new File(getDataFolder(), "lang.yml").exists()) {
            saveResource("lang.yml", false);
        }

        File file = new File(getDataFolder(), "lang.yml");
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        try (InputStream defaultStream = getResource("lang.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                );
                loaded.setDefaults(defaults);
                loaded.options().copyDefaults(true);
                loaded.set("lang-version", MintVersion.plugin(this));
                loaded.save(file);
            }
        } catch (IOException ex) {
            getLogger().warning("Failed to persist lang.yml defaults: " + ex.getMessage());
        }

        this.langConfig = loaded;
    }
}
