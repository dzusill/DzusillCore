package me.dzusill.core.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Two plugins, one rendering backend.
 *
 * <p>
 * This is the shape that broke in production: a backend keeps a single sink, every plugin resolves the same backend
 * instance, and whichever plugin attached last used to receive <em>everybody's</em> clicks. The other plugins' dialogs
 * closed on screen and their handlers never ran - a choice screen the player could not answer.
 * </p>
 */
class DialogSinksTest {

    private ServerMock server;
    private CorePlugin plugin;
    private PlayerMock alice;

    /** Stands in for dDialogs: one instance, one sink field, shared by every plugin that resolves it. */
    private static final class SharedBackend implements DialogBackend {

        private final List<String> tokens = new ArrayList<>();
        private DialogCallbackSink sink;
        private int attachments;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean supports(Player player) {
            return true;
        }

        @Override
        public void show(Player player, DialogSpec spec, String token) {
            tokens.add(token);
        }

        @Override
        public void close(Player player) {
            // no-op
        }

        @Override
        public void attach(DialogCallbackSink value) {
            this.sink = value;
            this.attachments++;
        }

        @Override
        public String describe() {
            return "shared";
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
        alice = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private RoutingDialogService service() {
        return new RoutingDialogService(plugin, new SchedulerService(plugin), new PendingDialogs(),
                DialogFallback.none());
    }

    private SharedBackend registerBackend() {
        SharedBackend backend = new SharedBackend();
        server.getServicesManager().register(DialogBackend.class, backend, plugin, ServicePriority.Normal);
        return backend;
    }

    private static DialogSpec confirmSpec() {
        return DialogSpec.builder(
                new DialogKind.Confirmation(DialogButton.callback("Yes", "yes"), DialogButton.callback("No", "no")),
                "Sure?").build();
    }

    @Test
    @DisplayName("each plugin's response reaches that plugin, not whichever attached last")
    void responsesGoToTheirOwner() {
        SharedBackend backend = registerBackend();
        RoutingDialogService first = service();
        RoutingDialogService second = service();

        AtomicReference<String> answeredFirst = new AtomicReference<>();
        AtomicReference<String> answeredSecond = new AtomicReference<>();
        first.show(alice, confirmSpec(), (id, values) -> answeredFirst.set(id));
        second.show(alice, confirmSpec(), (id, values) -> answeredSecond.set(id));

        String firstToken = backend.tokens.get(0);
        String secondToken = backend.tokens.get(1);

        backend.sink.onSubmit(alice.getUniqueId(), firstToken, "yes", DialogValues.empty());
        backend.sink.onSubmit(alice.getUniqueId(), secondToken, "no", DialogValues.empty());
        server.getScheduler().performTicks(2);

        assertEquals("yes", answeredFirst.get(), "the first plugin's own dialog must still answer it");
        assertEquals("no", answeredSecond.get(), "the second plugin's own dialog must still answer it");
    }

    @Test
    @DisplayName("every plugin attaches the same sink, so there is nothing to overwrite")
    void backendKeepsOneSink() {
        SharedBackend backend = registerBackend();
        RoutingDialogService first = service();
        RoutingDialogService second = service();

        first.show(alice, confirmSpec(), (id, values) -> {
        });
        DialogCallbackSink afterFirst = backend.sink;
        second.show(alice, confirmSpec(), (id, values) -> {
        });

        assertEquals(2, backend.attachments, "each plugin resolves the backend for itself");
        assertEquals(afterFirst, backend.sink, "but they hand it the same shared sink");
        assertEquals(DialogSinks.shared(), backend.sink);
    }

    @Test
    @DisplayName("a cancellation reaches the plugin that owns the token")
    void cancellationsGoToTheirOwner() {
        SharedBackend backend = registerBackend();
        RoutingDialogService first = service();
        RoutingDialogService second = service();

        AtomicReference<Boolean> cancelledFirst = new AtomicReference<>(false);
        AtomicReference<Boolean> cancelledSecond = new AtomicReference<>(false);
        first.show(alice, confirmSpec(), new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, DialogValues values) {
                // not this test
            }

            @Override
            public void onCancel() {
                cancelledFirst.set(true);
            }
        });
        second.show(alice, confirmSpec(), new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, DialogValues values) {
                // not this test
            }

            @Override
            public void onCancel() {
                cancelledSecond.set(true);
            }
        });

        backend.sink.onCancelled(alice.getUniqueId(), backend.tokens.get(1));
        server.getScheduler().performTicks(2);

        assertFalse(cancelledFirst.get(), "a cancellation must not resolve another plugin's dialog");
        assertTrue(cancelledSecond.get());
    }

    @Test
    @DisplayName("a token nobody owns is dropped rather than dispatched anywhere")
    void unknownTokenIsDropped() {
        SharedBackend backend = registerBackend();
        RoutingDialogService first = service();
        RoutingDialogService second = service();

        AtomicReference<String> answeredFirst = new AtomicReference<>();
        AtomicReference<String> answeredSecond = new AtomicReference<>();
        first.show(alice, confirmSpec(), (id, values) -> answeredFirst.set(id));
        second.show(alice, confirmSpec(), (id, values) -> answeredSecond.set(id));

        backend.sink.onSubmit(alice.getUniqueId(), "made-up", "yes", DialogValues.empty());
        server.getScheduler().performTicks(2);

        assertEquals(null, answeredFirst.get());
        assertEquals(null, answeredSecond.get());
    }

    @Test
    @DisplayName("a shut-down plugin stops receiving responses")
    void shutdownUnregisters() {
        SharedBackend backend = registerBackend();
        RoutingDialogService first = service();

        AtomicReference<String> answered = new AtomicReference<>();
        first.show(alice, confirmSpec(), (id, values) -> answered.set(id));
        String token = backend.tokens.get(0);

        first.shutdown();
        backend.sink.onSubmit(alice.getUniqueId(), token, "yes", DialogValues.empty());
        server.getScheduler().performTicks(2);

        assertEquals(null, answered.get(), "shutdown resolves the dialog as cancelled; a late click must not land");
    }
}
