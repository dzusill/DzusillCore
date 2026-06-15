# Arguments

The argument system turns raw command tokens into typed values and provides automatic tab-completion — without any manual `onTabComplete` implementation.

## Declaring arguments

Arguments are declared in the constructor of a `SubCommand` or `CoreCommand`:

```java
public MyCommand() {
    super("mycommand");
    arg("target", new OnlinePlayerArgument());          // required
    arg("amount", new IntArgument(1, 1000));             // required, range 1–1000
    optionalArg("reason", new StringArgument());         // optional
}
```

Usage string (auto-generated): `<target> <amount> [reason]`

- **Required** (`arg`) — missing token sends the invalid-usage message.
- **Optional** (`optionalArg`) — missing token leaves the argument absent; use `args.getOr()` with a default.

## Reading arguments in run()

```java
@Override
public void run(CommandContext context, Arguments args) throws CommandException {
    Player target = args.get("target");           // typed, throws if absent
    int amount    = args.getOr("amount", 0);     // typed with default
    String reason = args.getOr("reason", "No reason");
}
```

## Built-in ArgumentType implementations

| Class | Parsed type | Tab-completion |
|---|---|---|
| `StringArgument` | `String` | Fixed choices (optional) or nothing |
| `IntArgument` | `Integer` | None (numeric) |
| `OnlinePlayerArgument` | `Player` | Online player names |
| `PlayerArgument` | `OfflinePlayer` | Online player names |
| `EnumArgument<E>` | `E extends Enum<E>` | All enum constant names |
| `MaterialArgument` | `Material` | All `Material` names |

### StringArgument

```java
// Any string
new StringArgument()

// Fixed choices — tab-completion and validation
new StringArgument("kill", "teleport", "fly")
```

### IntArgument

```java
new IntArgument()           // any integer
new IntArgument(1, 100)     // clamped to 1–100
```

### EnumArgument

```java
new EnumArgument<>(GameMode.class)
// Parses "survival" → GameMode.SURVIVAL (case-insensitive)
```

### OnlinePlayerArgument vs PlayerArgument

| | `OnlinePlayerArgument` | `PlayerArgument` |
|---|---|---|
| Resolves to | `Player` (online) | `OfflinePlayer` |
| Fails if offline | Yes | No |
| Use when | You need to interact live | You just need the UUID/name |

## Custom ArgumentType

Implement `ArgumentType<T>` to create your own:

```java
public final class WorldArgument implements ArgumentType<World> {

    @Override
    public World parse(CommandContext context, String raw) throws CommandException {
        World world = Bukkit.getWorld(raw);
        if (world == null) {
            throw new CommandException("world-not-found", Placeholder.of("name", raw));
        }
        return world;
    }

    @Override
    public List<String> suggest(CommandContext context, String token) {
        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .collect(Collectors.toList());
    }
}
```

Then use it like any built-in:

```java
arg("world", new WorldArgument())
```

## Tab-completion

Tab-completion is fully automatic. `ArgumentParser.suggest()` identifies which argument position the player is currently typing and delegates to that `ArgumentType.suggest()`. Router commands suggest their child names. Permission-filtered children are hidden from suggestions.

No `onTabComplete` override is ever needed.
