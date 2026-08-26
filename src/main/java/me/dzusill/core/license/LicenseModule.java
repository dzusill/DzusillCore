package me.dzusill.core.license;

import java.util.concurrent.ThreadLocalRandom;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.config.ConfigManager;
import me.dzusill.core.module.AbstractModule;
import me.dzusill.core.scheduler.SchedulerService;

/**
 * The anti-piracy gate, shared by every paid plugin in the catalogue.
 *
 * <p>
 * On a market build ({@code -Plicensed}) it verifies the key at enable and again roughly every six hours. A definitive
 * negative verdict logs a console banner and disables the plugin; a licence server outage rides the signed
 * offline-grace window instead, so a customer is never bricked by our downtime. The check is fully asynchronous — the
 * server finishes starting regardless of how slow the licence server is.
 * </p>
 *
 * <p>
 * Add it to a plugin's module list right after the foundation module — it needs the config manager and the scheduler,
 * and nothing else:
 * </p>
 *
 * <pre>
 * new LicenseModule(this, "dshop", "https://builtbybit.com/resources/dshop", BBB_LICENSE)
 * </pre>
 *
 * <p>
 * Development builds skip it entirely and say so once in the log.
 * </p>
 */
public final class LicenseModule extends AbstractModule {

    /**
     * Base64 SPKI DER of the Ed25519 PUBLIC key. The matching private key lives only on the licence server.
     *
     * <p>
     * One pair for the whole catalogue: the signed payload names the product, so a key issued for dShop cannot be
     * replayed at dSell even though both trust this constant. Rotating it is therefore one operation rather than
     * sixteen — but it is also a release of all sixteen jars, because every copy already downloaded verifies against
     * whatever was compiled into it.
     * </p>
     */
    private static final String PUBLIC_KEY_B64 = "MCowBQYDK2VwAyEAuR2w4QtewYBUiHv8dWwO56EkGyRnAFRtY8By/eeGPXI=";

    /**
     * Where to check, when {@code license.yml} does not say.
     *
     * <p>
     * Shipping a default rather than an empty string is the difference between a plugin that works out of the box and
     * one whose first buyer files a bug. The customer-facing config still allows an override for the rare proxy case,
     * and pointing it somewhere else achieves nothing: responses are verified against the key above.
     * </p>
     */
    private static final String DEFAULT_VERIFY_URL = "https://licence.krampl.dev/verify";

    /** ~6 hours. Frequent enough that a revocation bites the same day, rare enough to be invisible. */
    private static final long RECHECK_TICKS = 20L * 60 * 60 * 6;

    private final String product;
    private final String storeUrl;
    private final String injectedKey;

    private LicenseClient client;

    /**
     * @param product
     *            the product id the licence server knows, lower-case ({@code "dshop"})
     * @param storeUrl
     *            where to send somebody whose licence failed
     */
    public LicenseModule(CorePlugin plugin, String product, String storeUrl) {
        this(plugin, product, storeUrl, null);
    }

    /**
     * @param injectedKey
     *            a BuiltByBit placeholder constant such as {@code "%%__DZ_LICENSE__%%"}, declared in the plugin's own
     *            class so BBB can rewrite it at download time. Pass {@code null} when the plugin is not sold through
     *            BuiltByBit.
     */
    public LicenseModule(CorePlugin plugin, String product, String storeUrl, String injectedKey) {
        super(plugin);
        this.product = product;
        this.storeUrl = storeUrl;
        this.injectedKey = injectedKey;
    }

    @Override
    public String name() {
        return "License";
    }

    @Override
    public void onEnable() {
        if (!BuildInfo.enforces(plugin)) {
            plugin.getLogger().info("License enforcement disabled (development build).");
            return;
        }

        LicenseConfig config = service(ConfigManager.class).register(new LicenseConfig(plugin));
        String key = resolveKey(config);
        String url = config.verifyUrl().isBlank() ? DEFAULT_VERIFY_URL : config.verifyUrl();

        this.client = new LicenseClient(product, key, url, plugin.getDescription().getVersion(), PUBLIC_KEY_B64,
                plugin.getDataFolder().toPath());

        if (!client.hasKey()) {
            fail("No license key set. Add your key to plugins/" + plugin.getName() + "/license.yml");
            return;
        }

        check();
        // Jitter so a network started together does not hit the licence server in the same instant, and so a fleet
        // does not stay in lockstep afterwards.
        long jitter = ThreadLocalRandom.current().nextLong(20L * 60 * 30);
        service(SchedulerService.class).repeatingAsync(this::check, RECHECK_TICKS + jitter, RECHECK_TICKS + jitter);
    }

    /**
     * @return the licence key, from BuiltByBit's injection if it happened and from {@code license.yml} otherwise
     *
     *         <p>
     *         BBB rewrites string constants inside {@code .class} files at download time, so on a purchased jar
     *         {@code injectedKey} already holds a real key and the buyer never touches a config. On a jar built here it
     *         still holds the literal placeholder — recognisable because it kept its {@code %%} markers — and the
     *         config is the answer instead.
     *         </p>
     */
    private String resolveKey(LicenseConfig config) {
        if (injectedKey != null && !injectedKey.isBlank() && !injectedKey.contains("%%")) {
            return injectedKey.trim();
        }
        return config.key();
    }

    private void check() {
        client.verify().thenAccept(state -> {
            long now = System.currentTimeMillis();
            if (state.allowsRunning(now)) {
                if (state.message() != null && state.message().contains("grace")) {
                    plugin.getLogger().warning(
                            "License server unreachable; running on offline grace (key " + client.redactedKey() + ").");
                } else {
                    plugin.getLogger().info("License OK (" + client.redactedKey() + ").");
                }
                return;
            }
            if (state.verdict() == LicenseVerdict.UNKNOWN && state.validUntilMillis() == 0) {
                fail("License could not be verified and no offline grace remains. " + state.message());
            } else {
                fail("License check failed: " + state.verdict()
                        + (state.message() == null || state.message().isBlank() ? "" : " — " + state.message()));
            }
        });
    }

    private void fail(String reason) {
        // verify() completes on an HTTP-client thread, and disabling a plugin has to happen on the main thread — the
        // global region on Folia, since it is server-wide rather than tied to any entity or chunk.
        service(SchedulerService.class).sync(() -> {
            var log = plugin.getLogger();
            log.severe("==============================================================");
            log.severe(plugin.getName() + " — LICENSE PROBLEM");
            log.severe(reason);
            log.severe("Purchase / support: " + storeUrl);
            log.severe("==============================================================");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        });
    }
}
