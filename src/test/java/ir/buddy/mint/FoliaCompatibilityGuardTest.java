package ir.buddy.mint;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliaCompatibilityGuardTest {

    private record GuardRule(Pattern pattern, String message, boolean allowInFoliaSchedulerOnly) {
    }

    private static final List<GuardRule> RULES = List.of(
            new GuardRule(Pattern.compile("\\bBukkit\\.getWorlds\\s*\\("),
                    "Use of Bukkit.getWorlds() is blocked for Folia safety.",
                    false),
            new GuardRule(Pattern.compile("\\bBukkit\\.getEntity\\s*\\("),
                    "Use world-scoped getEntity(UUID) instead of Bukkit.getEntity(UUID).",
                    false),
            new GuardRule(Pattern.compile("\\bBukkit\\.getScheduler\\s*\\("),
                    "Direct Bukkit scheduler access is blocked outside FoliaScheduler.",
                    true),
            new GuardRule(Pattern.compile("\\bBukkitRunnable\\b"),
                    "Use FoliaScheduler instead of BukkitRunnable for scheduled work.",
                    false),
            new GuardRule(Pattern.compile("\\b\\w+\\.getWorld\\s*\\(\\s*\\)\\s*\\.\\s*getEntities\\s*\\(\\s*\\)"),
                    "World-wide entity scans (getWorld().getEntities()) are blocked for Folia safety.",
                    false),
            new GuardRule(Pattern.compile("\\bworld\\s*\\.\\s*getEntities\\s*\\(\\s*\\)"),
                    "World-wide entity scans (world.getEntities()) are blocked for Folia safety.",
                    false),
            new GuardRule(Pattern.compile("\\bgetEntitiesByClass\\s*\\("),
                    "Global class entity scans (getEntitiesByClass()) are blocked for Folia safety.",
                    false)
    );

    @Test
    void blocksKnownFoliaUnsafeGlobalApiPatterns() throws IOException {
        Path mainJava = Path.of("src", "main", "java");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(mainJava)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> scanFile(path, violations));
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    private static void scanFile(Path path, List<String> violations) {
        String normalized = path.toString().replace('\\', '/');
        boolean isFoliaScheduler = normalized.endsWith("/ir/buddy/mint/util/FoliaScheduler.java");
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            violations.add("Failed reading file: " + normalized + " (" + ex.getMessage() + ")");
            return;
        }

        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (GuardRule rule : RULES) {
                if (!rule.pattern().matcher(line).find()) {
                    continue;
                }
                if (rule.allowInFoliaSchedulerOnly() && isFoliaScheduler) {
                    continue;
                }
                violations.add(normalized + ":" + (i + 1) + " - " + rule.message() + " Offending line: " + line.trim());
            }
        }
    }
}

