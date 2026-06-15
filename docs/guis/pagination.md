# Paginated Menus

`PaginatedMenu` extends `Menu` to display a variable-length list of items across multiple pages, with automatic navigation buttons. Subclasses only supply the full content list.

## How it works

- The bottom row is reserved for navigation (previous, close, next).
- All rows above the bottom row hold content items.
- Pages are calculated automatically from the content size and slots available.
- `refresh()` is called internally when a navigation button is clicked to rebuild the current page.

## Creating a paginated menu

```java
public final class PlayerListMenu extends PaginatedMenu {

    private final List<Player> players;

    public PlayerListMenu(CorePlugin plugin, PlayerMenuContext context, List<Player> players) {
        super(plugin, context);
        this.players = players;
    }

    @Override
    public Component title() {
        return ColorUtils.parse("<aqua>Online Players — Page " + (page + 1));
    }

    @Override
    public int size() {
        return 54;   // 6 rows: 5 content rows + 1 navigation row
    }

    @Override
    protected List<MenuItem> content() {
        List<MenuItem> items = new ArrayList<>();
        for (Player p : players) {
            items.add(MenuItem.of(
                    new ItemBuilder(Material.PLAYER_HEAD)
                            .skull(/* base64 texture */)
                            .name("<yellow>" + p.getName())
                            .lore("<gray>Click to select")
                            .build(),
                    event -> {
                        context.set("selected", p);
                        back();
                    }));
        }
        return items;
    }
}
```

## Content slots

By default all slots except the bottom navigation row are used for content. Override `contentSlots()` to change this:

```java
@Override
protected int[] contentSlots() {
    // Use only the middle three rows (slots 9–35, skipping the top and bottom rows)
    int[] slots = new int[27];
    for (int i = 0; i < 27; i++) slots[i] = 9 + i;
    return slots;
}
```

## Navigation buttons

The default buttons can be overridden:

```java
@Override
protected ItemStack previousButton() {
    return new ItemBuilder(Material.SPECTRAL_ARROW)
            .name("<yellow>← Previous")
            .build();
}

@Override
protected ItemStack nextButton() {
    return new ItemBuilder(Material.SPECTRAL_ARROW)
            .name("<yellow>Next →")
            .build();
}

@Override
protected ItemStack closeButton() {
    return new ItemBuilder(Material.BARRIER)
            .name("<red>Close")
            .build();
}
```

## Decorating additional slots per page

Use `decoratePage()` to add per-page decoration after content and navigation are placed:

```java
@Override
protected void decoratePage() {
    // e.g. page counter item in slot 49
    set(49, MenuItem.display(
            new ItemBuilder(Material.PAPER)
                    .name("<gray>Page " + (page + 1))
                    .build()));
}
```

## Page counter

The current page index is stored in the `protected int page` field (0-based). Access it inside `title()` or `decoratePage()`:

```java
@Override
public Component title() {
    return ColorUtils.parse("<aqua>Shop — Page " + (page + 1));
}
```
