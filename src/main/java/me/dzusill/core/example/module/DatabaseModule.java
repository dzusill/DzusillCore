package me.dzusill.core.example.module;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.database.DatabaseConfig;
import me.dzusill.core.database.DatabaseManager;
import me.dzusill.core.module.AbstractModule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Starts the database subsystem and publishes the {@link DatabaseManager}. The manager honors the
 * {@code enabled} toggle in {@code database.yml}, so this module is safe to include even when no
 * database is configured. Enabled after the foundation module so the async executor is available.
 */
public final class DatabaseModule extends AbstractModule {

    private ExecutorService dbExecutor;
    private DatabaseManager databaseManager;

    public DatabaseModule(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "Database";
    }

    @Override
    public void onEnable() {
        AtomicInteger count = new AtomicInteger();
        // Must use a plain Java thread pool, not SchedulerService.asyncExecutor() (Bukkit
        // scheduler). SchemaInitializer calls CompletableFuture.join() on the main thread;
        // Bukkit's scheduler only dispatches after the tick loop starts, which is after all
        // onEnable() calls complete — deadlock if used here.
        this.dbExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, plugin.getName() + "-db-" + count.getAndIncrement());
            t.setDaemon(true);
            return t;
        });

        this.databaseManager = new DatabaseManager(plugin, new DatabaseConfig(plugin), dbExecutor);
        databaseManager.start();
        provide(DatabaseManager.class, databaseManager);
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
    }
}
