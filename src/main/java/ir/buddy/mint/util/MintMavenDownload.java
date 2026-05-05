package ir.buddy.mint.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public final class MintMavenDownload {

    private static final int CONNECT_MS = 12_000;
    private static final int READ_MS = 120_000;
    private static final String USER_AGENT = "MintPlugin/1.0 (+https://github.com/)";

    private MintMavenDownload() {
    }

    public static void downloadTo(File target, MintBundledArtifacts.Artifact artifact, Logger log) throws IOException {
        Path dest = target.toPath();
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName().toString() + ".tmp");

        URL url = new URL(artifact.centralUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(CONNECT_MS);
        conn.setReadTimeout(READ_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " for " + url);
        }

        try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
            in.transferTo(out);
        } finally {
            conn.disconnect();
        }

        if (!Files.exists(tmp) || Files.size(tmp) == 0) {
            Files.deleteIfExists(tmp);
            throw new IOException("Downloaded file empty: " + artifact.fileName());
        }

        try {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Downloaded library " + artifact.fileName() + " from Maven Central.");
    }
}
