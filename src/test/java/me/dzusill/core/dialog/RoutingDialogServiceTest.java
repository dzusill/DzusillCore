package me.dzusill.core.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;
import me.dzusill.core.dialog.spi.DialogBackend;
import me.dzusill.core.dialog.spi.DialogCallbackSink;
import me.dzusill.core.dialog.spi.DialogValues;
import me.dzusill.core.scheduler.SchedulerService;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

/**
 * Routing, the guaranteed-callback contract, and the security checks that sit between a client packet and a handler.
 */
class RoutingDialogServiceTest {

    private ServerMock server;
    private CorePlugin plugin;
    private PendingDialogs pending;
    private RoutingDialogService dialogs;
    private PlayerMock alice;

    /** Records what it was asked to render and hands back the sink so a test can play the client. */
    private static final class FakeBackend implements DialogBackend {

        private final List<String> tokens = new ArrayList<>();
        private DialogCallbackSink sink;
        private boolean available = true;
        private boolean supported = true;
        private RuntimeException failure;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean supports(Player player) {
            return supported;
        }

        @Override
        public void show(Player player, DialogSpec spec, String token) {
            if (failure != null)
                throw failure;
            tokens.add(token);
        }

        @Override
        public void close(Player player) {
            // no-op
        }

        @Override
        public void attach(DialogCallbackSink value) {
            this.sink = value;
        }

        @Override
        public String describe() {
            return "fake";
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
        pending = new PendingDialogs();
        dialogs = new RoutingDialogService(plugin, new SchedulerService(plugin), pending, DialogFallback.none());
        alice = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private FakeBackend registerBackend() {
        FakeBackend backend = new FakeBackend();
        server.getServicesManager().register(DialogBackend.class, backend, plugin, ServicePriority.Normal);
        return backend;
    }

    private static DialogSpec confirmSpec() {
        return DialogSpec.builder(
                new DialogKind.Confirmation(DialogButton.callback("Yes", "yes"), DialogButton.callback("No", "no")),
                "Sure?").build();
    }

    @Nested
    @DisplayName("with no backend installed")
    class NoBackend {

        @Test
        @DisplayName("show reports it could not serve the dialog")
        void showReturnsEmpty() {
            assertTrue(dialogs.show(alice, confirmSpec(), (id, values) -> {
            }).isEmpty());
            assertFalse(dialogs.available());
        }

        @Test
        @DisplayName("confirm still resolves - exactly once, as false")
        void confirmStillResolves() {
            AtomicInteger calls = new AtomicInteger();
            AtomicReference<Boolean> value = new AtomicReference<>();
            dialogs.confirm(alice, "T", "B", accepted -> {
                calls.incrementAndGet();
                value.set(accepted);
            });
            assertEquals(1, calls.get());
            assertEquals(Boolean.FALSE, value.get());
        }

        @Test
        @DisplayName("input still resolves - exactly once, as empty string")
        void inputStillResolves() {
            AtomicInteger calls = new AtomicInteger();
            AtomicReference<String> value = new AtomicReference<>();
            dialogs.input(alice, "T", "L", result -> {
                calls.incrementAndGet();
                value.set(result);
            });
            assertEquals(1, calls.get());
            assertEquals("", value.get());
        }

        @Test
        @DisplayName("a fallback that takes the dialog owns the handler")
        void fallbackTakesOver() {
            dialogs.fallback((player, spec, handler) -> {
                handler.onSubmit("yes", DialogValues.empty());
                return true;
            });
            AtomicReference<Boolean> value = new AtomicReference<>();
            dialogs.confirm(alice, "T", "B", value::set);
            assertEquals(Boolean.TRUE, value.get());
        }
    }

    @Nested
    @DisplayName("with a backend installed")
    class WithBackend {

        @Test
        @DisplayName("the dialog is handed to the backend with a token")
        void rendersNatively() {
            FakeBackend backend = registerBackend();
            assertTrue(dialogs.show(alice, confirmSpec(), (id, values) -> {
            }).isPresent());
            assertEquals(1, backend.tokens.size());
            assertTrue(dialogs.available());
        }

        @Test
        @DisplayName("a submitted response reaches the handler with its values")
        void submitRoundTrips() {
            FakeBackend backend = registerBackend();
            AtomicReference<String> button = new AtomicReference<>();
            AtomicReference<String> text = new AtomicReference<>();

            DialogSpec spec = DialogSpec
                    .builder(DialogKind.MultiAction.of(List.of(DialogButton.callback("Go", "go"))), "T")
                    .input(DialogInput.Text.of("nick", "Nick")).build();
            dialogs.show(alice, spec, (id, values) -> {
                button.set(id);
                text.set(values.textOr("nick", "<none>"));
            });

            backend.sink.onSubmit(alice.getUniqueId(), backend.tokens.get(0), "go",
                    DialogValues.of(Map.of("nick", "Steve")));
            server.getScheduler().performTicks(2);

            assertEquals("go", button.get());
            assertEquals("Steve", text.get());
        }

        @Test
        @DisplayName("a response for another player's token is ignored")
        void foreignTokenIgnored() {
            FakeBackend backend = registerBackend();
            AtomicInteger calls = new AtomicInteger();
            dialogs.show(alice, confirmSpec(), (id, values) -> calls.incrementAndGet());

            backend.sink.onSubmit(UUID.randomUUID(), backend.tokens.get(0), "yes", DialogValues.empty());
            server.getScheduler().performTicks(2);

            assertEquals(0, calls.get());
        }

        @Test
        @DisplayName("an unknown token is ignored")
        void unknownTokenIgnored() {
            FakeBackend backend = registerBackend();
            AtomicInteger calls = new AtomicInteger();
            dialogs.show(alice, confirmSpec(), (id, values) -> calls.incrementAndGet());

            backend.sink.onSubmit(alice.getUniqueId(), "made-up", "yes", DialogValues.empty());
            server.getScheduler().performTicks(2);

            assertEquals(0, calls.get());
        }

        @Test
        @DisplayName("a replayed response is ignored")
        void replayIgnored() {
            FakeBackend backend = registerBackend();
            AtomicInteger calls = new AtomicInteger();
            dialogs.show(alice, confirmSpec(), (id, values) -> calls.incrementAndGet());

            String token = backend.tokens.get(0);
            backend.sink.onSubmit(alice.getUniqueId(), token, "yes", DialogValues.empty());
            backend.sink.onSubmit(alice.getUniqueId(), token, "yes", DialogValues.empty());
            server.getScheduler().performTicks(2);

            assertEquals(1, calls.get());
        }

        @Test
        @DisplayName("a flood of responses is rate limited")
        void floodIsDropped() {
            FakeBackend backend = registerBackend();
            // The backend is resolved lazily on first use, which is also when it receives the sink.
            dialogs.show(alice, confirmSpec(), (id, values) -> {
            });
            for (int i = 0; i < 50; i++)
                backend.sink.onSubmit(alice.getUniqueId(), "junk-" + i, "yes", DialogValues.empty());
            // Nothing to assert beyond "did not throw and did not dispatch"; the guard exists to bound log noise.
            assertEquals(1, pending.size(), "the real pending dialog must survive a flood of junk tokens");
        }

        @Test
        @DisplayName("a backend that throws falls back instead of losing the dialog")
        void backendFailureFallsBack() {
            FakeBackend backend = registerBackend();
            backend.failure = new IllegalStateException("boom");
            AtomicReference<Boolean> value = new AtomicReference<>();
            dialogs.fallback((player, spec, handler) -> {
                handler.onCancel();
                return true;
            });

            dialogs.confirm(alice, "T", "B", value::set);

            assertEquals(Boolean.FALSE, value.get());
            assertEquals(0, pending.size(), "the abandoned token must not leak");
        }

        @Test
        @DisplayName("forceFallback bypasses a healthy backend, so QA can exercise both paths")
        void forceFallbackWins() {
            FakeBackend backend = registerBackend();
            dialogs.forceFallback(true);
            assertFalse(dialogs.available());
            assertTrue(dialogs.show(alice, confirmSpec(), (id, values) -> {
            }).isEmpty());
            assertEquals(0, backend.tokens.size());
        }

        @Test
        @DisplayName("an unsupported client falls back rather than being sent a screen it cannot draw")
        void unsupportedClientFallsBack() {
            FakeBackend backend = registerBackend();
            backend.supported = false;
            AtomicReference<Boolean> value = new AtomicReference<>();
            dialogs.confirm(alice, "T", "B", value::set);
            assertEquals(Boolean.FALSE, value.get());
            assertEquals(0, backend.tokens.size());
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("quitting resolves every open dialog as cancelled")
        void quitCancels() {
            registerBackend();
            AtomicInteger cancels = new AtomicInteger();
            dialogs.show(alice, confirmSpec(), new DialogHandler() {

                @Override
                public void onSubmit(String buttonId, DialogValues values) {
                    // not used
                }

                @Override
                public void onCancel() {
                    cancels.incrementAndGet();
                }
            });

            dialogs.forget(alice.getUniqueId());
            assertEquals(1, cancels.get());
            assertEquals(0, pending.size());
        }

        @Test
        @DisplayName("reload resolves in-flight dialogs rather than dropping them")
        void reloadCancels() {
            registerBackend();
            AtomicInteger cancels = new AtomicInteger();
            dialogs.show(alice, confirmSpec(), new DialogHandler() {

                @Override
                public void onSubmit(String buttonId, DialogValues values) {
                    // not used
                }

                @Override
                public void onCancel() {
                    cancels.incrementAndGet();
                }
            });

            dialogs.reload();
            assertEquals(1, cancels.get());
            assertEquals(0, pending.size());
        }

        @Test
        @DisplayName("closing a handle cancels exactly once")
        void handleClose() {
            registerBackend();
            AtomicInteger cancels = new AtomicInteger();
            DialogHandle handle = dialogs.show(alice, confirmSpec(), new DialogHandler() {

                @Override
                public void onSubmit(String buttonId, DialogValues values) {
                    // not used
                }

                @Override
                public void onCancel() {
                    cancels.incrementAndGet();
                }
            }).orElseThrow();

            assertTrue(handle.isOpen());
            handle.close();
            handle.close();
            assertFalse(handle.isOpen());
            assertEquals(1, cancels.get());
        }

        @Test
        @DisplayName("sweep resolves expired dialogs")
        void sweepCancels() {
            long[] clock = {1_000L};
            PendingDialogs shortLived = new PendingDialogs(50L, () -> clock[0]);
            RoutingDialogService service = new RoutingDialogService(plugin, new SchedulerService(plugin), shortLived,
                    DialogFallback.none());
            registerBackend();
            AtomicInteger cancels = new AtomicInteger();
            service.show(alice, confirmSpec(), new DialogHandler() {

                @Override
                public void onSubmit(String buttonId, DialogValues values) {
                    // not used
                }

                @Override
                public void onCancel() {
                    cancels.incrementAndGet();
                }
            });

            assertEquals(0, service.sweep(), "nothing is expired yet");
            clock[0] += 100L;
            assertEquals(1, service.sweep());
            assertEquals(1, cancels.get());
        }
    }

    @Test
    @DisplayName("null values from a backend degrade to empty rather than NPE-ing the handler")
    void nullValuesTolerated() {
        FakeBackend backend = registerBackend();
        AtomicReference<String> text = new AtomicReference<>("unset");
        dialogs.show(alice, confirmSpec(), (id, values) -> text.set(values.textOr("nope", null)));

        backend.sink.onSubmit(alice.getUniqueId(), backend.tokens.get(0), "yes", null);
        server.getScheduler().performTicks(2);

        assertNull(text.get());
    }
}
