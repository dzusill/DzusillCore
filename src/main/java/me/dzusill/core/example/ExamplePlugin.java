package me.dzusill.core.example;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.module.CommandModule;
import me.dzusill.core.example.module.DatabaseModule;
import me.dzusill.core.example.module.FoundationModule;
import me.dzusill.core.example.module.IntegrationModule;
import me.dzusill.core.example.module.MenuModule;
import me.dzusill.core.module.CoreModule;
import me.dzusill.core.nms.NmsModule;

/**
 * Reference plugin wired entirely from framework building blocks. It exists to document the intended usage end-to-end;
 * a real plugin replaces this class (and the {@code example} package) with its own {@link CorePlugin} subclass and
 * modules.
 *
 * <p>
 * The module order is the startup order: foundation services first, then the NMS adapter (so any later module can
 * resolve it), then integrations, the menu subsystem, and finally the commands that depend on all of the above.
 * </p>
 */
public class ExamplePlugin extends CorePlugin {

    @Override
    protected CoreModule[] modules() {
        return new CoreModule[]{new FoundationModule(this), new NmsModule(this), new DatabaseModule(this),
                new IntegrationModule(this), new MenuModule(this), new CommandModule(this)};
    }

    @Override
    protected String[] banner() {
        return new String[]{"", "  DzusillCore " + getDescription().getVersion(),
                "  Example plugin running on the framework", ""};
    }
}
