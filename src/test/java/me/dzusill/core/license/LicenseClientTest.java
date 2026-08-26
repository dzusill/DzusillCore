package me.dzusill.core.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises the full fail-mode table from the {@code /spigot-secure-licence} skill against a stub license server whose
 * responses are signed with a throwaway keypair. Proves the security-relevant behaviors: spoofed/tampered/replayed
 * responses are rejected, and a server outage rides the signed grace window instead of bricking the plugin.
 */
class LicenseClientTest {

    private static final Gson GSON = new Gson();
    private static final String PRODUCT = "dgems";
    private static final String KEY = "DGEM-AAAA-BBBB-CCCC-DDDD";

    private KeyPair signingPair;
    private String publicKeyB64;
    private HttpServer server;
    private String url;
    /** What the stub server returns; each test sets this before calling verify(). */
    private final AtomicReference<String> nextResponse = new AtomicReference<>();
    /** When true, the "server" refuses connections (simulated outage). */
    private final AtomicReference<Boolean> down = new AtomicReference<>(false);

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() throws Exception {
        signingPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        publicKeyB64 = Base64.getEncoder().encodeToString(signingPair.getPublic().getEncoded());

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/verify", exchange -> {
            if (Boolean.TRUE.equals(down.get())) {
                exchange.close();
                return;
            }
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject request = GSON.fromJson(requestBody, JsonObject.class);
            String response = nextResponse.get().replace("__NONCE__", request.get("nonce").getAsString());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/verify";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private LicenseClient client() {
        return new LicenseClient(PRODUCT, KEY, url, "1.0.0", publicKeyB64, dataFolder);
    }

    /** Builds a correctly-signed server response; {@code __NONCE__} is echoed from the request by the stub. */
    private String signedResponse(String status, long validUntil, PrivateKey signWith, String instanceId)
            throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("status", status);
        json.addProperty("validUntil", validUntil);
        json.addProperty("nonce", "__NONCE__");
        json.addProperty("message", "");

        // The signed payload uses the literal echoed nonce; recompute the signature per request is impossible in a
        // static string, so tests that need a valid signature read back the instanceId the client persisted.
        String payload = String.join("|", PRODUCT, KEY, instanceId, "__NONCE__", status, Long.toString(validUntil));
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(signWith);
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        json.addProperty("signature", Base64.getEncoder().encodeToString(signature.sign()));
        return GSON.toJson(json);
    }

    /**
     * The client generates a random nonce per request, so a statically-signed response can't match it. These tests
     * instead run the sign step INSIDE the stub handler by capturing the instanceId and re-signing with the echoed
     * nonce. Simpler: drive the stub to sign dynamically.
     */
    private void respondSigned(String status, long validUntil, PrivateKey signWith) {
        String instanceId = readInstanceId();
        server.removeContext("/verify");
        server.createContext("/verify", exchange -> {
            if (Boolean.TRUE.equals(down.get())) {
                exchange.close();
                return;
            }
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject request = GSON.fromJson(requestBody, JsonObject.class);
            String nonce = request.get("nonce").getAsString();
            try {
                JsonObject json = new JsonObject();
                json.addProperty("status", status);
                json.addProperty("validUntil", validUntil);
                json.addProperty("nonce", nonce);
                json.addProperty("message", "");
                String payload = String.join("|", PRODUCT, KEY, instanceId, nonce, status, Long.toString(validUntil));
                Signature signature = Signature.getInstance("Ed25519");
                signature.initSign(signWith);
                signature.update(payload.getBytes(StandardCharsets.UTF_8));
                json.addProperty("signature", Base64.getEncoder().encodeToString(signature.sign()));
                byte[] bytes = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            }
            exchange.close();
        });
    }

    /** Drives the stub to echo a mismatched nonce (replay/mangle simulation). */
    private void respondWithWrongNonce(String status, long validUntil, PrivateKey signWith) {
        String instanceId = readInstanceId();
        server.removeContext("/verify");
        server.createContext("/verify", exchange -> {
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                String nonce = "not-the-nonce-you-sent";
                JsonObject json = new JsonObject();
                json.addProperty("status", status);
                json.addProperty("validUntil", validUntil);
                json.addProperty("nonce", nonce);
                json.addProperty("message", "");
                String payload = String.join("|", PRODUCT, KEY, instanceId, nonce, status, Long.toString(validUntil));
                Signature signature = Signature.getInstance("Ed25519");
                signature.initSign(signWith);
                signature.update(payload.getBytes(StandardCharsets.UTF_8));
                json.addProperty("signature", Base64.getEncoder().encodeToString(signature.sign()));
                byte[] bytes = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            }
            exchange.close();
        });
    }

    /** The client persists its instance id on construction; read it so the stub can sign the right payload. */
    private String readInstanceId() {
        client(); // constructs and writes instance-id
        try {
            return Files.readString(dataFolder.resolve("instance-id")).trim();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long future() {
        return System.currentTimeMillis() + 72L * 60 * 60 * 1000;
    }

    @Test
    void validSignedResponseRunsAndCachesGraceToken() {
        respondSigned("VALID", future(), signingPair.getPrivate());
        LicenseState state = client().verify().join();

        assertEquals(LicenseVerdict.VALID, state.verdict());
        assertTrue(state.allowsRunning(System.currentTimeMillis()));
        assertTrue(Files.exists(dataFolder.resolve("license-cache.json")), "a VALID response must be cached");
    }

    @Test
    void definitiveNegativeVerdictDisablesAndClearsGrace() {
        respondSigned("VALID", future(), signingPair.getPrivate());
        client().verify().join(); // warm the cache
        assertTrue(Files.exists(dataFolder.resolve("license-cache.json")));

        respondSigned("REVOKED", future(), signingPair.getPrivate());
        LicenseState state = client().verify().join();
        assertEquals(LicenseVerdict.REVOKED, state.verdict());
        assertFalse(state.allowsRunning(System.currentTimeMillis()));
        assertFalse(Files.exists(dataFolder.resolve("license-cache.json")), "negative verdict revokes grace");
    }

    @Test
    void responseSignedWithWrongKeyIsRejectedAsUnknown() throws Exception {
        KeyPair attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        respondSigned("VALID", future(), attacker.getPrivate());
        LicenseState state = client().verify().join();

        // A spoofed server can return VALID but cannot forge the signature → UNKNOWN, no grace, plugin disables.
        assertEquals(LicenseVerdict.UNKNOWN, state.verdict());
        assertFalse(state.allowsRunning(System.currentTimeMillis()));
    }

    @Test
    void replayedResponseWithWrongNonceIsRejected() {
        respondWithWrongNonce("VALID", future(), signingPair.getPrivate());
        LicenseState state = client().verify().join();
        assertEquals(LicenseVerdict.UNKNOWN, state.verdict(), "a nonce mismatch means replay/mangle → not trusted");
    }

    @Test
    void serverDownWithFreshCacheRunsOnGrace() {
        respondSigned("VALID", future(), signingPair.getPrivate());
        client().verify().join(); // cache a valid response

        down.set(true);
        LicenseState state = client().verify().join();
        assertEquals(LicenseVerdict.VALID, state.verdict());
        assertTrue(state.allowsRunning(System.currentTimeMillis()), "offline grace keeps a paid server running");
        assertTrue(state.message().contains("grace"));
    }

    @Test
    void serverDownWithNoCacheYieldsUnknownNoGrace() {
        down.set(true);
        LicenseState state = client().verify().join();
        assertEquals(LicenseVerdict.UNKNOWN, state.verdict());
        assertEquals(0, state.validUntilMillis(), "no cache, no grace → the module will disable");
    }

    @Test
    void serverDownWithExpiredCacheDoesNotRun() throws Exception {
        respondSigned("VALID", System.currentTimeMillis() + 1000, signingPair.getPrivate());
        client().verify().join(); // cache a nearly-expired grace token
        Thread.sleep(1100);

        down.set(true);
        LicenseState state = client().verify().join();
        // The signature is still valid, but validUntil is in the past → allowsRunning is false.
        assertFalse(state.allowsRunning(System.currentTimeMillis()), "expired grace must not keep running");
    }

    @Test
    void tamperedCacheFileBreaksSignatureAndDeniesGrace() throws Exception {
        respondSigned("VALID", future(), signingPair.getPrivate());
        client().verify().join();

        // Attacker edits the cached grace token to extend validUntil far into the future.
        Path cache = dataFolder.resolve("license-cache.json");
        JsonObject json = GSON.fromJson(Files.readString(cache), JsonObject.class);
        json.addProperty("validUntil", future() * 10);
        Files.writeString(cache, GSON.toJson(json));

        down.set(true);
        LicenseState state = client().verify().join();
        assertEquals(LicenseVerdict.UNKNOWN, state.verdict(), "tampering must break the signature");
        assertFalse(state.allowsRunning(System.currentTimeMillis()));
    }

    @Test
    void redactedKeyNeverExposesTheFullKey() {
        assertEquals("DGEM…", client().redactedKey());
    }
}
