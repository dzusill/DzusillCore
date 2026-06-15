# Message Service

`MessageService` centralizes all user-facing text. It reads from `messages.yml`, parses every string through Adventure's MiniMessage, handles the `<prefix>` token, and applies `Placeholder` substitutions — so call sites never deal with raw color codes or string concatenation.

## messages.yml

```yaml
prefix: "<gray>[<aqua>Core</aqua>]</gray> "

no-permission:   "<prefix><red>You do not have permission to do that."
players-only:    "<prefix><red>Only players can use this command."
reload-success:  "<prefix><green>Configuration reloaded."
invalid-usage:   "<prefix><yellow>Usage: <gray>%usage%"
player-not-found: "<prefix><red>Player '%name%' was not found."
```

- `<prefix>` is replaced with the value of the `prefix` key before MiniMessage parsing.
- Placeholders use either `%key%` or `{key}` syntax.

## Sending a message

```java
MessageService messages = service(MessageService.class);

// No placeholders
messages.send(player, Messages.NO_PERMISSION);

// With a named placeholder
messages.send(player, Messages.PLAYER_NOT_FOUND,
        Placeholder.of("name", targetName));

// With multiple placeholders
messages.send(player, Messages.INVALID_USAGE,
        Placeholder.of("usage", "/heal [target]").and("extra", "info"));
```

## Getting a Component

Sometimes you need the `Component` rather than sending it directly:

```java
Component component = messages.get(Messages.RELOAD_SUCCESS);
// Or with placeholder:
Component component = messages.get(Messages.INVALID_USAGE,
        Placeholder.of("usage", "/heal [target]"));
```

## Ad-hoc messages (not in messages.yml)

```java
// Parse a MiniMessage string directly
messages.sendRaw(player, "<green>Welcome, <yellow>%name%<green>!",
        Placeholder.of("name", player.getName()));

// Or get a component
Component c = messages.component("<aqua>Hello!", Placeholder.empty());
```

## Messages constant class

`Messages` provides string constants for every key in the default `messages.yml`:

```java
public final class Messages {
    public static final String NO_PERMISSION    = "no-permission";
    public static final String PLAYERS_ONLY     = "players-only";
    public static final String RELOAD_SUCCESS   = "reload-success";
    public static final String INVALID_USAGE    = "invalid-usage";
    public static final String PLAYER_NOT_FOUND = "player-not-found";
    // ...
}
```

Add your own constants following the same pattern.

## Placeholder

`Placeholder` supports named and positional substitution:

```java
// Named
Placeholder.of("player", player.getName())
           .and("amount", coins);

// Positional ({0}, {1}, ...)
Placeholder.positional("Steve", 100);

// Empty (no substitution)
Placeholder.empty();
```

Both `%key%` and `{key}` forms are replaced, so you can use whichever convention you prefer in `messages.yml`.

## List messages

For multi-line messages (e.g. help text), use a YAML list:

```yaml
help:
  - "<aqua>/heal</aqua> <gray>- Heal yourself or another player"
  - "<aqua>/shop</aqua> <gray>- Open the shop"
```

`MessageService.send()` automatically detects lists and sends each line separately:

```java
messages.send(player, "help");
```
