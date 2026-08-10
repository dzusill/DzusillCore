package me.dzusill.core.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.dzusill.core.database.PlayerRecord;
import me.dzusill.core.database.PlayerRepository;

/**
 * Drives the embedded backend end to end - real driver, real file, H2's own MERGE upsert - rather than the
 * MySQL-dialect-over-in-memory-H2 shortcut {@link DatabaseIntegrationTest} takes. The async executor is synchronous
 * ({@code Runnable::run}) so futures complete inline.
 */
class H2DatabaseTest {

    private Database database;

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    private Database open(Path directory, String file) {
        DatabaseCredentials credentials = new DatabaseCredentials("unused", 0, "unused", "sa", "", 2, 5000L, Map.of(),
                directory.resolve(file).toString());
        return new H2Database(credentials, Runnable::run);
    }

    @Test
    void createsTheDatabaseFileAndItsParentDirectory(@TempDir Path directory) {
        database = open(directory, "nested/data");
        database.update("CREATE TABLE t (id INT PRIMARY KEY)").join();

        assertTrue(new File(directory.toFile(), "nested/data.mv.db").isFile(),
                "expected H2 to create data.mv.db inside the auto-created nested directory");
    }

    @Test
    void upsertInsertsThenUpdatesTheSameRow(@TempDir Path directory) {
        database = open(directory, "data");
        database.update("CREATE TABLE core_players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16), "
                + "coins BIGINT, last_seen BIGINT)").join();
        PlayerRepository repository = new PlayerRepository(database);
        UUID id = UUID.randomUUID();

        repository.save(new PlayerRecord(id, "Steve", 100, 1L)).join();
        repository.save(new PlayerRecord(id, "Steve", 250, 2L)).join();

        assertEquals(250, repository.find(id).join().orElseThrow().coins());
        assertEquals(1, repository.findAll().join().size());
    }

    @Test
    void dataSurvivesReopeningTheFile(@TempDir Path directory) {
        database = open(directory, "data");
        database.update("CREATE TABLE t (id INT PRIMARY KEY, v VARCHAR(16))").join();
        database.update("INSERT INTO t (id, v) VALUES (?, ?)", 1, "kept").join();
        database.close();

        database = open(directory, "data");

        assertEquals("kept",
                database.queryOne("SELECT v FROM t WHERE id = ?", row -> row.getString("v"), 1).join().orElseThrow());
    }
}
