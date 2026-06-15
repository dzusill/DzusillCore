# Command Examples

## Example 1: /heal — leaf command with optional argument

A simple player-only command that heals the sender or a target player.

```java
@CommandMeta(
    name = "heal",
    permission = "core.heal",
    playerOnly = true,
    description = "Heal yourself or another player"
)
public final class HealCommand extends CoreCommand {

    public HealCommand() {
        super();
        optionalArg("target", new OnlinePlayerArgument());
    }

    @Override
    public void run(CommandContext context, Arguments args) throws CommandException {
        Player target = args.getOr("target", context.player());
        double maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        target.setHealth(maxHealth);
        target.setFoodLevel(20);

        target.sendMessage(ColorUtils.parse("<green>You have been healed."));
        if (!target.equals(context.sender())) {
            context.sender().sendMessage(
                    ColorUtils.parse("<green>Healed <yellow>" + target.getName() + "</yellow>."));
        }
    }
}
```

**Usage**: `/heal` or `/heal <player>`
**Tab-complete**: suggests online player names on the first argument.

---

## Example 2: /core — router with subcommand

A parent command that routes to subcommands and prints usage when called alone.

```java
@CommandMeta(
    name = "core",
    permission = "core.admin",
    description = "DzusillCore administration"
)
public final class CoreAdminCommand extends CoreCommand {

    public CoreAdminCommand(ConfigManager configs, MessageService messages) {
        super();
        child(new ReloadSubCommand(configs, messages));
    }

    @Override
    public void run(CommandContext context, Arguments args) {
        // Bare /core → print usage
        context.reply(Messages.INVALID_USAGE,
                Placeholder.of("usage", "/" + context.label() + " " + usage()));
    }

    @CommandMeta(name = "reload", permission = "core.reload", description = "Reload configuration")
    private static final class ReloadSubCommand extends SubCommand {

        private final ConfigManager configs;
        private final MessageService messages;

        private ReloadSubCommand(ConfigManager configs, MessageService messages) {
            super();
            this.configs = configs;
            this.messages = messages;
        }

        @Override
        public void run(CommandContext context, Arguments args) {
            try {
                configs.reload();
                messages.reload();
                context.reply(Messages.RELOAD_SUCCESS);
            } catch (Exception ex) {
                context.reply(Messages.RELOAD_FAILED);
            }
        }
    }
}
```

**Usage**: `/core reload`
**Tab-complete**: typing `/core ` suggests `reload`; `core.reload` permission hides it if the sender lacks it.

---

## Example 3: Command with multiple typed arguments

```java
@CommandMeta(name = "give", permission = "myplugin.give", description = "Give a player items")
public final class GiveCommand extends CoreCommand {

    public GiveCommand() {
        super();
        arg("player", new OnlinePlayerArgument());
        arg("material", new MaterialArgument());
        optionalArg("amount", new IntArgument(1, 64));
    }

    @Override
    public void run(CommandContext context, Arguments args) throws CommandException {
        Player target   = args.get("player");
        Material mat    = args.get("material");
        int amount      = args.getOr("amount", 1);

        target.getInventory().addItem(new ItemStack(mat, amount));
        context.reply("give-success",
                Placeholder.of("player", target.getName())
                           .and("amount", amount)
                           .and("item", mat.name()));
    }
}
```

**Usage**: `/give <player> <material> [amount]`
**Tab-complete**: players → materials → nothing (numeric).

---

## Registering all examples

```java
// In CommandModule.onEnable():
CommandRegistry commands = service(CommandRegistry.class);
commands.register(new HealCommand());
commands.register(new CoreAdminCommand(
        service(ConfigManager.class),
        service(MessageService.class)));
commands.register(new GiveCommand());
provide(CommandRegistry.class, commands);
```
