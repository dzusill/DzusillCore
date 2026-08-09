package me.dzusill.core.example.command;

import java.util.List;

import org.bukkit.entity.Player;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.command.CoreCommand;
import me.dzusill.core.command.argument.Arguments;
import me.dzusill.core.command.meta.CommandMeta;
import me.dzusill.core.dialog.AfterAction;
import me.dzusill.core.dialog.DialogButton;
import me.dzusill.core.dialog.DialogInput;
import me.dzusill.core.dialog.DialogKind;
import me.dzusill.core.dialog.DialogService;
import me.dzusill.core.dialog.DialogSpec;
import me.dzusill.core.util.ColorUtils;

/**
 * Exercises every part of the dialog subsystem end-to-end: {@code /coredialog}.
 *
 * <p>
 * Worth keeping as an example because it is also the acceptance test. Rendering, input round-trip and the fallback all
 * need a real client to verify, and this reduces that to one command a human can run. Each subcommand reports what it
 * got back in chat, so a wrong value is visible immediately rather than needing a debugger.
 * </p>
 *
 * <ul>
 * <li>{@code /coredialog confirm} - the guaranteed wrapper; works identically with or without a rendering backend</li>
 * <li>{@code /coredialog input} - single text field, echoing what came back</li>
 * <li>{@code /coredialog full} - every input type at once, which is where a translation bug shows up</li>
 * <li>{@code /coredialog status} - whether native rendering is active for you specifically</li>
 * </ul>
 */
@CommandMeta(name = "coredialog", permission = "core.dialog.demo", playerOnly = true, description = "Demonstrate and verify the dialog subsystem")
public final class DialogDemoCommand extends CoreCommand {

    private final DialogService dialogs;

    public DialogDemoCommand(DialogService dialogs) {
        super();
        this.dialogs = dialogs;
        arg("mode", new me.dzusill.core.command.argument.types.StringArgument());
    }

    @Override
    public void run(CommandContext context, Arguments args) throws CommandException {
        Player player = context.player();
        String mode = args.<String>get("mode").toLowerCase(java.util.Locale.ROOT);

        switch (mode) {
            case "status" -> reply(context, player, "<gray>native available: <yellow>" + dialogs.available()
                    + "<gray>, supported for you: <yellow>" + dialogs.supports(player));

            case "confirm" ->
                dialogs.confirm(player, "<gold>Delete that warp?", "<gray>This cannot be undone.", "<green>Delete",
                        "<red>Keep", accepted -> reply(context, player, "<yellow>You chose: <white>" + accepted));

            case "input" ->
                dialogs.input(player, "<gold>Rename warp", "<gray>New name", "spawn", 16, value -> reply(context,
                        player, value.isEmpty() ? "<red>Cancelled." : "<green>You typed: <white>" + value));

            case "full" -> showEverything(context, player);

            default -> reply(context, player, "<red>Usage: /coredialog <status|confirm|input|full>");
        }
    }

    /**
     * One dialog carrying all four input types. If the translator mishandles a type, it shows up here as a missing or
     * wrong value rather than as a silent render failure.
     */
    private void showEverything(CommandContext context, Player player) {
        DialogSpec spec = DialogSpec
                .builder(
                        DialogKind.MultiAction.of(List.of(DialogButton.callback("<green>Submit", "submit"),
                                DialogButton.of("<red>Cancel", new me.dzusill.core.dialog.DialogAction.None()))),
                        "<gradient:#ff0000:#ffaa00>Everything at once</gradient>")
                .text("<gray>Fill these in and press Submit.")
                .body(me.dzusill.core.dialog.DialogBody.Item.of("minecraft:diamond", 3, "<aqua>Three diamonds"))
                .input(DialogInput.Text.of("nick", "Nickname", 16))
                .input(DialogInput.Text.multiline("notes", "Notes", 200, 60))
                .input(DialogInput.Bool.of("gift", "Send as a gift", true))
                .input(DialogInput.SingleOption.of("reason", "Reason",
                        List.of(new DialogInput.SingleOption.Option("spam", "<red>Spam", true),
                                DialogInput.SingleOption.Option.of("rude", "<yellow>Rudeness"))))
                .input(DialogInput.NumberRange.ofInts("amount", "Amount", 1, 64, 8)).afterAction(AfterAction.CLOSE)
                .build();

        boolean shown = dialogs.show(player, spec,
                (buttonId, values) -> reply(context, player,
                        "<green>button=<white>" + buttonId + " <green>nick=<white>" + values.textOr("nick", "-")
                                + " <green>notes=<white>" + values.textOr("notes", "-") + " <green>gift=<white>"
                                + values.flagOr("gift", false) + " <green>reason=<white>" + values.textOr("reason", "-")
                                + " <green>amount=<white>" + values.intOr("amount", -1)))
                .isPresent();

        if (!shown)
            reply(context, player, "<red>Could not show that dialog, and the fallback could not represent it either. "
                    + "That is expected without a rendering backend: chat cannot draw a slider.");
    }

    private void reply(CommandContext context, Player player, String miniMessage) {
        context.messages().sendComponent(player, ColorUtils.parse(miniMessage));
    }
}
