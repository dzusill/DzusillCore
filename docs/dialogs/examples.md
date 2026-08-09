# Dialogs — Examples

Each of these is a real migration from a plugin in the ecosystem.

## Guard a destructive action

WarpGUI deleted a warp on a `DOUBLE_CLICK` with no prompt and no undo — the whitelist went with it. One stray double-click was enough.

```java
services.dialogs.confirm(player,
        messagesConfig.text("DeleteWarp.confirmTitle"),
        messagesConfig.text("DeleteWarp.confirmBody").replace("{0}", warp.getName()),
        messagesConfig.text("DeleteWarp.confirmYes"),
        messagesConfig.text("DeleteWarp.confirmNo"),
        accepted -> { if (accepted) delete(); else reopen(); });
```

Because the wrapper is guaranteed, the guard exists on **every** server version — old clients get a clickable yes/no instead of no confirmation at all.

> Keep the strings in `messages.yml`, but note that a missing key resolves to the key name, shipping a button labelled `DeleteWarp.confirmYes`. A test that reads the bundled resource and fails on an absent key costs ten lines and catches the whole class of mistake.

## Replace a chat prompt

```java
dialogs.input(player, "<gold>Rename home", "<gray>New name", home.getName(), 16, value -> {
    if (value.isEmpty()) { reopen(); return; }   // cancelled, timed out, or disconnected
    homes.rename(home, value);
});
```

Falls back to a chat prompt automatically. `""` means "no value" for every reason, so there is one path to write.

## A quantity slider

DRotatingShop replaced six stepper buttons (+1, +half, +stack and their negatives), each costing a click-and-redraw round trip.

```java
DialogSpec spec = DialogSpec.builder(
            DialogKind.MultiAction.of(List.of(DialogButton.callback("<green><b>Buy</b>", "buy"),
                                              DialogButton.of("<red>Cancel", new DialogAction.None()))),
            item.displayName())
        .body(DialogBody.Item.of(itemId, 1, item.displayName()))
        .text("<gray>Price each: <white>" + economy.format(unitPrice))
        .input(DialogInput.NumberRange.ofInts("amount", "Amount", 1, max, 1))
        .build();

boolean shown = dialogs.show(player, spec, (buttonId, values) -> {
    if (!"buy".equals(buttonId)) return;

    // Recomputed NOW, not when the screen was drawn — stock and balance move while a screen sits open.
    int allowed = purchases.maxBuyable(item, player);
    int amount  = Quantities.clamp(values.intOr("amount", 1), 1, allowed);
    purchases.attempt(player, item, amount);     // re-checks stock, balance and limits anyway
}).isPresent();

if (!shown) openChestQuantityMenu(player, item);  // chat cannot draw a slider
```

**The slider bound is a display, not a constraint.** The submitted value arrives in a client-controlled packet. Clamp it against a freshly computed limit and let your service re-validate regardless.

## Collect a whole form at once

dPhalanx asked one question per chat message. A five-question category meant five round trips, with no way to see prior answers, no way to go back, and total loss on timeout.

```java
DialogSpec.Builder builder = DialogSpec.builder(
        DialogKind.MultiAction.of(List.of(DialogButton.callback("<green><b>Submit</b>", "submit"),
                                          DialogButton.of("<red>Cancel", new DialogAction.None()))),
        category.name());

for (int i = 0; i < questions.size(); i++) {
    Question q = questions.get(i);
    // Positional keys: vanilla rejects an input name outside [A-Za-z0-9_], and question ids are author-supplied.
    builder.input("PARAGRAPH".equalsIgnoreCase(q.style())
            ? DialogInput.Text.multiline("q" + i, q.question(), 1000, 100)
            : DialogInput.Text.of("q" + i, q.question(), 200));
}
```

Multiline fields need an explicit `maxLength` — the vanilla default is **32 characters**, which truncates almost any description.

Keep the sequential prompt as the fallback; a multi-input form is declined by chat, so `show` returns empty on older clients.

## Offer something only where it renders

```java
// A multi-input form has no chat equivalent. Showing a button that silently does
// nothing is worse than not showing it.
if (dialogs.supports(player)) {
    button(16).icon(...).onClick(e -> editEverythingAtOnce(player)).add();
}
```

## Capture state at click time, not at callback time

```java
// The menu stays interactive while a chat confirmation is pending, so pendingAmount
// can move between asking and answering — and the player would be charged for
// something they never saw.
private void confirmPurchase(Player player, int amount) {
    dialogs.confirm(player, "<aqua>Buy " + amount + " ticket(s)?", …,
            accepted -> { if (accepted) lottery.buyTickets(player, amount); });
}
```
