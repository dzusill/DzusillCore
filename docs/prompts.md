# Chat prompts

`PromptService` asks a player for a line of text.

```java
prompts.prompt(player, "<yellow>Enter a name", value -> {
    if (value.isEmpty()) { reopenMenu(); return; }
    rename(value);
});
```

## The contract

**`onResult` runs exactly once, always** — with `""` when the player cancels, times out, or disconnects.

That is the whole point. Callers reopen their menu unconditionally in the callback: no separate cancel path, no way to leave a player staring at a closed inventory. It replaces a class that had been copy-pasted across the ecosystem, where four copies dropped the callback entirely on cancel — so the caller's menu simply never came back — and two had no timeout at all, capturing the player's chat indefinitely.

Every ending funnels through one guarded resolve, so submission, timeout, quit, the cancel keyword and an explicit cancel cannot race each other into a double call. A monotonic prompt id stops a stale timeout resolving a prompt that has since been replaced.

## Options

```java
prompts.prompt(player, PromptOptions.of("<yellow>Enter a name")
        .withTimeout(600L)      // ticks; default 1200 (60s)
        .withMaxLength(32),     // default 200 — over-long input is truncated, not rejected
        this::rename);
```

Starting a second prompt for the same player cancels the first, resolving its callback.

Commands are deliberately not intercepted, so a prompted player can still type `/spawn` to get unstuck.

## Setup

`DialogModule` publishes a `PromptService` if nothing else has, since the chat fallback needs one. To use it standalone, provide `ChatPromptService` yourself.

## Which chat event

Both the modern component-based event and the legacy string one are registered, with a short window that hides the twin delivery on servers firing both.

Picking one is not reliably possible up front: Paper fires the modern event and bridges the legacy one, plain Spigot fires only the legacy one, and some test environments supply the modern class without ever firing it. Missing the capture strands a prompted player, so listening for either is the safer trade. `kind()` reports what was installed (`paper+legacy`, `legacy`, …) and is logged at startup.

The modern path is entirely reflective: `AsyncChatEvent#message()` returns a native `net.kyori` component, and core's own `net.kyori` is relocated, so a direct call would hand back a component of the wrong class.
