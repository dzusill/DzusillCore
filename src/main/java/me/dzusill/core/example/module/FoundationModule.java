package me.dzusill.core.example.module;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.config.ConfigManager;
import me.dzusill.core.config.SettingsConfig;
import me.dzusill.core.event.ListenerRegistry;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.module.AbstractModule;
import me.dzusill.core.scheduler.SchedulerService;

/**
 * Foundational module: publishes the services every other module relies on (configuration, messages, scheduling,
 * listener registration). Enabled first so later modules can resolve these.
 */
public final class FoundationModule extends AbstractModule {

    public FoundationModule(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "Foundation";
    }

    @Override
    public void onEnable() {
        ConfigManager configs = new ConfigManager(plugin);
        configs.register(new SettingsConfig(plugin));
        provide(ConfigManager.class, configs);

        provide(MessageService.class, new MessageService(plugin));
        provide(SchedulerService.class, new SchedulerService(plugin));
        provide(ListenerRegistry.class, new ListenerRegistry(plugin));
    }
}
