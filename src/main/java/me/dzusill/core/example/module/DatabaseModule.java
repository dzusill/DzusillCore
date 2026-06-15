package me.dzusill.core.example.module;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.database.DatabaseConfig;
import me.dzusill.core.database.DatabaseManager;
import me.dzusill.core.module.AbstractModule;
import me.dzusill.core.scheduler.SchedulerService;

/**
 * Starts the database subsystem and publishes the {@link DatabaseManager}. The manager honors the
 * {@code enabled} toggle in {@code database.yml}, so this module is safe to include even when no
 * database is configured. Enabled after the foundation module so the async executor is available.
 */
public final class DatabaseModule extends AbstractModule {

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
        SchedulerService scheduler = service(SchedulerService.class);
        this.databaseManager = new DatabaseManager(plugin, new DatabaseConfig(plugin), scheduler.asyncExecutor());
        databaseManager.start();
        provide(DatabaseManager.class, databaseManager);
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }
}
