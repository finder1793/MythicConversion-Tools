package com.mythicconverter;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Core conversion logic: reads MMOItems YAML files and writes
 * MythicCrucible-compatible YAML output.
 */
public class ItemConverter {

    private final Logger logger;

    public ItemConverter(Logger logger) {
        this.logger = logger;
    }

    /**
     * Convert a single MMOItems type file into a MythicCrucible output file.
     *
     * @param inputFile  The MMOItems .yml file (e.g. SWORD.yml)
     * @param outputFile The target output file
     * @param typeName   The item type name (e.g. "SWORD")
     * @return number of items converted
     */
    public int convertFile(File inputFile, File outputFile, String typeName) throws IOException {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(inputFile);
        Set<String> itemIds = config.getKeys(false);

        if (itemIds.isEmpty()) {
            logger.warning("No items found in " + inputFile.getName());
            return 0;
        }

        outputFile.getParentFile().mkdirs();

        int count = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("# MythicCrucible items converted from MMOItems type: ").append(typeName).append("\n");
        sb.append("# Source: ").append(inputFile.getName()).append("\n");
        sb.append("# NOTE: Some MMOItems features have no direct MythicCrucible equivalent.\n");
        sb.append("# Look for comments starting with '#' for items needing manual review.\n");
        sb.append("# Abilities must be manually converted to MythicMobs Skills.\n");
        sb.append("# Gem sockets, soulbound, and item sets need alternative implementations.\n\n");

        for (String itemId : itemIds) {
            ConfigurationSection itemSection = config.getConfigurationSection(itemId);
            if (itemSection == null) continue;

            // MMOItems stores data under a "base" subsection
            ConfigurationSection base = itemSection.getConfigurationSection("base");
            if (base == null) {
                // Fallback: maybe the keys are directly under the item ID
                base = itemSection;
            }

            String internalName = typeName + "_" + itemId;
            internalName = internalName.replace(" ", "_").replace("-", "_");

            try {
                String converted = convertItem(internalName, base, typeName);
                sb.append(converted);
                count++;
            } catch (Exception e) {
                logger.warning("Failed to convert item '" + itemId + "': " + e.getMessage());
                sb.append("# FAILED TO CONVERT: ").append(itemId).append(" - ").append(e.getMessage()).append("\n\n");
            }
        }

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        }

        return count;
    }

    /**
     * Convert a single item's base config section into a MythicCrucible YAML block.
     */
    private String convertItem(String internalName, ConfigurationSection base, String typeName) {
        StringBuilder sb = new StringBuilder();
        StringBuilder comments = new StringBuilder();

        String material = base.getString("material", "STONE").toUpperCase();
        String slot = StatMappings.getSlotForType(typeName);

        // Attributes: attrName -> formatted value string
        Map<String, String> attributes = new LinkedHashMap<>();
        // Stats lines
        List<String> stats = new ArrayList<>();
        // Enchantments
        List<String> enchantments = new ArrayList<>();
        // Hide flags
        List<String> hideFlags = new ArrayList<>();
        // Options
        Map<String, String> options = new LinkedHashMap<>();

        // --- Material ---
        sb.append(internalName).append(":\n");
        sb.append("  Material: ").append(material).append("\n");

        // --- Display Name ---
        String displayName = base.getString("name");
        if (displayName != null) {
            sb.append("  Display: '").append(escapeYaml(displayName)).append("'\n");
        }

        // --- Lore ---
        if (base.isList("lore")) {
            List<String> loreLines = base.getStringList("lore");
            if (!loreLines.isEmpty()) {
                sb.append("  Lore:\n");
                for (String line : loreLines) {
                    sb.append("  - '").append(escapeYaml(line)).append("'\n");
                }
            }
        }

        // --- Custom Model Data ---
        if (base.contains("custom-model-data")) {
            int cmd = extractInt(base, "custom-model-data");
            if (cmd > 0) {
                sb.append("  Model: ").append(cmd).append("\n");
            }
        }

        // --- Item Model (1.21.2+) ---
        // MMOItems stores this under 'model' (stat ID: MODEL) as a namespaced string
        // Only treat as ItemModel if the value is a non-numeric string (namespaced key)
        String itemModelValue = base.getString("model", base.getString("item-model", ""));
        if (!itemModelValue.isEmpty()) {
            try {
                Integer.parseInt(itemModelValue);
                // It's a number — not an ItemModel, ignore (handled by custom-model-data)
            } catch (NumberFormatException e) {
                sb.append("  ItemModel: ").append(itemModelValue).append("\n");
            }
        }

        // --- Tooltip Style ---
        if (base.contains("tooltip-style")) {
            String tooltipStyle = base.getString("tooltip-style", "");
            if (!tooltipStyle.isEmpty()) {
                sb.append("  TooltipStyle: ").append(tooltipStyle).append("\n");
            }
        }

        // --- Enchantments ---
        ConfigurationSection enchSection = base.getConfigurationSection("enchants");
        if (enchSection != null) {
            for (String enchKey : enchSection.getKeys(false)) {
                int level = extractInt(enchSection, enchKey);
                if (level > 0) {
                    enchantments.add(enchKey.toUpperCase() + ":" + level);
                } else {
                    enchantments.add(enchKey.toUpperCase());
                }
            }
        }

        // --- Unbreakable ---
        if (base.getBoolean("unbreakable", false)) {
            options.put("Unbreakable", "true");
        }

        // --- Dye Color ---
        if (base.isConfigurationSection("dye-color")) {
            ConfigurationSection dye = base.getConfigurationSection("dye-color");
            int r = dye.getInt("red", 0);
            int g = dye.getInt("green", 0);
            int b = dye.getInt("blue", 0);
            options.put("Color", r + "," + g + "," + b);
        } else if (base.isString("dye-color")) {
            options.put("Color", base.getString("dye-color"));
        }

        // --- Hide Flags ---
        if (base.getBoolean("hide-enchants", false)) {
            hideFlags.add("ENCHANTS");
        }
        if (base.getBoolean("hide-potion-effects", false)) {
            hideFlags.add("POTION_EFFECTS");
        }
        if (base.getBoolean("hide-dye", false)) {
            hideFlags.add("DYE");
        }
        if (base.getBoolean("hide-armor-trim", false)) {
            hideFlags.add("ARMOR_TRIM");
        }

        // --- Skull Texture ---
        if (base.isConfigurationSection("skull-texture")) {
            ConfigurationSection skull = base.getConfigurationSection("skull-texture");
            String value = skull.getString("value", skull.getString("url", ""));
            if (!value.isEmpty()) {
                options.put("SkinTexture", value);
            }
        }

        // --- Max Durability ---
        if (base.contains("max-durability")) {
            int dur = extractInt(base, "max-durability");
            if (dur > 0) {
                comments.append("  # max-durability: ").append(dur)
                        .append(" (MythicCrucible uses custom durability via Skills or ItemData)\n");
            }
        }

        // --- Required Level ---
        if (base.contains("required-level")) {
            int lvl = extractInt(base, "required-level");
            if (lvl > 0) {
                sb.append("  EquipLevel: ").append(lvl).append("\n");
            }
        }

        // --- Abilities -> Skills (manual conversion needed) ---
        if (base.isConfigurationSection("ability")) {
            ConfigurationSection abilities = base.getConfigurationSection("ability");
            comments.append("  # --- Abilities (need manual conversion to MythicMobs Skills) ---\n");
            for (String abKey : abilities.getKeys(false)) {
                ConfigurationSection ab = abilities.getConfigurationSection(abKey);
                if (ab == null) continue;
                String abType = ab.getString("type", "UNKNOWN");
                String trigger = ab.getString("mode", "RIGHT_CLICK");
                String mmTrigger = mapAbilityTrigger(trigger);
                comments.append("  # Ability '").append(abKey).append("': type=").append(abType)
                        .append(", trigger=").append(trigger)
                        .append(" -> Skills: skill{").append(abType).append("} ~").append(mmTrigger).append("\n");
                // Log ability modifiers
                for (String modKey : ab.getKeys(false)) {
                    if (!modKey.equals("type") && !modKey.equals("mode")) {
                        comments.append("  #   ").append(modKey).append(": ").append(ab.get(modKey)).append("\n");
                    }
                }
            }
        }

        // --- Permanent Effects ---
        if (base.isConfigurationSection("perm-effects")) {
            ConfigurationSection effects = base.getConfigurationSection("perm-effects");
            comments.append("  # --- Permanent Effects (use MythicMobs Skills ~onEquip) ---\n");
            for (String effectKey : effects.getKeys(false)) {
                ConfigurationSection eff = effects.getConfigurationSection(effectKey);
                if (eff != null) {
                    int amp = eff.getInt("amplifier", 0);
                    comments.append("  # perm-effect: ").append(effectKey)
                            .append(" amplifier ").append(amp).append("\n");
                }
            }
        }

        // --- Elements ---
        if (base.isConfigurationSection("element")) {
            ConfigurationSection elements = base.getConfigurationSection("element");
            for (String elemName : elements.getKeys(false)) {
                ConfigurationSection elemData = elements.getConfigurationSection(elemName);
                if (elemData == null) continue;
                for (String elemStat : elemData.getKeys(false)) {
                    double val = extractDouble(elemData, elemStat);
                    if (val != 0) {
                        stats.add(elemName.toUpperCase() + "_" + elemStat.toUpperCase() + " " + formatNumber(val));
                    }
                }
            }
        }

        // --- MMOItems-specific features (logged as comments) ---
        for (String specialKey : new String[]{
                "soulbound-level", "soulbinding-chance", "success-rate",
                "gem-sockets", "item-level", "item-set", "item-tier",
                "upgrade", "two-handed"}) {
            if (base.contains(specialKey)) {
                comments.append("  # ").append(specialKey).append(": ").append(base.get(specialKey))
                        .append(" (MMOItems-specific, needs manual conversion)\n");
            }
        }

        // --- Process all numeric stats ---
        for (String key : base.getKeys(false)) {
            String normalizedKey = key.toLowerCase().replace("_", "-");
            if (StatMappings.SPECIAL_KEYS.contains(key) || StatMappings.SPECIAL_KEYS.contains(normalizedKey)) continue;

            // Vanilla attribute?
            if (StatMappings.ATTRIBUTE_MAP.containsKey(normalizedKey)) {
                double val = extractDouble(base, key);
                if (val != 0) {
                    if (StatMappings.PERCENT_STATS.contains(normalizedKey)) {
                        val = val / 100.0;
                    }
                    String mapping = StatMappings.ATTRIBUTE_MAP.get(normalizedKey);
                    // Mapping can be "AttributeName" or "AttributeName OPERATION"
                    String[] parts = mapping.split("\\s+", 2);
                    String attrName = parts[0];
                    String operation = parts.length > 1 ? parts[1] : null;
                    String valueStr = formatNumber(val);
                    if (operation != null && !operation.isEmpty()) {
                        valueStr = valueStr + " " + operation;
                    }
                    attributes.put(attrName, valueStr);
                }
                continue;
            }

            // Crucible stat?
            if (StatMappings.STAT_MAP.containsKey(normalizedKey)) {
                double val = extractDouble(base, key);
                if (val != 0) {
                    if (StatMappings.PERCENT_STATS.contains(normalizedKey)) {
                        val = val / 100.0;
                    }
                    stats.add(StatMappings.STAT_MAP.get(normalizedKey) + " " + formatNumber(val));
                }
                continue;
            }

            // Boolean flags
            if (key.startsWith("disable-") || key.startsWith("hide-")) {
                comments.append("  # ").append(key).append(": ").append(base.get(key))
                        .append(" (check MythicCrucible Options or Hide flags)\n");
                continue;
            }

            // Unknown stat
            double val = extractDouble(base, key);
            if (val != 0) {
                String statKey = key.toUpperCase().replace("-", "_");
                comments.append("  # unmapped-stat: ").append(key).append(" = ").append(formatNumber(val))
                        .append(" (STAT_KEY: ").append(statKey).append(")\n");
            } else if (base.isString(key)) {
                comments.append("  # unmapped: ").append(key).append(" = ").append(base.getString(key)).append("\n");
            } else if (base.isConfigurationSection(key)) {
                comments.append("  # unmapped-section: ").append(key).append("\n");
            }
        }

        // --- Write Enchantments ---
        if (!enchantments.isEmpty()) {
            sb.append("  Enchantments:\n");
            for (String ench : enchantments) {
                sb.append("  - ").append(ench).append("\n");
            }
        }

        // --- Write Attributes ---
        if (!attributes.isEmpty() && !slot.isEmpty()) {
            sb.append("  Attributes:\n");
            sb.append("    ").append(slot).append(":\n");
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                sb.append("      ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        } else if (!attributes.isEmpty()) {
            // No slot (accessory, consumable, etc.) - put attributes without slot
            sb.append("  Attributes:\n");
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                sb.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        // --- Write Stats ---
        if (!stats.isEmpty()) {
            sb.append("  Stats:\n");
            for (String stat : stats) {
                sb.append("  - ").append(stat).append("\n");
            }
        }

        // --- Write Hide Flags ---
        if (!hideFlags.isEmpty()) {
            sb.append("  Hide:\n");
            for (String flag : hideFlags) {
                sb.append("  - ").append(flag).append("\n");
            }
        }

        // --- Write Options ---
        if (!options.isEmpty()) {
            sb.append("  Options:\n");
            for (Map.Entry<String, String> opt : options.entrySet()) {
                sb.append("    ").append(opt.getKey()).append(": ").append(opt.getValue()).append("\n");
            }
        }

        // --- Write Comments ---
        if (comments.length() > 0) {
            sb.append(comments);
        }

        sb.append("\n");
        return sb.toString();
    }

    // --- Helpers ---

    /**
     * Extract a numeric value from a config key.
     * MMOItems stats can be a plain number or a section with "base", "scale", etc.
     */
    private double extractDouble(ConfigurationSection section, String key) {
        if (section.isDouble(key) || section.isInt(key)) {
            return section.getDouble(key);
        }
        if (section.isConfigurationSection(key)) {
            ConfigurationSection sub = section.getConfigurationSection(key);
            if (sub.contains("base")) {
                return sub.getDouble("base", 0);
            }
        }
        if (section.isString(key)) {
            try {
                return Double.parseDouble(section.getString(key));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private int extractInt(ConfigurationSection section, String key) {
        return (int) extractDouble(section, key);
    }

    private String escapeYaml(String input) {
        if (input == null) return "";
        return input.replace("'", "''");
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private String mapAbilityTrigger(String mmoitemsTrigger) {
        if (mmoitemsTrigger == null) return "onInteract";
        switch (mmoitemsTrigger.toUpperCase()) {
            case "RIGHT_CLICK":       return "onInteract";
            case "LEFT_CLICK":        return "onAttack";
            case "SHIFT_RIGHT_CLICK": return "onInteract";
            case "SHIFT_LEFT_CLICK":  return "onAttack";
            case "WHEN_HIT":          return "onDamaged";
            case "SNEAK":             return "onCrouch";
            case "TIMER":             return "onTimer:20";
            default:                  return "onInteract";
        }
    }
}
