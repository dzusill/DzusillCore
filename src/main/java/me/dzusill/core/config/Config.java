package me.dzusill.core.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * A {@link YamlConfiguration} that preserves comments and auto-syncs missing keys from the bundled resource. New keys
 * are added, existing values are kept, and comments stay attached.
 */
public class Config extends YamlConfiguration {

    private final Map<String, String> comments = new LinkedHashMap<>();
    private boolean failed = false;
    private String resourcePath;
    private String serverPath;
    private String[] ignoredSections;
    private File file;
    private Plugin plugin;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static Config loadConfig(Plugin plugin, String resourcePath, String serverPath, String... ignoredSections) {
        File f = new File(plugin.getDataFolder(), serverPath);
        if (!f.exists()) {
            try {
                f.getParentFile().mkdirs();
                InputStream bundled = plugin.getResource(resourcePath);
                if (bundled != null)
                    plugin.saveResource(resourcePath, false);
                else
                    f.createNewFile();
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not create " + serverPath + ": " + ex.getMessage());
            }
        }
        Config cfg = loadConfiguration(f);
        cfg.resourcePath = resourcePath;
        cfg.serverPath = serverPath;
        cfg.ignoredSections = ignoredSections;
        cfg.file = f;
        cfg.plugin = plugin;
        cfg.syncWithConfig(f, plugin.getResource(resourcePath), ignoredSections);
        return cfg;
    }

    // -------------------------------------------------------------------------
    // Instance API
    // -------------------------------------------------------------------------

    public void save() {
        save(file);
    }

    public Config reload() {
        return loadConfig(plugin, resourcePath, serverPath, ignoredSections);
    }

    public Collection<String> getKeys(String path) {
        ConfigurationSection sec = getConfigurationSection(path);
        return sec == null ? Collections.emptyList() : sec.getKeys(false);
    }

    public boolean exists(String path) {
        return isConfigurationSection(path);
    }

    @Override
    public String getName() {
        return file == null ? "unknown" : file.getName();
    }

    public void syncWithConfig(File file, InputStream resource, String... ignoredSections) {
        if (failed || resource == null)
            return;
        Config defaults = loadConfiguration(resource, file.getName());
        if (mergeFrom(defaults, defaults.getConfigurationSection(""), Arrays.asList(ignoredSections))) {
            save(file);
        }
    }

    public void setComment(String path, String comment) {
        if (comment == null)
            comments.remove(path);
        else
            comments.put(path, comment);
    }

    public String getComment(String path) {
        return comments.get(path);
    }

    public String getComment(String path, String def) {
        return comments.getOrDefault(path, def);
    }

    public boolean containsComment(String path) {
        return comments.containsKey(path);
    }

    public boolean hasFailed() {
        return failed;
    }

    public void loadFromString(String contents, String name) throws InvalidConfigurationException {
        super.loadFromString(contents);
        parseComments(contents);
    }

    @Override
    public void save(File file) {
        try {
            super.save(file);
        } catch (IOException ex) {
            Bukkit.getLogger().warning("Could not save " + getName() + ": " + ex.getMessage());
        }
    }

    @Override
    public String saveToString() {
        // FileConfigurationOptions#setHeader(List<String>) was only added in newer Bukkit;
        // header(String) is the signature available back on Spigot API 1.16.5 too.
        options().header("");
        List<String> lines = new ArrayList<>(Arrays.asList(super.saveToString().split("\n", -1)));
        injectComments(lines);
        StringBuilder sb = new StringBuilder();
        for (String line : lines)
            sb.append('\n').append(line);
        return sb.length() == 0 ? "" : sb.substring(1);
    }

    // -------------------------------------------------------------------------
    // Static loaders
    // -------------------------------------------------------------------------

    public static Config loadConfiguration(File file) {
        try {
            return loadConfiguration(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),
                    file.getName());
        } catch (FileNotFoundException ex) {
            Bukkit.getLogger().warning("Config file not found: " + file.getName());
            return newFailed();
        }
    }

    public static Config loadConfiguration(InputStream stream, String name) {
        return loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8), name);
    }

    public static Config loadConfiguration(Reader reader, String name) {
        Config cfg = new Config();
        try (BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
                sb.append(line).append('\n');
            cfg.loadFromString(sb.toString(), name);
        } catch (IOException | InvalidConfigurationException ex) {
            Bukkit.getLogger().warning("Failed to load config " + name + ": " + ex.getMessage());
            cfg.failed = true;
        }
        return cfg;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static Config newFailed() {
        Config c = new Config();
        c.failed = true;
        return c;
    }

    /**
     * Walks the raw YAML text and maps pending comment blocks to the key path that follows them. Uses an indent-level
     * stack to reconstruct dot-separated paths without re-parsing the tree.
     */
    private void parseComments(String contents) {
        String[] lines = contents.split("\n", -1);
        StringBuilder pending = new StringBuilder();
        Deque<int[]> indentStack = new ArrayDeque<>();
        Deque<String> keyStack = new ArrayDeque<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                pending.append(line).append('\n');
                continue;
            }
            if (trimmed.startsWith("-") || !trimmed.contains(":")) {
                pending.setLength(0);
                continue;
            }
            int indent = leadingSpaces(line);
            String key = trimmed.split(":", 2)[0].replaceAll("^['\"]|['\"]$", "").trim();

            while (!indentStack.isEmpty() && indentStack.peek()[0] >= indent) {
                indentStack.pop();
                keyStack.pop();
            }
            indentStack.push(new int[]{indent});
            keyStack.push(key);

            if (pending.length() > 0) {
                setComment(buildPath(keyStack), pending.substring(0, pending.length() - 1));
                pending.setLength(0);
            }
        }
    }

    /**
     * Inserts stored comment lines into the serialized output immediately before the key they belong to, using the same
     * indent-stack path resolution as {@link #parseComments}.
     */
    private void injectComments(List<String> lines) {
        Deque<int[]> indentStack = new ArrayDeque<>();
        Deque<String> keyStack = new ArrayDeque<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (!trimmed.startsWith("-") && trimmed.contains(":")) {
                int indent = leadingSpaces(line);
                String key = trimmed.split(":", 2)[0].replaceAll("^['\"]|['\"]$", "").trim();

                while (!indentStack.isEmpty() && indentStack.peek()[0] >= indent) {
                    indentStack.pop();
                    keyStack.pop();
                }
                indentStack.push(new int[]{indent});
                keyStack.push(key);

                String comment = getComment(buildPath(keyStack));
                if (comment != null) {
                    String[] commentLines = comment.split("\n", -1);
                    for (int j = commentLines.length - 1; j >= 0; j--) {
                        lines.add(i, commentLines[j]);
                    }
                    i += commentLines.length;
                }
            }
            i++;
        }
    }

    private boolean mergeFrom(Config source, ConfigurationSection section, List<String> ignored) {
        if (section == null)
            return false;
        boolean changed = false;
        for (String key : section.getKeys(false)) {
            String path = section.getCurrentPath().isEmpty() ? key : section.getCurrentPath() + "." + key;
            if (section.isConfigurationSection(key)) {
                boolean isIgnored = ignored.stream().anyMatch(path::contains);
                if (!isIgnored || !contains(path)) {
                    changed = mergeFrom(source, section.getConfigurationSection(key), ignored) || changed;
                }
            } else if (!contains(path)) {
                set(path, section.get(key));
                changed = true;
            }
            String srcComment = source.getComment(path);
            if (srcComment != null && !srcComment.equals(getComment(path))) {
                setComment(path, srcComment);
                changed = true;
            }
        }
        return changed;
    }

    private static String buildPath(Deque<String> keyStack) {
        String[] keys = keyStack.toArray(new String[0]);
        StringBuilder sb = new StringBuilder();
        for (int i = keys.length - 1; i >= 0; i--) {
            if (sb.length() > 0)
                sb.append('.');
            sb.append(keys[i]);
        }
        return sb.toString();
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ')
            count++;
        return count;
    }
}
