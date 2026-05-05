package ir.buddy.mint.util;

/**
 * JDBC {@link Class#forName(String, boolean, ClassLoader)} target for a storage backend.
 */
public record JdbcDriverBinding(String className, ClassLoader classLoader) {
}
