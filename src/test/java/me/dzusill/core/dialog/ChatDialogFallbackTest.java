package me.dzusill.core.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;
import me.dzusill.core.dialog.spi.DialogValues;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.prompt.ChatPromptService;
import me.dzusill.core.scheduler.SchedulerService;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

/**
 * The fallback is what lets call sites ignore server version entirely, so its job is to either serve the dialog
 * faithfully or decline outright - never to approximate.
 */
class ChatDialogFallbackTest {

    private ServerMock server;
    private CorePlugin plugin;
    private ChatDialogFallback fallback;
    private PlayerMock alice;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
        MessageService messages = new MessageService(plugin);
        fallback = new ChatDialogFallback(messages,
                new ChatPromptService(plugin, new SchedulerService(plugin), messages));
        alice = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @SuppressWarnings("deprecation")
    private void chat(String message) {
        server.getPluginManager().callEvent(new org.bukkit.event.player.AsyncPlayerChatEvent(false, alice, message,
                new java.util.HashSet<>(java.util.Set.of(alice))));
    }

    @Test
    @DisplayName("a notice is delivered and resolves immediately")
    void notice() {
        AtomicReference<String> button = new AtomicReference<>();
        DialogSpec spec = DialogSpec.builder(new DialogKind.Notice(DialogButton.callback("OK", "ok")), "Hi")
                .text("Body").build();

        assertTrue(fallback.handle(alice, spec, (id, values) -> button.set(id)));
        assertEquals("ok", button.get());
    }

    @Test
    @DisplayName("a confirmation becomes a yes/no prompt")
    void confirmYes() {
        AtomicReference<String> button = new AtomicReference<>();
        DialogSpec spec = DialogSpec.builder(new DialogKind.Confirmation(DialogButton.callback("<green>Yes", "yes"),
                DialogButton.callback("<red>No", "no")), "Sure?").build();

        assertTrue(fallback.handle(alice, spec, (id, values) -> button.set(id)));
        chat("yes");
        server.getScheduler().performTicks(2);
        assertEquals("yes", button.get());
    }

    @Test
    @DisplayName("anything that is not a yes counts as no")
    void confirmNo() {
        AtomicReference<String> button = new AtomicReference<>();
        DialogSpec spec = DialogSpec.builder(
                new DialogKind.Confirmation(DialogButton.callback("Yes", "yes"), DialogButton.callback("No", "no")),
                "Sure?").build();

        fallback.handle(alice, spec, (id, values) -> button.set(id));
        chat("nope");
        server.getScheduler().performTicks(2);
        assertEquals("no", button.get());
    }

    @Test
    @DisplayName("cancelling a confirmation resolves it as no, not as nothing")
    void confirmCancel() {
        AtomicReference<String> button = new AtomicReference<>();
        DialogSpec spec = DialogSpec.builder(
                new DialogKind.Confirmation(DialogButton.callback("Yes", "yes"), DialogButton.callback("No", "no")),
                "Sure?").build();

        fallback.handle(alice, spec, (id, values) -> button.set(id));
        chat("cancel");
        server.getScheduler().performTicks(2);
        assertEquals("no", button.get());
    }

    @Test
    @DisplayName("a single text input becomes a chat prompt carrying the value back under its key")
    void textInput() {
        AtomicReference<String> value = new AtomicReference<>();
        DialogSpec spec = DialogSpec
                .builder(new DialogKind.Confirmation(DialogButton.callback("OK", "ok"),
                        DialogButton.callback("Cancel", "cancel")), "Rename")
                .input(DialogInput.Text.of("name", "New name")).build();

        assertTrue(fallback.handle(alice, spec, (id, values) -> value.set(values.textOr("name", "<missing>"))));
        chat("spawn");
        server.getScheduler().performTicks(2);
        assertEquals("spawn", value.get());
    }

    @Test
    @DisplayName("cancelling a text prompt cancels the dialog rather than submitting an empty value")
    void textInputCancel() {
        AtomicInteger submits = new AtomicInteger();
        AtomicInteger cancels = new AtomicInteger();
        DialogSpec spec = DialogSpec
                .builder(new DialogKind.Confirmation(DialogButton.callback("OK", "ok"),
                        DialogButton.callback("Cancel", "cancel")), "Rename")
                .input(DialogInput.Text.of("name", "New name")).build();

        fallback.handle(alice, spec, new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, DialogValues values) {
                submits.incrementAndGet();
            }

            @Override
            public void onCancel() {
                cancels.incrementAndGet();
            }
        });
        chat("cancel");
        server.getScheduler().performTicks(2);

        assertEquals(0, submits.get());
        assertEquals(1, cancels.get());
    }

    @Test
    @DisplayName("declines what chat cannot represent instead of approximating it")
    void declinesRichSpecs() {
        DialogHandler noop = (id, values) -> {
        };

        DialogSpec twoInputs = DialogSpec
                .builder(DialogKind.MultiAction.of(List.of(DialogButton.callback("Go", "go"))), "T")
                .input(DialogInput.Text.of("a", "A")).input(DialogInput.Text.of("b", "B")).build();
        assertFalse(fallback.handle(alice, twoInputs, noop));

        DialogSpec slider = DialogSpec
                .builder(DialogKind.MultiAction.of(List.of(DialogButton.callback("Go", "go"))), "T")
                .input(DialogInput.NumberRange.ofInts("amount", "Amount", 1, 64, 1)).build();
        assertFalse(fallback.handle(alice, slider, noop));

        DialogSpec picker = DialogSpec
                .builder(DialogKind.MultiAction.of(List.of(DialogButton.callback("Go", "go"))), "T")
                .input(DialogInput.SingleOption.of("reason", "Reason",
                        List.of(DialogInput.SingleOption.Option.of("spam", "Spam"))))
                .build();
        assertFalse(fallback.handle(alice, picker, noop));
    }
}
