package me.dzusill.core.example.database;

import java.util.UUID;

/**
 * Example entity persisted by {@link PlayerRepository}, matching the {@code core_players} table in
 * the bundled schema. Real plugins replace this with their own domain records.
 */
public record PlayerRecord(UUID uuid, String name, long coins, long lastSeen) {
}
