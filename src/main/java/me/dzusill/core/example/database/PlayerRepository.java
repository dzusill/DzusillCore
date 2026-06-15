package me.dzusill.core.example.database;

import me.dzusill.core.database.Database;
import me.dzusill.core.database.query.RowMapper;
import me.dzusill.core.database.repository.AbstractSqlRepository;

import java.util.List;
import java.util.UUID;

/**
 * Example repository showing how little is needed for full async CRUD on top of
 * {@link AbstractSqlRepository}: declare the table, its columns and key, and how rows map to and
 * from the entity. The upsert used by {@code save} is generated per dialect, so this works on both
 * MySQL and PostgreSQL unchanged.
 *
 * <pre>{@code
 * repo.find(player.getUniqueId())
 *     .thenAcceptAsync(opt -> opt.ifPresent(rec -> ...), scheduler.mainThreadExecutor());
 * }</pre>
 */
public final class PlayerRepository extends AbstractSqlRepository<UUID, PlayerRecord> {

    public PlayerRepository(Database database) {
        super(database);
    }

    @Override
    protected String table() {
        return "core_players";
    }

    @Override
    protected List<String> columns() {
        return List.of("uuid", "name", "coins", "last_seen");
    }

    @Override
    protected List<String> keyColumns() {
        return List.of("uuid");
    }

    @Override
    protected RowMapper<PlayerRecord> mapper() {
        return resultSet -> new PlayerRecord(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("name"),
                resultSet.getLong("coins"),
                resultSet.getLong("last_seen"));
    }

    @Override
    protected Object[] values(PlayerRecord entity) {
        return new Object[]{entity.uuid().toString(), entity.name(), entity.coins(), entity.lastSeen()};
    }

    @Override
    protected Object[] keyValues(UUID id) {
        return new Object[]{id.toString()};
    }
}
