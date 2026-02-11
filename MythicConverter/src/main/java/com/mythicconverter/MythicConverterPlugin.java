package com.mythicconverter;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for MythicConverter.
 * <p>
 * Handles plugin lifecycle (enable/disable), loads the default configuration,
 * initializes stat mappings from {@code config.yml}, and registers the
 * {@code /convertitems} and {@code /convertia} commands.
 * </p>
 *
 * @see StatMappings
 * @see ConvertCommand
 * @see ItemsAdderConvertCommand
 */
public class MythicConverterPlugin extends JavaPlugin {

    /** Singleton instance, set during {@link #onEnable()} for static access. */
    private static MythicConverterPlugin instance;

    /**
     * Called when the plugin is enabled.
     * <p>
     * Saves the default {@code config.yml} if it does not already exist,
     * loads all stat/attribute/slot mappings from the config, and registers
     * the {@code /convertitems} and {@code /convertia} commands.
     * </p>
     */
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        StatMappings.loadFromConfig(getConfig(), getLogger());

        ConvertCommand cmd = new ConvertCommand(this);
        getCommand("convertitems").setExecutor(cmd);
        getCommand("convertitems").setTabCompleter(cmd);

        ItemsAdderConvertCommand iaCmd = new ItemsAdderConvertCommand(this);
        getCommand("convertia").setExecutor(iaCmd);
        getCommand("convertia").setTabCompleter(iaCmd);

        getLogger().info("MythicConverter enabled! Use /convertitems or /convertia to convert configs.");
    }

    /** Called when the plugin is disabled. Logs a shutdown message. */
    @Override
    public void onDisable() {
        getLogger().info("MythicConverter disabled.");
    }

    /**
     * Returns the singleton plugin instance.
     *
     * @return the active {@link MythicConverterPlugin} instance, or {@code null} if not yet enabled
     */
    public static MythicConverterPlugin getInstance() {
        return instance;
    }
}
