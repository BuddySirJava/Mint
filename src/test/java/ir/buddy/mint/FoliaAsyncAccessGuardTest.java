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

class FoliaAsyncAccessGuardTest {

    private static final Pattern ASYNC_CALL_PATTERN = Pattern.compile(
            "\\b(FoliaScheduler\\.runAsync|Bukkit\\.getScheduler\\s*\\(\\s*\\)\\s*\\.\\s*runTaskAsynchronously)\\b"
    );

    private static final List<Pattern> ASYNC_UNSAFE_CALL_PATTERNS = List.of(
            Pattern.compile("\\bBukkit\\.getOnlinePlayers\\s*\\("),
            Pattern.compile("\\bBukkit\\.getWorlds\\s*\\("),
            Pattern.compile("\\bBukkit\\.getEntity\\s*\\("),
            Pattern.compile("\\b\\w+\\.getWorld\\s*\\(\\s*\\)\\s*\\.\\s*spawn\\s*\\("),
            Pattern.compile("\\b\\w+\\.getWorld\\s*\\(\\s*\\)\\s*\\.\\s*dropItemNaturally\\s*\\("),
            Pattern.compile("\\b\\w+\\.getWorld\\s*\\(\\s*\\)\\s*\\.\\s*playSound\\s*\\("),
            Pattern.compile("\\b\\w+\\.getWorld\\s*\\(\\s*\\)\\s*\\.\\s*getEntities\\s*\\("),
            Pattern.compile("\\b\\w+\\.getWorld\\s*\\(\\s*\\)\\s*\\.\\s*getEntitiesByClass\\s*\\("),
            Pattern.compile("\\b\\w+\\.teleport\\s*\\("),
            Pattern.compile("\\b\\w+\\.setType\\s*\\(")
    );

    @Test
    void blocksObviousWorldOrEntityAccessInsideAsyncLambdas() throws IOException {
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
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            violations.add("Failed reading file: " + normalized + " (" + ex.getMessage() + ")");
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!ASYNC_CALL_PATTERN.matcher(line).find()) {
                continue;
            }
            int lambdaIndex = line.indexOf("->");
            if (lambdaIndex < 0) {
                continue;
            }
            int blockStartLine = line.substring(lambdaIndex).contains("{") ? i : -1;
            if (blockStartLine < 0) {
                continue;
            }

            int braceDepth = 0;
            boolean entered = false;
            for (int j = blockStartLine; j < lines.size(); j++) {
                String current = lines.get(j);
                for (char c : current.toCharArray()) {
                    if (c == '{') {
                        braceDepth++;
                        entered = true;
                    } else if (c == '}') {
                        braceDepth--;
                    }
                }
                if (!entered) {
                    continue;
                }

                for (Pattern pattern : ASYNC_UNSAFE_CALL_PATTERNS) {
                    if (pattern.matcher(current).find()) {
                        violations.add(normalized + ":" + (j + 1)
                                + " - Potential Folia-unsafe world/entity access inside async block: "
                                + current.trim());
                    }
                }
                if (entered && braceDepth <= 0) {
                    break;
                }
            }
        }
    }
}

