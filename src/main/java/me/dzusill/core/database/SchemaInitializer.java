package me.dzusill.core.database;

import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs a bundled, per-dialect schema file ({@code schema-<type>.sql}) at startup so required
 * tables exist before the plugin uses them. Statements are executed synchronously during enable
 * (by joining the async futures), which is acceptable one-time startup cost.
 */
public final class SchemaInitializer {

    private SchemaInitializer() {
    }

    /**
     * Loads and executes {@code schema-<type>.sql} for the database's dialect, if present.
     *
     * @throws DatabaseException if a statement fails to execute
     */
    public static void initialize(Plugin plugin, Database database) {
        String resource = "schema-" + database.type().name().toLowerCase() + ".sql";
        InputStream stream = plugin.getResource(resource);
        if (stream == null) {
            plugin.getLogger().info("No bundled schema (" + resource + "); skipping schema setup.");
            return;
        }

        for (String statement : readStatements(stream)) {
            try {
                database.update(statement).join();
            } catch (Exception ex) {
                throw new DatabaseException("Failed to apply schema statement: " + statement, ex);
            }
        }
        plugin.getLogger().info("Applied database schema from " + resource + ".");
    }

    private static List<String> readStatements(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String content = reader.lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            return Arrays.stream(content.split(";"))
                    .map(String::trim)
                    .filter(statement -> !statement.isEmpty())
                    .toList();
        } catch (Exception ex) {
            throw new DatabaseException("Failed to read schema resource", ex);
        }
    }
}
