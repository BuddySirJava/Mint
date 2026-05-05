package ir.buddy.mint.player.storage;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.util.JdbcDriverBinding;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;

public class JdbcPlayerToggleStorage implements PlayerToggleStorage {

    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS player_module_toggles ("
            + "player_uuid VARCHAR(36) NOT NULL,"
            + "module_key VARCHAR(128) NOT NULL,"
            + "enabled BOOLEAN NOT NULL,"
            + "PRIMARY KEY (player_uuid, module_key)"
            + ")";
    private static final String SELECT_SQL = "SELECT enabled FROM player_module_toggles WHERE player_uuid = ? AND module_key = ?";
    private static final String SELECT_PLAYER_SQL = "SELECT module_key, enabled FROM player_module_toggles WHERE player_uuid = ?";
    private static final String UPDATE_SQL = "UPDATE player_module_toggles SET enabled = ? WHERE player_uuid = ? AND module_key = ?";
    private static final String INSERT_SQL = "INSERT INTO player_module_toggles (player_uuid, module_key, enabled) VALUES (?, ?, ?)";

    private final MintPlugin plugin;
    private final String description;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Driver jdbcDriver;
    private Connection connection;
    private PreparedStatement selectStatement;
    private PreparedStatement selectPlayerStatement;
    private PreparedStatement updateStatement;
    private PreparedStatement insertStatement;

    public JdbcPlayerToggleStorage(MintPlugin plugin,
                                   String description,
                                   JdbcDriverBinding driver,
                                   String jdbcUrl,
                                   String username,
                                   String password) {
        this.plugin = plugin;
        this.description = description;
        this.jdbcUrl = ensureH2AutoServer(plugin, description, jdbcUrl);
        this.username = username;
        this.password = password;

        try {
            Class<?> driverClass = Class.forName(driver.className(), true, driver.classLoader());
            this.jdbcDriver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            initialize();
        } catch (SQLException | ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to initialize " + description + " player toggle storage", ex);
        }
    }

    @Override
    public synchronized boolean getToggle(UUID playerUuid, String moduleKey, boolean defaultValue) {
        try {
            ensureConnection();
            selectStatement.setString(1, playerUuid.toString());
            selectStatement.setString(2, moduleKey);
            try (ResultSet resultSet = selectStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("enabled");
                }
            }
        } catch (SQLException ex) {
            logStorageError("read", ex);
        }
        return defaultValue;
    }

    @Override
    public synchronized Map<String, Boolean> getToggles(UUID playerUuid) {
        Map<String, Boolean> toggles = new HashMap<>();
        try {
            ensureConnection();
            selectPlayerStatement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = selectPlayerStatement.executeQuery()) {
                while (resultSet.next()) {
                    toggles.put(resultSet.getString("module_key"), resultSet.getBoolean("enabled"));
                }
            }
        } catch (SQLException ex) {
            logStorageError("read-all", ex);
        }
        return toggles;
    }

    @Override
    public synchronized void setToggle(UUID playerUuid, String moduleKey, boolean enabled) {
        try {
            ensureConnection();
            updateStatement.setBoolean(1, enabled);
            updateStatement.setString(2, playerUuid.toString());
            updateStatement.setString(3, moduleKey);

            if (updateStatement.executeUpdate() > 0) {
                return;
            }

            insertStatement.setString(1, playerUuid.toString());
            insertStatement.setString(2, moduleKey);
            insertStatement.setBoolean(3, enabled);
            insertStatement.executeUpdate();
        } catch (SQLException ex) {
            logStorageError("write", ex);
        }
    }

    @Override
    public synchronized void close() {
        plugin.getLogger().info("Closing JDBC storage for " + description + "...");
        closeQuietly(selectStatement);
        closeQuietly(selectPlayerStatement);
        closeQuietly(updateStatement);
        closeQuietly(insertStatement);
        selectStatement = null;
        selectPlayerStatement = null;
        updateStatement = null;
        insertStatement = null;

        if (connection != null && "h2".equals(description)) {
            try (Statement st = connection.createStatement()) {
                plugin.getLogger().info("Executing H2 SHUTDOWN IMMEDIATELY...");
                st.execute("SHUTDOWN IMMEDIATELY");
                plugin.getLogger().info("H2 SHUTDOWN executed.");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to execute H2 SHUTDOWN", ex);
            }
        }
        closeQuietly(connection);
        connection = null;
        plugin.getLogger().info("JDBC storage closed.");
    }

    @Override
    public String getDescription() {
        return description;
    }

    private void initialize() throws SQLException {
        ensureConnection();
    }

    private void ensureConnection() throws SQLException {
        if (connection != null && !connection.isClosed() && connection.isValid(2)) {
            return;
        }

        close();

        Exception lastException = null;
        int maxRetries = 25;
        long retryDelayMs = 250;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                connectToDatabase();
                return;
            } catch (Exception ex) {
                lastException = ex;
                String message = ex.getMessage();
                Throwable cause = ex.getCause();

                boolean isLockIssue = (message != null && (message.contains("file is locked") || 
                                       message.contains("Database may be already in use") ||
                                       message.contains("OverlappingFileLockException"))) ||
                                      ex instanceof java.nio.channels.OverlappingFileLockException ||
                                      (cause != null && cause instanceof java.nio.channels.OverlappingFileLockException);
                                       
                if (isLockIssue) {
                    if (attempt < maxRetries) {
                        plugin.getLogger().warning("Database file locked (attempt " + attempt + "/" + maxRetries + "), retrying in " + retryDelayMs + "ms...");
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            if (ex instanceof SQLException) throw (SQLException) ex;
                            throw new SQLException("Interrupted while waiting for database lock", ex);
                        }
                        continue;
                    }
                }
                if (ex instanceof SQLException) throw (SQLException) ex;
                throw new SQLException("Failed to connect to database", ex);
            }
        }
        
        if (lastException instanceof SQLException) throw (SQLException) lastException;
        throw new SQLException("Failed to connect to database after retries", lastException);
    }
    
    private void connectToDatabase() throws SQLException {
        if (connection != null && !connection.isClosed() && connection.isValid(2)) {
            return;
        }

        close();
        Properties info = new Properties();
        if (username != null && !username.isEmpty()) {
            info.setProperty("user", username);
        }
        if (password != null && !password.isEmpty()) {
            info.setProperty("password", password);
        }
        connection = jdbcDriver.connect(jdbcUrl, info);
        if (connection == null) {
            throw new SQLException("No JDBC driver accepts URL: " + jdbcUrl);
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE_SQL);
        }

        selectStatement = connection.prepareStatement(SELECT_SQL);
        selectPlayerStatement = connection.prepareStatement(SELECT_PLAYER_SQL);
        updateStatement = connection.prepareStatement(UPDATE_SQL);
        insertStatement = connection.prepareStatement(INSERT_SQL);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static String ensureH2AutoServer(MintPlugin plugin, String description, String url) {
        if (url == null || !"h2".equals(description)) {
            return url;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("jdbc:h2:")) {
            return url;
        }
        if (lower.startsWith("jdbc:h2:mem:")
                || lower.startsWith("jdbc:h2:tcp:")
                || lower.startsWith("jdbc:h2:ssl:")
                || lower.startsWith("jdbc:h2:zip:")) {
            return url;
        }
        String result = url;
        if (lower.contains("file_lock=")) {
            result = stripUrlParameter(result, "FILE_LOCK");
            plugin.getLogger().info("Removed deprecated FILE_LOCK parameter from H2 URL "
                    + "(MVStore ignores it; AUTO_SERVER=TRUE is used instead).");
        }
        if (!result.toLowerCase(Locale.ROOT).contains("auto_server=")) {
            String separator = result.endsWith(";") ? "" : ";";
            result = result + separator + "AUTO_SERVER=TRUE";
            plugin.getLogger().info("Appended AUTO_SERVER=TRUE to H2 URL to allow plugin reloads "
                    + "without OverlappingFileLockException.");
        }
        return result;
    }

    private static String stripUrlParameter(String url, String parameter) {
        StringBuilder out = new StringBuilder(url.length());
        boolean first = true;
        for (String part : url.split(";", -1)) {
            String trimmed = part.trim();
            if (!first && trimmed.toLowerCase(Locale.ROOT).startsWith(parameter.toLowerCase(Locale.ROOT) + "=")) {
                continue;
            }
            if (!first) {
                out.append(';');
            }
            out.append(part);
            first = false;
        }
        return out.toString();
    }

    private void logStorageError(String action, SQLException ex) {
        plugin.getLogger().log(
                Level.WARNING,
                "Failed to " + action + " player toggle using " + description + " storage; falling back to defaults for this request.",
                ex
        );
    }
}
