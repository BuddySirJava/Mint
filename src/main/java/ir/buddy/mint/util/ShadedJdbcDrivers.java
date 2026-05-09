package ir.buddy.mint.util;





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
                
            }
        }
        try {
            Class.forName(shadedFqcn, false, plugin);
            return new JdbcDriverBinding(shadedFqcn, plugin);
        } catch (ClassNotFoundException | LinkageError ignored) {
            
        }
        try {
            Class.forName(vanillaFqcn, false, plugin);
            return new JdbcDriverBinding(vanillaFqcn, plugin);
        } catch (ClassNotFoundException | LinkageError ignored) {
            
        }
        return new JdbcDriverBinding(vanillaFqcn, libCl != null ? libCl : plugin);
    }
}
