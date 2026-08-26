package me.dzusill.core.license;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * The license-check client: POSTs a nonce-stamped request to the license server, verifies the Ed25519-signed verdict
 * against the embedded public key, and caches the last VALID response as a tamper-proof offline-grace token.
 *
 * <p>
 * Design guarantees (see the {@code /spigot-secure-licence} skill): a spoofed server can't forge signatures; the nonce
 * blocks replay of a captured VALID response; the signed {@code validUntil} lets the plugin keep running while the
 * license server is down (editing the cache file breaks the signature). Never blocks the main thread.
 * </p>
 */
public final class LicenseClient {

    private static final Gson GSON = new Gson();

    private final String product;
    private final String licenseKey;
    private final String verifyUrl;
    private final String pluginVersion;
    private final SignatureVerifier verifier;
    private final Path dataFolder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String instanceId;

    public LicenseClient(String product, String licenseKey, String verifyUrl, String pluginVersion, String publicKeyB64,
            Path dataFolder) {
        this.product = product;
        this.licenseKey = licenseKey == null ? "" : licenseKey.trim();
        this.verifyUrl = verifyUrl == null ? "" : verifyUrl.trim();
        this.pluginVersion = pluginVersion;
        this.verifier = new SignatureVerifier(publicKeyB64);
        this.dataFolder = dataFolder;
        this.instanceId = loadOrCreateInstanceId();
    }

    public boolean hasKey() {
        return !licenseKey.isEmpty();
    }

    public boolean hasUrl() {
        return !verifyUrl.isEmpty();
    }

    /** Async verification. The result is a definitive verdict, a signed grace state, or UNKNOWN. */
    public CompletableFuture<LicenseState> verify() {
        String nonce = UUID.randomUUID().toString();
        JsonObject body = new JsonObject();
        body.addProperty("product", product);
        body.addProperty("key", licenseKey);
        body.addProperty("instanceId", instanceId);
        body.addProperty("nonce", nonce);
        body.addProperty("pluginVersion", pluginVersion);

        HttpRequest request = HttpRequest.newBuilder(URI.create(verifyUrl)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleResponse(response.body(), nonce)).exceptionally(error -> graceFromCache());
    }

    private LicenseState handleResponse(String responseBody, String sentNonce) {
        try {
            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            String status = json.get("status").getAsString();
            long validUntil = json.get("validUntil").getAsLong();
            String nonce = json.get("nonce").getAsString();
            String signature = json.get("signature").getAsString();
            String message = json.has("message") ? json.get("message").getAsString() : "";

            if (!sentNonce.equals(nonce)) {
                return graceFromCache(); // replayed or mangled response
            }
            String payload = String.join("|", product, licenseKey, instanceId, nonce, status,
                    Long.toString(validUntil));
            if (!verifier.verify(payload, signature)) {
                return graceFromCache(); // unsigned/spoofed response — treat like a network failure
            }

            LicenseVerdict verdict = LicenseVerdict.valueOf(status);
            LicenseState state = new LicenseState(verdict, validUntil, message);
            if (verdict == LicenseVerdict.VALID) {
                saveCache(json); // the signed VALID response IS the grace token
            } else {
                deleteCache(); // a definitive negative verdict revokes grace too
            }
            return state;
        } catch (Exception malformed) {
            return graceFromCache();
        }
    }

    /** Offline grace: reload the last signed VALID response and re-verify it. Tampering breaks the signature. */
    private LicenseState graceFromCache() {
        try {
            Path cache = dataFolder.resolve("license-cache.json");
            if (!Files.exists(cache)) {
                return new LicenseState(LicenseVerdict.UNKNOWN, 0, "license server unreachable, no grace cache");
            }
            JsonObject json = GSON.fromJson(Files.readString(cache), JsonObject.class);
            String status = json.get("status").getAsString();
            long validUntil = json.get("validUntil").getAsLong();
            String nonce = json.get("nonce").getAsString();
            String payload = String.join("|", product, licenseKey, instanceId, nonce, status,
                    Long.toString(validUntil));
            if (!verifier.verify(payload, json.get("signature").getAsString())) {
                return new LicenseState(LicenseVerdict.UNKNOWN, 0, "grace cache invalid");
            }
            return new LicenseState(LicenseVerdict.valueOf(status), validUntil, "grace (offline)");
        } catch (Exception e) {
            return new LicenseState(LicenseVerdict.UNKNOWN, 0, "grace cache unreadable");
        }
    }

    private void saveCache(JsonObject signedResponse) {
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(dataFolder.resolve("license-cache.json"), GSON.toJson(signedResponse));
        } catch (Exception ignored) {
            // Cache is best-effort; a write failure only shortens offline grace.
        }
    }

    private void deleteCache() {
        try {
            Files.deleteIfExists(dataFolder.resolve("license-cache.json"));
        } catch (Exception ignored) {
            // Nothing actionable; grace simply won't be available next time.
        }
    }

    private String loadOrCreateInstanceId() {
        try {
            Path file = dataFolder.resolve("instance-id");
            if (Files.exists(file)) {
                return Files.readString(file).trim();
            }
            String id = UUID.randomUUID().toString();
            Files.createDirectories(dataFolder);
            Files.writeString(file, id);
            return id;
        } catch (Exception e) {
            return "volatile-" + UUID.randomUUID(); // still functional, just not sticky across restarts
        }
    }

    /** For log lines — never print the whole key. */
    public String redactedKey() {
        return licenseKey.length() <= 4 ? "****" : licenseKey.substring(0, 4) + "…";
    }
}
