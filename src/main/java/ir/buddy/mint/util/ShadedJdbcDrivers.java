package ir.buddy.mint.util;

/**
 * Resolves JDBC drivers for (1) jars in {@code plugins/Mint/lib/}, (2) relocated names inside the
 * plugin jar (legacy fat-jar builds), or (3) vanilla coordinates on the plugin/test classpath.
 */
public final class ShadedJdbcDrivers {

    private static final String LIB = "ir.buddy.mint.lib";

    private ShadedJdbcDrivers() {
    }

    public static JdbcDriverBinding mariadb(ClassLoader pluginClassLoader, ClassLoader libClassLoader) {
        return resolve(pluginClassLoader, libClassLoader, LIB + ".mariadb.jdbc.Driver", "org.mariadb.jdbc.Driver");
    }

    public static JdbcDriverBinding mysql(ClassLoader pluginClassLoader, ClassLoader libClassLoader) {
        return resolve(pluginClassLoader, libClassLoader, LIB + ".mysql.cj.jdbc.Driver", "com.mysql.cj.jdbc.Driver");
    }

    public static JdbcDriverBinding h2(ClassLoader pluginClassLoader, ClassLoader libClassLoader) {
        return resolve(pluginClassLoader, libClassLoader, LIB + ".h2.Driver", "org.h2.Driver");
    }

    private static JdbcDriverBinding resolve(ClassLoader pluginCl,
                                             ClassLoader libCl,
                                             String shadedFqcn,
                                             String vanillaFqcn) {
        ClassLoader plugin = pluginCl != null ? pluginCl : ShadedJdbcDrivers.class.getClassLoader();
        if (libCl != null) {
            try {
                Class.forName(vanillaFqcn, false, libCl);
                return new JdbcDriverBinding(vanillaFqcn, libCl);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Driver jar not in lib; try embedded / classpath.
            }
        }
        try {
            Class.forName(shadedFqcn, false, plugin);
            return new JdbcDriverBinding(shadedFqcn, plugin);
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Not a shaded fat jar.
        }
        try {
            Class.forName(vanillaFqcn, false, plugin);
            return new JdbcDriverBinding(vanillaFqcn, plugin);
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Prefer vanilla name in binding so Class.forName errors match Maven coordinates.
        }
        return new JdbcDriverBinding(vanillaFqcn, libCl != null ? libCl : plugin);
    }
}
