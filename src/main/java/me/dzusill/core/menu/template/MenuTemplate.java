package me.dzusill.core.menu.template;

import me.dzusill.core.menu.Menu;

/**
 * A reusable menu layout applied to a {@link Menu} before its own content is placed. Templates
 * exist so common scaffolding (borders, fillers, fixed decorations) is defined once and shared,
 * rather than re-implemented in every menu. Because a template is just a strategy over the menu's
 * public placement API, both code-based and YAML-driven templates implement the same contract.
 */
public interface MenuTemplate {

    /**
     * Applies the layout to the given menu.
     */
    void apply(Menu menu);
}
