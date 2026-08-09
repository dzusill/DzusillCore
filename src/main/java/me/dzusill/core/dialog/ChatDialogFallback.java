package me.dzusill.core.dialog;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;

import me.dzusill.core.dialog.spi.DialogValues;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.message.Placeholder;
import me.dzusill.core.prompt.PromptOptions;
import me.dzusill.core.prompt.PromptService;

/**
 * Serves dialogs as chat when no native rendering is possible.
 *
 * <p>
 * This is what makes {@link DialogService} safe to call unconditionally: on a pre-1.21.6 server, for a player on an old
 * client, or with no rendering backend installed, the same call still reaches the same handler.
 * </p>
 *
 * <h2>What it can and cannot represent</h2>
 *
 * <p>
 * A notice becomes a chat message; a confirmation becomes a clickable yes/no prompt; a single text input becomes a chat
 * prompt. Anything richer - several inputs, a slider, an option picker - returns {@code false} rather than pretending,
 * so the caller keeps whatever menu it already had. A sequential multi-input wizard is the obvious next step here.
 * </p>
 *
 * <p>
 * The yes/no buttons use {@code suggest_command} rather than {@code run_command} on purpose: it pre-fills the chat box,
 * which the chat capture then reads. That keeps the flow clickable without registering a command per dialog - and core
 * has no way to unregister commands, so per-dialog commands would leak on every reload.
 * </p>
 */
public final class ChatDialogFallback implements DialogFallback {

    private static final String YES = "yes";
    private static final String NO = "no";

    private final MessageService messages;
    private final PromptService prompts;

    public ChatDialogFallback(MessageService messages, PromptService prompts) {
        this.messages = messages;
        this.prompts = prompts;
    }

    @Override
    public boolean handle(Player player, DialogSpec spec, DialogHandler handler) {
        List<DialogInput> inputs = spec.inputs();
        if (inputs.size() > 1)
            return false;

        if (inputs.isEmpty())
            return handleWithoutInput(player, spec, handler);

        if (inputs.get(0) instanceof DialogInput.Text text)
            return handleTextInput(player, spec, text, handler);

        // Sliders and option pickers have no faithful chat equivalent; better to decline than to approximate.
        return false;
    }

    private boolean handleWithoutInput(Player player, DialogSpec spec, DialogHandler handler) {
        if (spec.kind() instanceof DialogKind.Notice notice) {
            sendHeader(player, spec);
            String buttonId = callbackId(notice.action());
            if (buttonId == null)
                handler.onCancel();
            else
                handler.onSubmit(buttonId, DialogValues.empty());
            return true;
        }

        if (spec.kind() instanceof DialogKind.Confirmation confirmation) {
            sendHeader(player, spec);
            String yesId = callbackId(confirmation.yes());
            String noId = callbackId(confirmation.no());

            prompts.prompt(player,
                    PromptOptions.of("<click:suggest_command:'" + YES + "'><green>["
                            + stripTags(confirmation.yes().label()) + "]</green></click> <click:suggest_command:'" + NO
                            + "'><red>[" + stripTags(confirmation.no().label()) + "]</red></click>"),
                    answer -> {
                        boolean accepted = answer.trim().toLowerCase(Locale.ROOT).startsWith("y");
                        String chosen = accepted ? yesId : noId;
                        if (chosen == null)
                            handler.onCancel();
                        else
                            handler.onSubmit(chosen, DialogValues.empty());
                    });
            return true;
        }

        return false;
    }

    private boolean handleTextInput(Player player, DialogSpec spec, DialogInput.Text input, DialogHandler handler) {
        String buttonId = firstCallbackId(spec);
        if (buttonId == null)
            return false;

        sendHeader(player, spec);
        prompts.prompt(player, PromptOptions.of("<yellow>" + input.label()).withMaxLength(input.maxLength()),
                answer -> {
                    if (answer.isEmpty())
                        handler.onCancel();
                    else
                        handler.onSubmit(buttonId, DialogValues.of(Map.of(input.key(), answer)));
                });
        return true;
    }

    private void sendHeader(Player player, DialogSpec spec) {
        messages.sendRaw(player, "<gold><bold>" + spec.title(), Placeholder.empty());
        for (DialogBody element : spec.body()) {
            if (element instanceof DialogBody.PlainMessage message)
                messages.sendRaw(player, "<gray>" + message.contents(), Placeholder.empty());
            else if (element instanceof DialogBody.Item item && item.description() != null)
                messages.sendRaw(player, "<gray>- " + item.description(), Placeholder.empty());
        }
    }

    private static String firstCallbackId(DialogSpec spec) {
        for (DialogButton button : spec.buttons()) {
            String id = callbackId(button);
            if (id != null)
                return id;
        }
        return null;
    }

    private static String callbackId(DialogButton button) {
        return button != null && button.action() instanceof DialogAction.Callback callback ? callback.buttonId() : null;
    }

    /**
     * Button labels are MiniMessage; nesting them inside a click tag would produce mismatched markup, so only the plain
     * text is kept.
     */
    private static String stripTags(String miniMessage) {
        return miniMessage.replaceAll("<[^>]*>", "").trim();
    }
}
