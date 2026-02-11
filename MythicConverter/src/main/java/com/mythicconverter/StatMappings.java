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
 * Central registry of all mapping tables used to translate MMOItems stat keys
 * into their MythicMobs / MythicCrucible equivalents.
 * <p>
 * All mappings are loaded from {@code config.yml} at startup (and on reload)
 * via {@link #loadFromConfig(FileConfiguration, Logger)}. Users can freely
 * edit the config to add, remove, or rename mappings without recompiling.
 * </p>
 * <p>
 * Attribute mapping values support an optional operation suffix:
 * <ul>
 *   <li>{@code "ATTACK_DAMAGE"} — uses the MythicMobs default operation (ADD)</li>
 *   <li>{@code "ATTACK_SPEED ADD_SCALAR"} — explicit operation override</li>
 * </ul>
 * Supported operations: {@code ADD}, {@code ADD_SCALAR}, {@code MULTIPLY}.
 * </p>
 *
 * @see ItemConverter
 */
public final class StatMappings {

    private StatMappings() {}

    /**
     * Maps lowercase MMOItems stat keys to MythicMobs vanilla attribute values.
     * Values may include an optional operation suffix (e.g. {@code "ATTACK_SPEED ADD_SCALAR"}).
     */
    public static final Map<String, String> ATTRIBUTE_MAP = new HashMap<>();

    /**
     * Maps lowercase MMOItems stat keys to MythicMobs custom stat keys (MythicLib stats).
     * For example, {@code "critical-strike-chance"} → {@code "CriticalStrikeChance"}.
     */
    public static final Map<String, String> STAT_MAP = new HashMap<>();

    /**
     * Maps uppercase MMOItems type names to MythicMobs equipment slot names.
     * Empty string values indicate types with no equipment slot (e.g. consumables).
     * For example, {@code "SWORD"} → {@code "MainHand"}, {@code "ACCESSORY"} → {@code ""}.
     */
    public static final Map<String, String> SLOT_MAP = new HashMap<>();

    /**
     * Set of lowercase MMOItems stat keys whose values are stored as whole percentages
     * (e.g. {@code 25} meaning 25%). The converter divides these by 100 before output.
     */
    public static final Set<String> PERCENT_STATS = new HashSet<>();

    /**
     * Keys that are handled specially (not numeric stats).
     */
    public static final Set<String> SPECIAL_KEYS = Set.of(
            "material", "name", "lore", "custom-model-data", "enchants",
            "unbreakable", "dye-color", "hide-enchants", "hide-potion-effects",
            "skull-texture", "max-durability", "required-level", "two-handed",
            "soulbound-level", "soulbinding-chance", "success-rate",
            "gem-sockets", "item-level", "item-set", "item-tier",
            "upgrade", "ability", "perm-effects", "element",
            "revision-id", "displayed-type", "lore-format", "tooltip",
            "item-particles", "arrow-particles", "projectile-particles",
            "custom-sounds", "commands", "disable-interaction",
            "disable-crafting", "disable-smelting", "disable-smithing",
            "disable-enchanting", "disable-repairing", "disable-attack-passive",
            "hide-dye", "hide-armor-trim", "trim-pattern", "trim-material",
            "compatible-types", "compatible-ids", "compatible-materials",
            "crafted-amount", "craft-permission", "craft-amount",
            "tooltip-style", "item-model", "model"
    );

    /**
     * Loads (or reloads) all mapping tables from the plugin configuration.
     * <p>
     * Clears all existing mappings before loading. Reads four config sections:
     * <ul>
     *   <li>{@code attribute-mappings} → {@link #ATTRIBUTE_MAP} (keys lowercased)</li>
     *   <li>{@code stat-mappings} → {@link #STAT_MAP} (keys lowercased)</li>
     *   <li>{@code slot-mappings} → {@link #SLOT_MAP} (keys uppercased, empty values allowed)</li>
     *   <li>{@code percent-stats} → {@link #PERCENT_STATS} (list of lowercase keys)</li>
     * </ul>
     *
     * @param config the plugin's {@link FileConfiguration} to read from
     * @param logger the logger for reporting loaded mapping counts
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
     * Loads a config section into a map with lowercased keys.
     * Entries with empty values are skipped (not stored).
     *
     * @param config      the plugin configuration
     * @param sectionName the YAML section name to read (e.g. {@code "attribute-mappings"})
     * @param target      the map to populate
     * @return the number of entries loaded
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
     * Loads slot mappings with uppercased keys and allows empty values.
     * <p>
     * Unlike {@link #loadSection}, this method preserves key casing (uppercased)
     * to match the uppercase type names used in {@link #getSlotForType(String)},
     * and stores empty values so that types like {@code ACCESSORY} correctly
     * resolve to no equipment slot.
     * </p>
     *
     * @param config      the plugin configuration
     * @param sectionName the YAML section name to read (e.g. {@code "slot-mappings"})
     * @param target      the map to populate
     * @return the number of entries loaded
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
     * Returns the MythicCrucible equipment slot name for the given MMOItems type.
     * <p>
     * Looks up the type name (uppercased) in {@link #SLOT_MAP}. If no mapping
     * exists, defaults to {@code "MainHand"}. An empty string return value
     * indicates the type has no equipment slot (e.g. accessories, consumables).
     * </p>
     *
     * @param typeName the MMOItems type name (e.g. "SWORD", "HELMET", "ACCESSORY")
     * @return the MythicCrucible slot name, or empty string for slot-less types
     */
    public static String getSlotForType(String typeName) {
        return SLOT_MAP.getOrDefault(typeName.toUpperCase(), "MainHand");
    }

    /**
     * Determine the MythicMobs equipment slot based on material name.
     * Used as a fallback when no type-based slot mapping is available.
     *
     * @param material the Bukkit material name (e.g. "DIAMOND_SWORD", "IRON_HELMET")
     * @return the equipment slot name
     */
    public static String getEquipSlot(String material) {
        String mat = material.toUpperCase();
        if (mat.equals("SHIELD")) return "OffHand";
        if (mat.contains("HELMET") || mat.contains("TURTLE")) return "Head";
        if (mat.contains("CHESTPLATE")) return "Chest";
        if (mat.contains("LEGGINGS")) return "Legs";
        if (mat.contains("BOOTS")) return "Feet";
        return "MainHand";
    }
}
