package com.mythicconverter;

import org.bukkit.plugin.java.JavaPlugin;

public class MythicConverterPlugin extends JavaPlugin {
    private static MythicConverterPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        StatMappings.loadFromConfig(getConfig(), getLogger());
        ConvertCommand cmd = new ConvertCommand(this);
        getCommand("convertitems").setExecutor(cmd);
        getCommand("convertitems").setTabCompleter(cmd);
        getLogger().info("MythicConverter enabled! Use /convertitems to convert MMOItems configs.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MythicConverter disabled.");
    }

    public static MythicConverterPlugin getInstance() {
        return instance;
    }
}
