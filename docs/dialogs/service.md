# DialogService

```java
public interface DialogService extends Service {
    boolean available();
    boolean supports(Player player);

    Optional<DialogHandle> show(Player player, DialogSpec spec, DialogHandler handler);
    void close(Player player);

    void notice (Player p, String title, String body, String buttonLabel, Runnable onClose);
    void confirm(Player p, String title, String body, String yes, String no, Consumer<Boolean> result);
    void input  (Player p, String title, String label, String initial, int maxLength, Consumer<String> result);
}
```

Every text parameter is **MiniMessage**, matching `MessageService` and your `messages.yml`, so strings move between chat and dialogs unchanged.

## available() vs supports(player)

`available()` answers "could anyone here see a dialog right now" — server new enough, backend installed, force-fallback off. `supports(player)` additionally checks *that player's client*, which may be older than the server behind a translating proxy.

Use `supports` when deciding whether to *offer* something a fallback cannot represent:

```java
// A multi-input form has no chat equivalent, so only show the button that opens one
// where it will actually render. A button that silently does nothing is worse than no button.
if (dialogs.supports(player)) {
    button(16).icon(...).onClick(e -> editEverythingAtOnce(player)).add();
}
```

## Building a spec

```java
DialogSpec spec = DialogSpec.builder(
            DialogKind.MultiAction.of(List.of(DialogButton.callback("<green>Buy", "buy"))),
            "<gradient:#ff0000:#ffaa00>Shop</gradient>")
        .body(DialogBody.Item.of("minecraft:diamond", 1, "<aqua>A diamond"))
        .text("<gray>Pick an amount.")
        .input(DialogInput.NumberRange.ofInts("amount", "Amount", 1, 64, 1))
        .build();

dialogs.show(player, spec, (buttonId, values) -> {
    int amount = values.intOr("amount", 1);   // sliders submit floats — intOr rounds
});
```

### Kinds

| Kind | Buttons |
|---|---|
| `Notice` | one dismiss button |
| `Confirmation` | `yes` + `no` (both required by vanilla; `no` is also the escape action) |
| `MultiAction` | a grid, at least one button, optional exit |
| `DialogList` | links to registered dialogs — cannot be shown ad-hoc |
| `ServerLinks` | the server-links screen |

### Inputs

| Input | Submits | Read with |
|---|---|---|
| `Text` | string | `text(key)` |
| `Bool` | byte `1b`/`0b` | `flag(key)` |
| `SingleOption` | the chosen option's id | `text(key)` |
| `NumberRange` | **float** | `number(key)` / `intOr(key, …)` |

**Read by declared type.** A checkbox asked for text yields nothing — the accessors return empty both for "absent" and "present but a different type". An untouched control with no initial value may not be in the payload at all.

Input keys must match `[A-Za-z0-9_]+`; vanilla rejects anything else outright. Keys starting with `dz_` are reserved. If your keys come from config, map them to positional names (`q0`, `q1`, …) — an author-supplied id will eventually contain a dash.

### Actions

`Callback` is the only one that reports back to the server. The rest are handled entirely by the client.

> `RunCommand` and `TemplateCommand` run **as the player**, with the player's permissions. Never use them for a privileged action — a player can trigger the same command by other means. Route anything that needs elevated rights through `Callback` and do the work server-side.

## Validation

`DialogSpec` rejects at construction what vanilla would reject at render time — a missing title, an empty `MultiAction`, an illegal input key, duplicate keys, `afterAction = NONE` without `pause = false`. That turns a server-side parse failure your plugin cannot see or attribute into an `IllegalArgumentException` with a stack trace pointing at your code.

Vanilla silently *ignores* unknown fields, so a typo there is never reported. That is why the spec is a closed set of records rather than a map.

## Testing

`RecordingDialogService` ships in main sources — core publishes no test-jar, so a classifier dependency in every consumer pom was the alternative. Inject it and drive both halves of a flow with no server, no client and no dialog API:

```java
RecordingDialogService dialogs = new RecordingDialogService();
warps.requestDelete(player, "spawn");

assertEquals("Delete warp?", dialogs.last().spec().title());
dialogs.submitLast("yes", Map.of());
assertFalse(warps.exists("spawn"));

dialogs.setAvailable(false);   // drive the fallback branch instead
```
