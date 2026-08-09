# Dialogs — Overview

Minecraft 1.21.6 added **server-driven dialogs**: real client-rendered screens with text fields, checkboxes, sliders and option pickers, instead of items arranged in a chest.

`DialogService` exposes them to your plugin — and, more importantly, makes them safe to call on a server that cannot render them at all.

## The one thing to understand

**Call sites never branch on version.** There is no `if (server.isNewEnough())` anywhere in your code. You ask for a confirmation; the player gets a native dialog, or a clickable yes/no in chat, or your own chest menu — whichever the server and that particular client can manage. Your callback runs either way.

That matters because a Minecraft server usually cannot assume its players are on the same version it is. A translating proxy lets a 1.21.4 client join a 1.21.11 server, and that client cannot draw a dialog. `DialogService` checks per player, not just per server.

## Two tiers, different guarantees

| | Guarantee |
|---|---|
| `notice` / `confirm` / `input` | **The callback always runs exactly once.** On any server version, with or without a rendering backend. Cancelling yields `false` / `""`. |
| `show(DialogSpec, …)` | **Best effort.** Returns an empty `Optional` when the dialog could be neither rendered nor mapped onto a fallback, and the caller must keep its own path for that case. |

Prefer the wrappers. They are a straight swap for an existing chat prompt or confirm menu:

```java
dialogs.confirm(player, "<red>Delete that warp?", "<gray>This cannot be undone.",
        accepted -> { if (accepted) warps.delete(name); });
```

Reach for `show` only when you need several inputs, an item icon, or a slider — and handle the empty case.

## Rendering lives in another plugin

DzusillCore relocates `net.kyori`, and every typed dialog builder in the server API takes a native `net.kyori` component. Core calling one would be rewritten by the shade relocator and fail at runtime. So core defines the vocabulary and the **DDialogs** plugin does the drawing, registered through Bukkit's `ServicesManager`.

Practically:

- **Without DDialogs installed** — everything still works, through the fallbacks.
- **With DDialogs installed** — the same call sites start rendering real dialogs. No code change.

Everything crossing that boundary is a plain record of `String`, primitives and `java.util` collections. No component, no NBT.

> **A plugin that shades DzusillCore instead of depending on a separate core jar carries its own copy of these classes.** `ServicesManager` matches on class identity, so DDialogs' registration can never be found from such a plugin, and it will always use the fallback. That is a deployment property, not a bug.

## Setup

Add `DialogModule` after your menu module and before any module whose commands open dialogs:

```java
protected CoreModule[] modules() {
    return new CoreModule[]{ new FoundationModule(this), new MenuModule(this),
                             new DialogModule(this), new CommandModule(this) };
}
```

Then resolve it like any other service:

```java
DialogService dialogs = plugin.services().get(DialogService.class);
```

`DialogModule` also publishes a [`PromptService`](../prompts.md) if nothing else has, since the chat fallback needs one.

## Trust

Values in a callback arrive in a **client-initiated packet**. A modified client can send one at any time, with any contents.

Core validates the correlation — tokens are 128-bit random, bound to the player they were issued to, single-use and time-limited — but it cannot validate *meaning*. Re-check length, charset and range at your call site, and never derive authority from a submitted value. A slider labelled 1–64 is a display, not a constraint.

## Pages

* [DialogService](service.md) — the API in full
* [Fallbacks](fallback.md) — what happens when a dialog cannot be drawn, and how to supply your own
* [Examples](examples.md) — recipes
