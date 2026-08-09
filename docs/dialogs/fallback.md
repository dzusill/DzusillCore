# Fallbacks

A dialog cannot always be drawn: the server may predate 1.21.7, the player's client may be older than the server, DDialogs may not be installed, or an admin may have forced fallback on for testing. A fallback is what keeps your call site version-agnostic.

## The built-in chat fallback

Installed automatically by `DialogModule`.

| Spec | Becomes |
|---|---|
| `Notice`, no inputs | a chat message |
| `Confirmation`, no inputs | a clickable `[Yes] [No]` prompt |
| exactly one `Text` input | a chat prompt |
| **anything else** | **declined** |

Declining is deliberate. A fallback that claims a dialog **owns its handler** and must resolve it exactly once — so claiming a spec it cannot represent would strand the caller waiting on a callback that never fires. A slider, an option picker, or several inputs at once have no faithful chat equivalent, so `show` returns empty and you keep whatever you already had.

The yes/no buttons use `suggest_command`, not `run_command`: it pre-fills the chat box, which the chat capture then reads. That keeps the flow clickable without registering a command per dialog — and core cannot unregister commands, so per-dialog commands would leak on every reload.

## Supplying your own

If your plugin already has a good screen for something, use it. Pass a fallback to `DialogModule`:

```java
new DialogModule(this, new MenuConfirmFallback(this))
```

```java
public final class MenuConfirmFallback implements DialogFallback {
    @Override
    public boolean handle(Player player, DialogSpec spec, DialogHandler handler) {
        if (!spec.inputs().isEmpty() || !(spec.kind() instanceof DialogKind.Confirmation confirmation))
            return false;                       // decline what a chest menu cannot draw

        return menus.open(player, MenuKeys.CONFIRM, ctx -> {
            ctx.set(CTX_CONFIRM_ACTION, (Runnable) () -> handler.onSubmit("yes", DialogValues.empty()));
            ctx.set(CTX_CONFIRM_BACK,   (Runnable) () -> handler.onSubmit("no",  DialogValues.empty()));
        });
    }
}
```

Two rules:

1. **Claim only what you can honestly draw.** Over-claiming is the dangerous direction — under-claiming just falls through.
2. **Resolve the handler exactly once**, on every path including cancel.

Your fallback is tried first and the chat fallback catches the rest, so overriding confirmations cannot accidentally disable text prompts. That chaining is `DialogFallback#orElse`, wired for you by `DialogModule`.

Resolve collaborators (a menu registry, say) *when a dialog is actually served*, not in the constructor — the fallback is built while the module list is being assembled, before any module has run.

## Forcing it

```yaml
# config.yml
dialogs:
  force-fallback: true
```

Sends every dialog down the fallback path on a server that could render natively. Both paths are then testable without a second server or an old client — worth running before release, since the fallback is the path most players on mixed-version servers actually see.

## The prompt service underneath

The chat fallback is built on `PromptService`, whose contract is that **`onResult` runs exactly once, always** — `""` on cancel, timeout or disconnect. Callers can reopen their menu unconditionally in the callback, with no separate cancel path.

It replaces the per-plugin chat-prompt class that had been copied across the ecosystem. Four of those copies dropped the callback entirely on cancel, so the caller's menu never reopened; two had no timeout, leaving a player captured indefinitely. Every ending funnels through one guarded `resolve`, and a monotonic prompt id stops a stale timeout resolving a prompt that has since been replaced.

Chat capture listens for **both** the modern and legacy chat events, de-duplicating the twin delivery. Which event a server fires is not reliably detectable up front, and missing the capture strands a prompted player.
