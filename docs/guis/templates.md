# Menu Templates

Templates define reusable layout layers applied to a `Menu` before `decorate()` runs. They handle decoration (borders, fillers, fixed buttons) so individual menus only declare their interactive content.

## MenuTemplate interface

```java
public interface MenuTemplate {
    void apply(Menu menu);
}
```

## AbstractMenuTemplate

Provides protected helpers for common patterns:

```java
public abstract class AbstractMenuTemplate implements MenuTemplate {
    protected void applyBorder(Menu menu, ItemStack filler) { ... }
    protected void applyFill(Menu menu, ItemStack filler) { ... }
}
```

- `applyBorder` — fills the outer ring of slots (top row, bottom row, left and right edges).
- `applyFill` — fills every slot.

## Built-in templates (Templates factory)

```java
// Border with default gray glass pane filler
Templates.bordered()

// Border with custom filler
Templates.bordered(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build())

// Fill all slots with default filler (content overwrites after)
Templates.filled()

// Fill with custom filler
Templates.filled(myFillerItem)
```

## Using a template in a menu

Override `template()`:

```java
@Override
protected MenuTemplate template() {
    return Templates.bordered();
}
```

The template is applied first; `decorate()` runs after, so content overwrites template items in any slot.

## Custom templates

Extend `AbstractMenuTemplate` for reusable layouts:

```java
public final class ShopTemplate extends AbstractMenuTemplate {

    @Override
    public void apply(Menu menu) {
        applyBorder(menu, new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());

        // Fixed close button in the bottom center
        menu.set(49, MenuItem.of(
                new ItemBuilder(Material.BARRIER).name("<red>Close").build(),
                event -> menu.back()));
    }
}
```

Apply it: `return new ShopTemplate();`

## YAML-driven templates

Define templates in `menus.yml` without recompiling:

```yaml
my-template:
  filler:
    material: GRAY_STAINED_GLASS_PANE
    name: " "
  border: true
  items:
    title-icon:
      material: NETHER_STAR
      name: "<aqua><bold>My Menu"
      lore:
        - "<gray>A config-driven layout."
      slots: [4]
```

Load and apply with `YamlMenuTemplate`:

```java
Config menus = service(ConfigManager.class).load("menus.yml");
MenuTemplate template = new YamlMenuTemplate(menus, "my-template");
```

Then return it from `template()`:

```java
@Override
protected MenuTemplate template() {
    return new YamlMenuTemplate(menusConfig, "my-template");
}
```

### YAML item format

| Key | Description |
|---|---|
| `material` | Bukkit `Material` name (e.g. `DIAMOND`) |
| `name` | MiniMessage display name |
| `lore` | List of MiniMessage lore lines |
| `slots` | List of slot indices the item occupies |

`border: true` uses `applyBorder`; `border: false` uses `applyFill`.
