package me.dzusill.core;

import me.dzusill.core.bootstrap.CommandModule;
import me.dzusill.core.bootstrap.DatabaseModule;
import me.dzusill.core.bootstrap.FoundationModule;
import me.dzusill.core.bootstrap.IntegrationModule;
import me.dzusill.core.bootstrap.MenuModule;
import me.dzusill.core.dialog.DialogModule;
import me.dzusill.core.module.CoreModule;
import me.dzusill.core.nms.NmsModule;

/**
 * The framework's own plugin.
 *
 * <p>
 * It is a library first: downstream plugins declare {@code depend: [DzusillCore]} and build their own
 * {@link CorePlugin} with their own modules. This class exists so the jar is a loadable plugin and so the shared
 * subsystems have somewhere to live.
 * </p>
 *
 * <p>
 * It deliberately registers <em>no gameplay commands of its own</em>. It used to ship a demo {@code /shop}, {@code
 * /heal} and {@code /coredialog} to document the framework end-to-end - which meant every server running the framework
 * handed its players an "Example Shop" selling a diamond, {@code /shop} being open to everyone by default.
 * Documentation belongs in the docs, not in the command map of a production server.
 * </p>
 *
 * <p>
 * The module order is the startup order: foundation services first, then the NMS adapter (so any later module can
 * resolve it), then the database, integrations, the menu and dialog subsystems, and finally commands, which depend on
 * all of the above.
 * </p>
 */
public class DzusillCorePlugin extends CorePlugin {

    @Override
    protected CoreModule[] modules() {
        return new CoreModule[]{new FoundationModule(this), new NmsModule(this), new DatabaseModule(this),
                new IntegrationModule(this), new MenuModule(this), new DialogModule(this), new CommandModule(this)};
    }

    @Override
    protected String[] banner() {
        return new String[]{"", "  DzusillCore " + getDescription().getVersion(), "  Framework ready", ""};
    }
}
