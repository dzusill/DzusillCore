package me.dzusill.core.database;

import java.util.concurrent.Executor;

/**
 * MySQL-backed {@link Database}. All behaviour is inherited from {@link AbstractSqlDatabase}; this subclass only fixes
 * the {@link DatabaseType}.
 */
public final class MySqlDatabase extends AbstractSqlDatabase {

    public MySqlDatabase(DatabaseCredentials credentials, Executor asyncExecutor) {
        super(DatabaseType.MYSQL, credentials, asyncExecutor);
    }
}
