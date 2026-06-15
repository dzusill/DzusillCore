package me.dzusill.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.dzusill.core.database.repository.SqlDataStore;
import me.dzusill.core.example.database.PlayerRecord;
import me.dzusill.core.example.database.PlayerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the full database stack (Database -> repository / SqlDataStore) against an in-memory
 * H2 instance in MySQL-compatibility mode, so CRUD and dialect upsert run offline without a real
 * server. The async executor is synchronous ({@code Runnable::run}) so futures complete inline.
 */
class DatabaseIntegrationTest {

    private HikariDataSource dataSource;
    private Database database;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:dz_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource = new HikariDataSource(config);
        database = new AbstractSqlDatabase(DatabaseType.MYSQL, dataSource, Runnable::run) {
        };
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void repositorySupportsCrudAndUpsert() {
        database.update("CREATE TABLE core_players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16), "
                + "coins BIGINT, last_seen BIGINT)").join();
        PlayerRepository repository = new PlayerRepository(database);
        UUID id = UUID.randomUUID();

        repository.save(new PlayerRecord(id, "Steve", 100, 1L)).join();
        assertTrue(repository.exists(id).join());
        assertEquals(100, repository.find(id).join().orElseThrow().coins());

        repository.save(new PlayerRecord(id, "Steve", 250, 2L)).join();
        Optional<PlayerRecord> updated = repository.find(id).join();
        assertEquals(250, updated.orElseThrow().coins());
        assertEquals(1, repository.findAll().join().size());

        repository.delete(id).join();
        assertFalse(repository.exists(id).join());
    }

    @Test
    void sqlDataStorePersistsAndReloads() {
        database.update("CREATE TABLE kv (k VARCHAR(64) PRIMARY KEY, v VARCHAR(255))").join();

        SqlDataStore<String> store = new SqlDataStore<>(
                database, "kv", "k", "v", value -> value, Object::toString);
        store.put("alpha", "1");
        store.put("beta", "2");
        store.save();

        SqlDataStore<String> reloaded = new SqlDataStore<>(
                database, "kv", "k", "v", value -> value, Object::toString);
        reloaded.load();

        assertEquals("1", reloaded.get("alpha").orElseThrow());
        assertEquals("2", reloaded.get("beta").orElseThrow());
    }
}
