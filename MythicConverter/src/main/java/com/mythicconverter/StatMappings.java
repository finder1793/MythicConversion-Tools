package com.mythicconverter;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Holds mapping tables from MMOItems stat keys to MythicMobs attribute/stat keys.
 * All mappings are loaded from config.yml and can be edited by the user.
 *
 * Attribute mapping values can be:
 *   "AttributeName"              - uses MythicMobs default operation
 *   "AttributeName OPERATION"    - explicit operation (ADD, ADD_SCALAR, MULTIPLY)
 */
public class StatMappings {

    // MMOItems stat key -> MythicMobs Attribute value ("Name" or "Name OPERATION")
    public static final Map<String, String> ATTRIBUTE_MAP = new HashMap<>();

    // MMOItems stat key -> MythicMobs Stats key (custom/MythicLib stats)
    public static final Map<String, String> STAT_MAP = new HashMap<>();

    // MMOItems type name -> equipment slot for MythicMobs Attributes
    public static final Map<String, String> SLOT_MAP = new HashMap<>();

    // MMOItems stat keys that store values as whole percentages (need /100)
    public static final Set<String> PERCENT_STATS = new HashSet<>();

    /**
     * Load all mappings from the plugin config.
     * Each section (attribute-mappings, stat-mappings, slot-mappings) is read
     * as key-value pairs from config.yml. percent-stats is a list of keys.
     */
    public static void loadFromConfig(FileConfiguration config, Logger logger) {
        ATTRIBUTE_MAP.clear();
        STAT_MAP.clear();
        SLOT_MAP.clear();
        PERCENT_STATS.clear();

        int attrCount = loadSection(config, "attribute-mappings", ATTRIBUTE_MAP);
        int statCount = loadSection(config, "stat-mappings", STAT_MAP);
        int slotCount = loadSlotSection(config, "slot-mappings", SLOT_MAP);

        List<String> percentList = config.getStringList("percent-stats");
        for (String key : percentList) {
            PERCENT_STATS.add(key.toLowerCase());
        }

        logger.info("Loaded mappings from config: " + attrCount + " attributes, "
                + statCount + " stats, " + slotCount + " slot mappings, "
                + PERCENT_STATS.size() + " percent stats");
    }

    /**
     * Load a config section into a map. Keys are lowercased for consistent lookup.
     */
    private static int loadSection(FileConfiguration config, String sectionName, Map<String, String> target) {
        ConfigurationSection section = config.getConfigurationSection(sectionName);
        if (section == null) return 0;

        int count = 0;
        for (String key : section.getKeys(false)) {
            String value = section.getString(key, "");
            if (!value.isEmpty()) {
                target.put(key.toLowerCase(), value);
                count++;
            }
        }
        return count;
    }

    /**
     * Load slot mappings preserving key case (uppercase) and allowing empty values.
     */
    private static int loadSlotSection(FileConfiguration config, String sectionName, Map<String, String> target) {
        ConfigurationSection section = config.getConfigurationSection(sectionName);
        if (section == null) return 0;

        int count = 0;
        for (String key : section.getKeys(false)) {
            String value = section.getString(key, "");
            target.put(key.toUpperCase(), value);
            count++;
        }
        return count;
    }

    /**
     * Determine the equipment slot for MythicCrucible Attributes section
     * based on the MMOItems type name.
     */
    public static String getSlotForType(String typeName) {
        return SLOT_MAP.getOrDefault(typeName.toUpperCase(), "MainHand");
    }
}
