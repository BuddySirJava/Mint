package ir.buddy.mint.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maven Central coordinates for jars shipped in {@code target/mint-lib/} (offline bundle) and
 * optionally downloaded into {@code plugins/Mint/lib/}. Versions must match {@code pom.xml}.
 * bStats is not listed here: it is shaded into the Mint plugin jar with a relocated package.
 */
public final class MintBundledArtifacts {

    public static final String MAVEN_CENTRAL_PREFIX = "https://repo1.maven.org/maven2/";

    private MintBundledArtifacts() {
    }

    public record Artifact(String groupId, String artifactId, String version) {

        public String fileName() {
            return artifactId + "-" + version + ".jar";
        }

        public String mavenPath() {
            return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + fileName();
        }

        public String centralUrl() {
            return MAVEN_CENTRAL_PREFIX + mavenPath();
        }
    }

    private static final Artifact MONGO_SYNC = new Artifact("org.mongodb", "mongodb-driver-sync", "5.5.0");
    private static final Artifact BSON = new Artifact("org.mongodb", "bson", "5.5.0");
    private static final Artifact MONGO_CORE = new Artifact("org.mongodb", "mongodb-driver-core", "5.5.0");
    private static final Artifact BSON_RECORD_CODEC = new Artifact("org.mongodb", "bson-record-codec", "5.5.0");

    private static final Artifact H2 = new Artifact("com.h2database", "h2", "2.3.232");
    private static final Artifact MARIADB = new Artifact("org.mariadb.jdbc", "mariadb-java-client", "3.5.3");
    private static final Artifact MYSQL = new Artifact("com.mysql", "mysql-connector-j", "9.3.0");
    private static final Artifact PROTOBUF = new Artifact("com.google.protobuf", "protobuf-java", "4.29.0");

    public static List<Artifact> forStorageType(String normalizedStorageType) {
        List<Artifact> out = new ArrayList<>();
        switch (normalizedStorageType) {
            case "mongo" -> {
                out.add(BSON);
                out.add(BSON_RECORD_CODEC);
                out.add(MONGO_CORE);
                out.add(MONGO_SYNC);
            }
            case "h2" -> out.add(H2);
            case "mariadb" -> out.add(MARIADB);
            case "mysql" -> {
                out.add(MYSQL);
                out.add(PROTOBUF);
            }
            default -> {
            }
        }
        return out;
    }

    public static String describeMissing(String normalizedType, List<Artifact> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("Missing libraries in plugins/Mint/lib/ for storage type \"")
                .append(normalizedType.toLowerCase(Locale.ROOT))
                .append("\": ");
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(missing.get(i).fileName());
        }
        sb.append(". Copy jars from the mint-lib folder next to your build (see target/mint-lib/) or enable outbound HTTPS to Maven Central.");
        return sb.toString();
    }
}
