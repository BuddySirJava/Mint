package ir.buddy.mint.player.storage;

import ir.buddy.mint.MintPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;




public final class ReflectiveMongoPlayerToggleStorage implements PlayerToggleStorage {

    private final MintPlugin plugin;
    private final ClassLoader cl;
    private final Object mongoClient;
    private final Object collection;
    private final ExecutorService writeExecutor;

    private final Class<?> bsonClass;
    private final Class<?> documentClass;
    private final Method filtersEq;
    private final Method filtersAnd;
    private final Method collectionFind;
    private final Method findIterableFirst;
    private final Method collectionUpdateOne;
    private final Method documentGet;
    private final Method documentGetString;
    private final Method clientClose;
    private final Class<?> updateOptionsClass;

    public ReflectiveMongoPlayerToggleStorage(MintPlugin plugin,
                                              ClassLoader libClassLoader,
                                              String connectionString,
                                              String database,
                                              String collectionName) {
        this.plugin = plugin;
        this.cl = libClassLoader;
        this.writeExecutor = Executors.newSingleThreadExecutor();

        try {
            this.bsonClass = Class.forName("org.bson.conversions.Bson", false, cl);
            this.documentClass = Class.forName("org.bson.Document", false, cl);

            Class<?> connectionStringClass = Class.forName("com.mongodb.ConnectionString", false, cl);
            Object cs = connectionStringClass.getConstructor(String.class).newInstance(connectionString);

            Class<?> mongoClientsClass = Class.forName("com.mongodb.client.MongoClients", false, cl);
            Method create = mongoClientsClass.getMethod("create", connectionStringClass);
            this.mongoClient = create.invoke(null, cs);

            Method getDatabase = mongoClient.getClass().getMethod("getDatabase", String.class);
            Object db = getDatabase.invoke(mongoClient, database);
            Method getCollection = db.getClass().getMethod("getCollection", String.class);
            this.collection = getCollection.invoke(db, collectionName);

            Class<?> indexesClass = Class.forName("com.mongodb.client.model.Indexes", false, cl);
            Method ascending = indexesClass.getMethod("ascending", String[].class);
            Object ascUuid = ascending.invoke(null, (Object) new String[]{"player_uuid"});
            Object ascKey = ascending.invoke(null, (Object) new String[]{"module_key"});
            Class<?> bsonArrayType = Array.newInstance(bsonClass, 0).getClass();
            Method compoundIndex = indexesClass.getMethod("compoundIndex", bsonArrayType);
            Object compound = compoundIndex.invoke(null, newArray(bsonClass, ascUuid, ascKey));

            Class<?> indexOptionsClass = Class.forName("com.mongodb.client.model.IndexOptions", false, cl);
            Object indexOpts = indexOptionsClass.getConstructor().newInstance();
            indexOptionsClass.getMethod("unique", boolean.class).invoke(indexOpts, true);

            Method createIndex = collection.getClass().getMethod("createIndex", bsonClass, indexOptionsClass);
            createIndex.invoke(collection, compound, indexOpts);

            Class<?> filtersClass = Class.forName("com.mongodb.client.model.Filters", false, cl);
            this.filtersEq = filtersClass.getMethod("eq", String.class, Object.class);
            this.filtersAnd = filtersClass.getMethod("and", bsonArrayType);

            this.collectionFind = collection.getClass().getMethod("find", bsonClass);
            Class<?> findIterableClass = Class.forName("com.mongodb.client.FindIterable", false, cl);
            this.findIterableFirst = findIterableClass.getMethod("first");

            this.updateOptionsClass = Class.forName("com.mongodb.client.model.UpdateOptions", false, cl);
            this.collectionUpdateOne = collection.getClass().getMethod(
                    "updateOne",
                    bsonClass,
                    bsonClass,
                    this.updateOptionsClass
            );

            this.documentGet = documentClass.getMethod("get", Object.class);
            this.documentGetString = documentClass.getMethod("getString", Object.class);
            this.clientClose = mongoClient.getClass().getMethod("close");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to initialize mongo player toggle storage", ex);
        }
    }

    private static Object newArray(Class<?> component, Object... elements) {
        Object arr = Array.newInstance(component, elements.length);
        for (int i = 0; i < elements.length; i++) {
            Array.set(arr, i, elements[i]);
        }
        return arr;
    }

    @Override
    public boolean getToggle(UUID playerUuid, String moduleKey, boolean defaultValue) {
        try {
            Object f1 = filtersEq.invoke(null, "player_uuid", playerUuid.toString());
            Object f2 = filtersEq.invoke(null, "module_key", moduleKey);
            Object filter = filtersAnd.invoke(null, newArray(bsonClass, f1, f2));

            Object findIterable = collectionFind.invoke(collection, filter);
            Object document = findIterableFirst.invoke(findIterable);
            if (document == null) {
                return defaultValue;
            }
            Object enabled = documentGet.invoke(document, "enabled");
            if (enabled instanceof Boolean) {
                return (Boolean) enabled;
            }
        } catch (Throwable ex) {
            logStorageError("read", ex);
        }
        return defaultValue;
    }

    @Override
    public Map<String, Boolean> getToggles(UUID playerUuid) {
        Map<String, Boolean> toggles = new HashMap<>();
        try {
            Object filter = filtersEq.invoke(null, "player_uuid", playerUuid.toString());
            Object findIterable = collectionFind.invoke(collection, filter);
            Method iterator = findIterable.getClass().getMethod("iterator");
            Object it = iterator.invoke(findIterable);
            Method hasNext = it.getClass().getMethod("hasNext");
            Method next = it.getClass().getMethod("next");
            while (Boolean.TRUE.equals(hasNext.invoke(it))) {
                Object document = next.invoke(it);
                String mk = (String) documentGetString.invoke(document, "module_key");
                Object enabled = documentGet.invoke(document, "enabled");
                if (mk != null && enabled instanceof Boolean) {
                    toggles.put(mk, (Boolean) enabled);
                }
            }
        } catch (Throwable ex) {
            logStorageError("read-all", ex);
        }
        return toggles;
    }

    @Override
    public void setToggle(UUID playerUuid, String moduleKey, boolean enabled) {
        writeExecutor.execute(() -> {
            try {
                Object f1 = filtersEq.invoke(null, "player_uuid", playerUuid.toString());
                Object f2 = filtersEq.invoke(null, "module_key", moduleKey);
                Object filter = filtersAnd.invoke(null, newArray(bsonClass, f1, f2));

                Constructor<?> docCtor = documentClass.getConstructor(String.class, Object.class);
                Object inner = docCtor.newInstance("enabled", enabled);
                Object update = docCtor.newInstance("$set", inner);

                Object updateOpts = updateOptionsClass.getConstructor().newInstance();
                updateOptionsClass.getMethod("upsert", boolean.class).invoke(updateOpts, true);

                collectionUpdateOne.invoke(collection, filter, update, updateOpts);
            } catch (Throwable ex) {
                logStorageError("write", ex);
            }
        });
    }

    @Override
    public void close() {
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writeExecutor.shutdownNow();
        } finally {
            try {
                clientClose.invoke(mongoClient);
            } catch (ReflectiveOperationException ignored) {
                
            }
        }
    }

    @Override
    public String getDescription() {
        return "mongo";
    }

    private void logStorageError(String action, Throwable ex) {
        plugin.getLogger().log(
                Level.WARNING,
                "Failed to " + action + " player toggle using mongo storage; falling back to defaults for this request.",
                ex
        );
    }
}
