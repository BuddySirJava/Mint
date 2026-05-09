package ir.buddy.mint.util;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.player.storage.PlayerToggleStorageFactory;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;







public final class MintRuntimeLibraries {

    private MintRuntimeLibraries() {
    }

    




    public static void prepare(MintPlugin plugin, Runnable afterLibrariesResolved) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("storage.player-toggles");
        String type = PlayerToggleStorageFactory.readSettings(section).normalizedType();

        boolean tryDownload = plugin.getConfig().getBoolean("storage.download-missing-libraries", true);

        File libDir = new File(plugin.getDataFolder(), "lib");
        if (!libDir.exists() && !libDir.mkdirs()) {
            plugin.getLogger().severe("Could not create directory " + libDir.getAbsolutePath());
            afterLibrariesResolved.run();
            return;
        }

        Map<String, MintBundledArtifacts.Artifact> byFile = new LinkedHashMap<>();
        for (MintBundledArtifacts.Artifact a : MintBundledArtifacts.forStorageType(type)) {
            byFile.put(a.fileName(), a);
        }

        List<MintBundledArtifacts.Artifact> missing = new ArrayList<>();
        for (MintBundledArtifacts.Artifact artifact : byFile.values()) {
            File jar = new File(libDir, artifact.fileName());
            if (jar.isFile() && jar.length() > 0L) {
                continue;
            }
            missing.add(artifact);
        }

        if (missing.isEmpty()) {
            afterLibrariesResolved.run();
            return;
        }

        if (!tryDownload) {
            plugin.getLogger().severe(MintBundledArtifacts.describeMissing(type, missing));
            afterLibrariesResolved.run();
            return;
        }

        List<String> missingNames = new ArrayList<>(missing.size());
        for (MintBundledArtifacts.Artifact a : missing) {
            missingNames.add(a.fileName());
        }
        plugin.getLogger().info("Downloading " + missing.size()
                + " missing runtime librar(y/ies) in the background from Maven Central (" + String.join(", ", missingNames)
                + ").");

        FoliaScheduler.runAsync(plugin, () -> {
            try {
                for (MintBundledArtifacts.Artifact artifact : missing) {
                    File jar = new File(libDir, artifact.fileName());
                    if (jar.isFile() && jar.length() > 0L) {
                        continue;
                    }
                    downloadArtifactWithRetries(plugin, jar, artifact);
                }

                List<MintBundledArtifacts.Artifact> stillMissing = listStillMissing(byFile.values(), libDir);

                if (!stillMissing.isEmpty()) {
                    plugin.getLogger().severe(MintBundledArtifacts.describeMissing(type, stillMissing));
                }

                FoliaScheduler.runGlobal(plugin, () -> {
                    if (!plugin.isEnabled()) {
                        return;
                    }
                    afterLibrariesResolved.run();
                });
            } catch (Throwable fatal) {
                plugin.getLogger().log(Level.SEVERE, "Runtime library download failed unexpectedly.", fatal);
                FoliaScheduler.runGlobal(plugin, () -> {
                    if (!plugin.isEnabled()) {
                        return;
                    }
                    List<MintBundledArtifacts.Artifact> vanished = listStillMissing(byFile.values(), libDir);
                    if (!vanished.isEmpty()) {
                        plugin.getLogger().severe(MintBundledArtifacts.describeMissing(type, vanished));
                    }
                    afterLibrariesResolved.run();
                });
            }
        });
    }

    private static void downloadArtifactWithRetries(
            MintPlugin plugin,
            File jar,
            MintBundledArtifacts.Artifact artifact
    ) {
        IOException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (!plugin.isEnabled()) {
                return;
            }
            if (jar.isFile() && jar.length() > 0L) {
                return;
            }
            try {
                MintMavenDownload.downloadTo(jar, artifact, plugin.getLogger());
                return;
            } catch (IOException ex) {
                last = ex;
                plugin.getLogger().log(Level.WARNING, "Could not download " + artifact.fileName()
                        + (attempt < 2 ? " (retrying): " : " (giving up): ") + ex.getMessage());
                if (attempt < 2) {
                    try {
                        Thread.sleep(450L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        if (last != null) {
            plugin.getLogger().log(Level.WARNING, "Library " + artifact.fileName()
                    + " is still missing after retries; JDBC/Mongo features may fall back.", last);
        }
    }

    private static List<MintBundledArtifacts.Artifact> listStillMissing(
            Iterable<MintBundledArtifacts.Artifact> required,
            File libDir
    ) {
        List<MintBundledArtifacts.Artifact> out = new ArrayList<>();
        for (MintBundledArtifacts.Artifact artifact : required) {
            File jar = new File(libDir, artifact.fileName());
            if (!jar.isFile() || jar.length() == 0L) {
                out.add(artifact);
            }
        }
        return out;
    }
}
