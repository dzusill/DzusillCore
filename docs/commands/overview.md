# Commands — Overview

The command framework replaces Bukkit's boilerplate (`CommandExecutor`, `TabCompleter`, manual permission checks, argument parsing) with a declarative, tree-structured API.

## Architecture

```
CoreCommand (root, registered with CommandMap)
└── SubCommand (child nodes — routers or leaves)
    └── SubCommand (nested children, any depth)
```

| Class | Role |
|---|---|
| `CoreCommand` | Root of a command tree; implements Bukkit's `CommandExecutor` + `TabCompleter` |
| `SubCommand` | Any node in the tree; handles permission, player-only check, argument parsing, routing |
| `@CommandMeta` | Annotation for declaring name, permission, description, aliases declaratively |
| `CommandContext` | Per-invocation context: sender, label, raw args, helpers |
| `CommandException` | Thrown to send an error message without crashing the command |
| `CommandRegistry` | Service that registers `CoreCommand`s with the server's `CommandMap` at runtime |

## Creating a command

### Option 1 — annotation-driven (recommended)

```java
@CommandMeta(
    name = "heal",
    permission = "myplugin.heal",
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
        target.setHealth(target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        target.sendMessage(ColorUtils.parse("<green>Healed!"));
    }
}
```

### Option 2 — programmatic

```java
public final class KickCommand extends CoreCommand {

    public KickCommand() {
        super("kick");
        permission("myplugin.kick");
        description("Kick a player");
        arg("target", new OnlinePlayerArgument());
        optionalArg("reason", new StringArgument());
    }

    @Override
    public void run(CommandContext context, Arguments args) throws CommandException {
        Player target = args.get("target");
        String reason = args.getOr("reason", "No reason given.");
        target.kickPlayer(reason);
    }
}
```

## Registering commands

Commands are **never** declared in `plugin.yml`. Register them through `CommandRegistry`:

```java
CommandRegistry commands = service(CommandRegistry.class);
commands.register(new HealCommand());
commands.register(new CoreAdminCommand(configs, messages));
```

`CommandRegistry` injects the plugin and `MessageService` into each `CoreCommand` before registration with the server's live `CommandMap`.

## Router commands (parent nodes)

A node with children acts as a router: it receives the first token, finds the matching child, and delegates. When invoked bare (no subcommand given), `run()` is called — typically to print help:

```java
@CommandMeta(name = "core", permission = "core.admin", description = "Admin commands")
public final class CoreAdminCommand extends CoreCommand {

    public CoreAdminCommand(ConfigManager configs, MessageService messages) {
        super();
        child(new ReloadSubCommand(configs, messages));
    }

    @Override
    public void run(CommandContext context, Arguments args) {
        context.reply(Messages.INVALID_USAGE,
                Placeholder.of("usage", "/" + context.label() + " " + usage()));
    }
}
```

`usage()` on a router returns `<reload|...>` automatically from the registered children.

## CommandContext helpers

```java
context.sender()          // the CommandSender
context.player()          // cast to Player (null if not a player)
context.isPlayer()        // boolean check
context.label()           // the command label used (e.g. "heal")
context.args()            // raw String[]
context.arg(index)        // raw arg at position
context.size()            // number of raw args
context.reply(key)        // send a messages.yml key to the sender
context.reply(key, placeholder)
```

## CommandException

Throw a `CommandException` to send an error message and abort the command cleanly:

```java
if (someConditionFailed) {
    throw new CommandException(Messages.PLAYER_NOT_FOUND,
            Placeholder.of("name", input));
}
```

The exception is caught by `CoreCommand.onCommand()` which sends the message to the sender without printing a stack trace.
