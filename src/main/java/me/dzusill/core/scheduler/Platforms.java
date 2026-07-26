package me.dzusill.core.scheduler;

/**
 * Detects the server flavour at runtime.
 *
 * <p>
 * Paper and Purpur are indistinguishable for scheduling purposes — Purpur is a Paper fork and keeps the single
 * main-thread model, so both take the {@link BukkitPlatformScheduler} path. Only Folia regionises the tick loop and
 * needs {@link FoliaPlatformScheduler}.
 * </p>
 */
public final class Platforms {

    /**
     * Folia's regionised server class. Note the plural {@code threadedregions} — the singular spelling silently
     * misdetects every Folia server as plain Bukkit.
     */
    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    private static final boolean FOLIA = detect(FOLIA_MARKER);
    private static final boolean PAPER = detect("com.destroystokyo.paper.PaperConfig")
            || detect("io.papermc.paper.configuration.Configuration");
    private static final boolean PURPUR = detect("org.purpurmc.purpur.PurpurConfig");

    private Platforms() {
    }

    private static boolean detect(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    /** @return {@code true} when running on a Folia (regionised) server */
    public static boolean isFolia() {
        return FOLIA;
    }

    /** @return {@code true} on Paper or any Paper fork (Purpur, Folia) */
    public static boolean isPaper() {
        return PAPER;
    }

    /** @return {@code true} on Purpur specifically */
    public static boolean isPurpur() {
        return PURPUR;
    }

    /** @return a short human-readable platform name for startup logging */
    public static String name() {
        if (FOLIA) {
            return "Folia";
        }
        if (PURPUR) {
            return "Purpur";
        }
        if (PAPER) {
            return "Paper";
        }
        return "Bukkit/Spigot";
    }
}
