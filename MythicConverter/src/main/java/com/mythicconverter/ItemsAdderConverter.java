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
 * Converts ItemsAdder item YAML files into MythicCrucible-compatible YAML output.
 * <p>
 * ItemsAdder stores items under a top-level {@code items:} section, with each item
 * containing properties like {@code display_name}/{@code name}, {@code resource},
 * {@code attribute_modifiers}, {@code enchants}, {@code lore}, {@code durability}, etc.
 * </p>
 * <p>
 * The converter maps these to MythicCrucible format: {@code Material}, {@code Display},
 * {@code Lore}, {@code Enchantments}, {@code Attributes}, {@code Options}, etc.
 * Features that cannot be directly converted (events, behaviours) are emitted as
 * YAML comments with migration hints.
 * </p>
 */
public class ItemsAdderConverter {

    /** Logger for conversion progress and error reporting. */
    private final Logger logger;

    /**
     * Maps ItemsAdder attribute modifier key names to MythicMobs attribute names.
     * ItemsAdder uses camelCase keys (e.g. {@code attackDamage}), MythicMobs uses
     * uppercase underscore names (e.g. {@code ATTACK_DAMAGE}).
     */
    private static final Map<String, String> IA_ATTRIBUTE_MAP = new LinkedHashMap<>();

    /**
     * Maps ItemsAdder slot names to MythicMobs slot names.
     * ItemsAdder uses lowercase (e.g. {@code mainhand}), MythicMobs uses
     * PascalCase (e.g. {@code MainHand}).
     */
    private static final Map<String, String> IA_SLOT_MAP = new LinkedHashMap<>();

    static {
        // Attribute name mappings: ItemsAdder camelCase -> MythicMobs UPPER_CASE
        IA_ATTRIBUTE_MAP.put("attackdamage", "ATTACK_DAMAGE");
        IA_ATTRIBUTE_MAP.put("attackspeed", "ATTACK_SPEED");
        IA_ATTRIBUTE_MAP.put("maxhealth", "MAX_HEALTH");
        IA_ATTRIBUTE_MAP.put("movementspeed", "MOVEMENT_SPEED");
        IA_ATTRIBUTE_MAP.put("armor", "ARMOR");
        IA_ATTRIBUTE_MAP.put("armortoughness", "ARMOR_TOUGHNESS");
        IA_ATTRIBUTE_MAP.put("attackknockback", "ATTACK_KNOCKBACK");
        IA_ATTRIBUTE_MAP.put("knockbackresistance", "KNOCKBACK_RESISTANCE");
        IA_ATTRIBUTE_MAP.put("luck", "LUCK");
        IA_ATTRIBUTE_MAP.put("flyingspeed", "FLYING_SPEED");
        IA_ATTRIBUTE_MAP.put("followrange", "FOLLOW_RANGE");
        IA_ATTRIBUTE_MAP.put("maxabsorption", "MAX_ABSORPTION");
        IA_ATTRIBUTE_MAP.put("scale", "SCALE");
        IA_ATTRIBUTE_MAP.put("stepheight", "STEP_HEIGHT");
        IA_ATTRIBUTE_MAP.put("jumpstrength", "JUMP_HEIGHT");
        IA_ATTRIBUTE_MAP.put("gravity", "GRAVITY");
        IA_ATTRIBUTE_MAP.put("safefalldistance", "SAFE_FALL_DISTANCE");
        IA_ATTRIBUTE_MAP.put("falldamagemultiplier", "FALL_DAMAGE_MULTIPLIER");
        IA_ATTRIBUTE_MAP.put("burningtime", "BURNING_TIME");
        IA_ATTRIBUTE_MAP.put("explosionknockbackresistance", "EXPLOSION_KNOCKBACK_RESISTANCE");
        IA_ATTRIBUTE_MAP.put("miningefficiency", "MINING_EFFICIENCY");
        IA_ATTRIBUTE_MAP.put("movementefficiency", "MOVEMENT_EFFICIENCY");
        IA_ATTRIBUTE_MAP.put("oxygenbonus", "OXYGEN");
        IA_ATTRIBUTE_MAP.put("sneakingspeed", "SNEAKING_SPEED");
        IA_ATTRIBUTE_MAP.put("submergedminingspeed", "SUBMERGED_MINING_SPEED");
        IA_ATTRIBUTE_MAP.put("sweepingdamageratio", "SWEEPING_DAMAGE_RATIO");
        IA_ATTRIBUTE_MAP.put("watermovementefficiency", "WATER_MOVEMENT_EFFICIENCY");
        IA_ATTRIBUTE_MAP.put("blockbreakspeed", "BLOCK_BREAK_SPEED");
        IA_ATTRIBUTE_MAP.put("blockinteractionrange", "BLOCK_INTERACTION_RANGE");
        IA_ATTRIBUTE_MAP.put("entityinteractionrange", "ENTITY_INTERACTION_RANGE");

        // Slot name mappings: ItemsAdder lowercase -> MythicMobs PascalCase
        IA_SLOT_MAP.put("mainhand", "MainHand");
        IA_SLOT_MAP.put("offhand", "OffHand");
        IA_SLOT_MAP.put("head", "Head");
        IA_SLOT_MAP.put("chest", "Chest");
        IA_SLOT_MAP.put("legs", "Legs");
        IA_SLOT_MAP.put("feet", "Feet");
    }

    /**
     * Creates a new ItemsAdder converter instance.
     *
     * @param logger the logger to use for conversion messages
     */
    public ItemsAdderConverter(Logger logger) {
        this.logger = logger;
    }

    /**
     * Converts all items in an ItemsAdder YAML file to MythicCrucible format.
     * <p>
     * ItemsAdder files have a top-level {@code info:} section with the namespace,
     * and an {@code items:} section containing all item definitions.
     * </p>
     *
     * @param inputFile  the ItemsAdder YAML file
     * @param outputFile the output MythicCrucible YAML file
     * @return number of items converted
     */
    public int convertFile(File inputFile, File outputFile) throws IOException {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(inputFile);

        // Get the namespace from info section
        String namespace = config.getString("info.namespace", "");

        // Items are under the "items" section
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) return 0;

        Set<String> itemIds = itemsSection.getKeys(false);
        if (itemIds.isEmpty()) return 0;

        StringBuilder output = new StringBuilder();
        output.append("# MythicCrucible items converted from ItemsAdder");
        if (!namespace.isEmpty()) {
            output.append(" namespace: ").append(namespace);
        }
        output.append("\n");
        output.append("# Generated by MythicConverter\n\n");

        int count = 0;
        for (String itemId : itemIds) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
            if (itemSection == null) continue;

            // Skip disabled items
            if (!itemSection.getBoolean("enabled", true)) continue;

            String converted = convertItem(itemId, itemSection, namespace);
            if (converted != null && !converted.isEmpty()) {
                output.append(converted).append("\n");
                count++;
            }
        }

        // Write output
        outputFile.getParentFile().mkdirs();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            writer.write(output.toString());
        }

        logger.info("Converted " + count + " ItemsAdder items from " + inputFile.getName() + " -> " + outputFile.getName());
        return count;
    }

    /**
     * Converts a single ItemsAdder item into a MythicCrucible YAML block.
     *
     * @param itemId      the item ID (used as the top-level YAML key)
     * @param item        the configuration section for this item
     * @param namespace   the ItemsAdder namespace (for comment reference)
     * @return the formatted MythicCrucible YAML string
     */
    private String convertItem(String itemId, ConfigurationSection item, String namespace) {
        StringBuilder result = new StringBuilder();
        StringBuilder comments = new StringBuilder();

        // --- Material ---
        String material = "PAPER";
        ConfigurationSection resource = item.getConfigurationSection("resource");
        if (resource != null) {
            material = resource.getString("material", "PAPER").toUpperCase();
        }

        // --- Display Name ---
        // ItemsAdder 4.0.9+ uses "name", older versions use "display_name"
        String displayName = item.getString("name", item.getString("display_name", ""));

        // --- Lore ---
        List<String> lore = item.getStringList("lore");

        // --- Model Path / Item Model ---
        String modelPath = "";
        if (resource != null) {
            modelPath = resource.getString("model_path", "");
        }

        // --- Enchantments ---
        // ItemsAdder format: list of "enchant_name:level" strings
        List<String> enchantments = new ArrayList<>();
        List<String> iaEnchants = item.getStringList("enchants");
        for (String ench : iaEnchants) {
            // Convert to MythicMobs format: ENCHANT_NAME:level
            String[] parts = ench.split(":");
            String enchName = parts[0].toUpperCase().replace(" ", "_");
            if (parts.length > 1) {
                enchantments.add(enchName + ":" + parts[1].trim());
            } else {
                enchantments.add(enchName);
            }
        }

        // --- Attribute Modifiers ---
        // ItemsAdder format:
        //   attribute_modifiers:
        //     mainhand:
        //       attackDamage: 10
        //       attackSpeed: 1.6
        Map<String, Map<String, Double>> slotAttributes = new LinkedHashMap<>();
        ConfigurationSection attrSection = item.getConfigurationSection("attribute_modifiers");
        if (attrSection != null) {
            for (String slotKey : attrSection.getKeys(false)) {
                ConfigurationSection slotSection = attrSection.getConfigurationSection(slotKey);
                if (slotSection == null) continue;

                String mythicSlot = IA_SLOT_MAP.getOrDefault(slotKey.toLowerCase(), slotKey);
                Map<String, Double> attrs = new LinkedHashMap<>();

                for (String attrKey : slotSection.getKeys(false)) {
                    double value = extractDouble(slotSection, attrKey);
                    String mythicAttr = IA_ATTRIBUTE_MAP.getOrDefault(attrKey.toLowerCase(), attrKey.toUpperCase());
                    attrs.put(mythicAttr, value);
                }

                if (!attrs.isEmpty()) {
                    slotAttributes.put(mythicSlot, attrs);
                }
            }
        }

        // Also handle slot_attribute_modifiers (used in armor configs)
        ConfigurationSection slotAttrSection = item.getConfigurationSection("slot_attribute_modifiers");
        if (slotAttrSection != null) {
            for (String attrKey : slotAttrSection.getKeys(false)) {
                double value = extractDouble(slotAttrSection, attrKey);
                String mythicAttr = IA_ATTRIBUTE_MAP.getOrDefault(attrKey.toLowerCase(), attrKey.toUpperCase());
                // slot_attribute_modifiers don't specify a slot; infer from material
                String inferredSlot = inferSlotFromMaterial(material);
                slotAttributes.computeIfAbsent(inferredSlot, k -> new LinkedHashMap<>()).put(mythicAttr, value);
            }
        }

        // --- Options ---
        Map<String, String> options = new LinkedHashMap<>();

        // Glint
        if (item.contains("glint")) {
            options.put("EnchantGlint", String.valueOf(item.getBoolean("glint", false)));
        }

        // --- Hide Flags ---
        List<String> hideFlags = new ArrayList<>();
        List<String> iaFlags = item.getStringList("item_flags");
        for (String flag : iaFlags) {
            // Convert Bukkit ItemFlag names to MythicMobs Hide names
            String upper = flag.toUpperCase().replace("HIDE_", "");
            hideFlags.add(upper);
        }

        // --- Durability ---
        ConfigurationSection durSection = item.getConfigurationSection("durability");
        if (durSection != null) {
            int maxDur = durSection.getInt("max_durability", 0);
            if (maxDur > 0) {
                comments.append("  # max_durability: ").append(maxDur)
                        .append(" (MythicCrucible uses custom durability via Skills or ItemData)\n");
            }
        }

        // --- Events (ItemsAdder-specific, cannot be directly converted) ---
        if (item.isConfigurationSection("events")) {
            comments.append("  # EVENTS: This item has ItemsAdder events. Recreate as MythicMobs Skills.\n");
            ConfigurationSection events = item.getConfigurationSection("events");
            for (String eventKey : events.getKeys(false)) {
                comments.append("  #   - ").append(eventKey).append("\n");
                if (events.isConfigurationSection(eventKey)) {
                    for (String actionKey : events.getConfigurationSection(eventKey).getKeys(false)) {
                        comments.append("  #     - ").append(actionKey).append("\n");
                    }
                }
            }
        }

        // --- Furniture (behaviours.furniture) ---
        ConfigurationSection furnitureSection = null;
        if (item.isConfigurationSection("behaviours")) {
            ConfigurationSection behaviours = item.getConfigurationSection("behaviours");
            if (behaviours.isConfigurationSection("furniture")) {
                furnitureSection = behaviours.getConfigurationSection("furniture");
            }
            // Other behaviours that can't be converted
            if (behaviours.isConfigurationSection("hat")) {
                comments.append("  # BEHAVIOUR: hat - No direct MythicCrucible equivalent.\n");
            }
            if (behaviours.isConfigurationSection("music_disc")) {
                comments.append("  # BEHAVIOUR: music_disc - No direct MythicCrucible equivalent.\n");
            }
        }

        // --- Block (specific_properties.block) ---
        ConfigurationSection blockSection = null;
        if (item.isConfigurationSection("specific_properties")) {
            ConfigurationSection specificProps = item.getConfigurationSection("specific_properties");
            if (specificProps.isConfigurationSection("block")) {
                blockSection = specificProps.getConfigurationSection("block");
            }
        }

        // --- Bow / Crossbow detection ---
        boolean isBow = material.equals("BOW");
        boolean isCrossbow = material.equals("CROSSBOW");

        // --- Custom Armor (Trims) ---
        String armorTexturePath = "";
        String armorType = "";
        String inventoryTexture = "";
        int customModelData = 0;
        if (resource != null) {
            // Check for generate_custom_armor section
            ConfigurationSection customArmor = resource.getConfigurationSection("generate_custom_armor");
            if (customArmor != null && customArmor.getBoolean("enabled", true)) {
                armorTexturePath = customArmor.getString("armor_texture_path", "");
                armorType = customArmor.getString("type", "TRIMS").toUpperCase();
            }
            // Inventory texture from textures list
            List<String> textures = resource.getStringList("textures");
            if (!textures.isEmpty()) {
                inventoryTexture = textures.get(0);
            }
            // CustomModelData
            customModelData = resource.getInt("model_id", resource.getInt("custom_model_data", 0));
        }

        // --- Equipment (armor wear texture) ---
        if (item.isConfigurationSection("equipment")) {
            String equipId = item.getString("equipment.id", "");
            if (!equipId.isEmpty()) {
                comments.append("  # EQUIPMENT: ").append(equipId)
                        .append(" - Configure armor model in MythicCrucible Generation section.\n");
            }
        }

        // --- Blocked Enchants ---
        if (item.isList("blocked_enchants")) {
            comments.append("  # BLOCKED ENCHANTS: ");
            comments.append(String.join(", ", item.getStringList("blocked_enchants")));
            comments.append(" - No direct MythicCrucible equivalent.\n");
        }

        // --- Max Stack Size ---
        if (item.contains("max_stack_size")) {
            int maxStack = item.getInt("max_stack_size", 64);
            if (maxStack != 64) {
                comments.append("  # max_stack_size: ").append(maxStack).append("\n");
            }
        }

        // === Build the MythicCrucible YAML output ===
        // Use uppercase item ID for MythicCrucible convention
        String outputId = itemId.toUpperCase();
        result.append(outputId).append(":\n");
        result.append("  Material: ").append(material).append("\n");

        // Type: FURNITURE or BLOCK (omit for normal items, MythicCrucible defaults to ITEM)
        if (furnitureSection != null) {
            result.append("  Type: FURNITURE\n");
        } else if (blockSection != null) {
            result.append("  Type: BLOCK\n");
        }

        if (!displayName.isEmpty()) {
            result.append("  Display: '").append(displayName.replace("'", "''")).append("'\n");
        }

        // Item Model from model_path
        if (!modelPath.isEmpty()) {
            // ItemsAdder model_path is like "item/my_sword" — convert to namespaced key
            String itemModel = modelPath;
            if (!itemModel.contains(":")) {
                itemModel = namespace.isEmpty() ? itemModel : namespace + ":" + itemModel;
            }
            result.append("  ItemModel: ").append(itemModel).append("\n");
        }

        // Model (for resource pack generation) — skip if custom armor will emit its own Model line
        if (!modelPath.isEmpty() && armorTexturePath.isEmpty()) {
            result.append("  Model: ").append(modelPath).append("\n");
        }

        // Lore
        if (!lore.isEmpty()) {
            result.append("  Lore:\n");
            for (String line : lore) {
                result.append("  - '").append(line.replace("'", "''")).append("'\n");
            }
        }

        // Enchantments
        if (!enchantments.isEmpty()) {
            result.append("  Enchantments:\n");
            for (String ench : enchantments) {
                result.append("  - ").append(ench).append("\n");
            }
        }

        // Attributes (per-slot)
        if (!slotAttributes.isEmpty()) {
            result.append("  Attributes:\n");
            for (Map.Entry<String, Map<String, Double>> slotEntry : slotAttributes.entrySet()) {
                result.append("    ").append(slotEntry.getKey()).append(":\n");
                for (Map.Entry<String, Double> attrEntry : slotEntry.getValue().entrySet()) {
                    result.append("      ").append(attrEntry.getKey()).append(": ")
                            .append(formatNumber(attrEntry.getValue())).append("\n");
                }
            }
        }

        // Hide flags
        if (!hideFlags.isEmpty()) {
            result.append("  Hide:\n");
            for (String flag : hideFlags) {
                result.append("  - ").append(flag).append("\n");
            }
        }

        // Options
        if (!options.isEmpty()) {
            result.append("  Options:\n");
            for (Map.Entry<String, String> entry : options.entrySet()) {
                result.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        // === Custom Armor Generation (Trims) ===
        if (!armorTexturePath.isEmpty()) {
            if (customModelData > 0) {
                result.append("  Model: ").append(customModelData).append("\n");
            }
            result.append("  Generation:\n");
            if (!inventoryTexture.isEmpty()) {
                result.append("    Texture: ").append(inventoryTexture).append("\n");
            }
            result.append("    Armor:\n");
            result.append("      Texture: ").append(armorTexturePath).append("\n");
            result.append("      Type: ").append(armorType.isEmpty() ? "TRIMS" : armorType).append("\n");
        }

        // === Furniture Section ===
        if (furnitureSection != null) {
            result.append("  Furniture:\n");

            // Entity type: armor_stand -> ARMOR_STAND, item_frame -> ITEM_FRAME, item_display -> DISPLAY
            String entityType = furnitureSection.getString("entity", "item_display");
            String crucibleEntityType = mapFurnitureEntityType(entityType);
            result.append("    Type: ").append(crucibleEntityType).append("\n");

            // Placement
            String placement = furnitureSection.getString("placement", "floor");
            result.append("    Placement: ").append(placement.toUpperCase()).append("\n");

            // Solid
            if (furnitureSection.contains("solid")) {
                result.append("    IsSolid: ").append(furnitureSection.getBoolean("solid", true)).append("\n");
            }

            // Fixed rotation -> LockRotation
            if (furnitureSection.contains("fixed_rotation")) {
                result.append("    LockRotation: ").append(furnitureSection.getBoolean("fixed_rotation", false)).append("\n");
            }

            // Hitbox
            if (furnitureSection.isConfigurationSection("hitbox")) {
                ConfigurationSection hitbox = furnitureSection.getConfigurationSection("hitbox");
                double height = hitbox.getDouble("height", hitbox.getDouble("length", 1));
                double width = hitbox.getDouble("width", 1);
                result.append("    Hitbox:\n");
                result.append("      Height: ").append(formatNumber(height)).append("\n");
                result.append("      Width: ").append(formatNumber(width)).append("\n");
            }

            // Light level -> Lights
            if (furnitureSection.contains("light_level")) {
                int lightLevel = furnitureSection.getInt("light_level", 0);
                if (lightLevel > 0) {
                    result.append("    Lights:\n");
                    result.append("    - 0,0,0 ").append(lightLevel).append("\n");
                }
            }

            // Barriers
            if (furnitureSection.isList("barriers")) {
                List<String> barriers = furnitureSection.getStringList("barriers");
                if (!barriers.isEmpty()) {
                    result.append("    Barriers:\n");
                    for (String barrier : barriers) {
                        result.append("    - ").append(barrier).append("\n");
                    }
                }
            }

            // Seats (different system in MythicCrucible, emit as comment)
            if (furnitureSection.isList("seats") || furnitureSection.isConfigurationSection("seats")) {
                comments.append("  # SEATS: This furniture has seats. Configure using MythicCrucible FurnitureSkills ~onInteract.\n");
            }

            // Opposite direction
            if (furnitureSection.getBoolean("opposite_direction", false)) {
                comments.append("  # opposite_direction: true - Adjust model rotation in MythicCrucible.\n");
            }

            // Gravity
            if (furnitureSection.contains("gravity")) {
                comments.append("  # gravity: ").append(furnitureSection.getBoolean("gravity", false))
                        .append(" - ArmorStand gravity; configure in MythicCrucible Furniture entity settings.\n");
            }

            // Small (armor stand)
            if (furnitureSection.getBoolean("small", false)) {
                comments.append("  # small: true - Use small armor stand; configure in MythicCrucible Furniture.Small.\n");
            }

            // Sound
            if (furnitureSection.isConfigurationSection("sound")) {
                comments.append("  # SOUNDS: Furniture has custom sounds. Configure via FurnitureSkills in MythicCrucible.\n");
            }
        }

        // === Custom Block Section ===
        if (blockSection != null) {
            result.append("  CustomBlock:\n");

            // Block type: REAL_NOTE -> NOTEBLOCK, REAL -> MUSHROOM, REAL_WIRE -> TRIPWIRE
            String placedModelType = "NOTEBLOCK";
            if (blockSection.isConfigurationSection("placed_model")) {
                String iaType = blockSection.getString("placed_model.type", "REAL_NOTE").toUpperCase();
                placedModelType = mapBlockType(iaType);
            }
            result.append("    Type: ").append(placedModelType).append("\n");

            // Hardness
            if (blockSection.contains("hardness")) {
                int hardness = blockSection.getInt("hardness", 0);
                result.append("    Hardness: ").append(hardness).append("\n");
            }

            // Blast resistance
            if (blockSection.contains("blast_resistance")) {
                double blastRes = extractDouble(blockSection, "blast_resistance");
                result.append("    BlastResistance: ").append(formatNumber(blastRes)).append("\n");
            }

            // Tools whitelist -> CustomBlock.Tools
            if (blockSection.isList("break_tools_whitelist")) {
                List<String> tools = blockSection.getStringList("break_tools_whitelist");
                if (!tools.isEmpty()) {
                    result.append("    Tools:\n");
                    for (String tool : tools) {
                        result.append("    - ").append(tool.toUpperCase()).append("\n");
                    }
                }
            }

            // Drop when mined
            if (blockSection.contains("drop_when_mined")) {
                boolean dropWhenMined = blockSection.getBoolean("drop_when_mined", true);
                if (!dropWhenMined) {
                    comments.append("  # drop_when_mined: false - Configure CustomBlock.Drops to control drops.\n");
                }
            }

            // Light level
            if (blockSection.contains("light_level")) {
                int lightLevel = blockSection.getInt("light_level", 0);
                if (lightLevel > 0) {
                    comments.append("  # light_level: ").append(lightLevel)
                            .append(" - MythicCrucible blocks don't natively emit light. Use light blocks or furniture overlay.\n");
                }
            }

            // Break particles
            if (blockSection.contains("break_particles")) {
                comments.append("  # break_particles: ").append(blockSection.getString("break_particles", ""))
                        .append(" - Configure via CustomBlockSkills in MythicCrucible.\n");
            }

            // Sounds
            if (blockSection.isConfigurationSection("sound")) {
                comments.append("  # SOUNDS: Block has custom sounds. Configure via CustomBlockSkills in MythicCrucible.\n");
            }

            // No explosion
            if (blockSection.getBoolean("no_explosion", false)) {
                comments.append("  # no_explosion: true - Set a very high BlastResistance value.\n");
            }

            // Tools blacklist
            if (blockSection.isList("break_tools_blacklist")) {
                comments.append("  # BREAK TOOLS BLACKLIST: ");
                comments.append(String.join(", ", blockSection.getStringList("break_tools_blacklist")));
                comments.append(" - No direct MythicCrucible equivalent; use CustomBlockSkills.\n");
            }
        }

        // === Bow / Crossbow notes ===
        if (isBow || isCrossbow) {
            String texturePath = "";
            if (resource != null) {
                List<String> textures = resource.getStringList("textures");
                if (!textures.isEmpty()) {
                    texturePath = textures.get(0);
                }
            }
            if (isBow) {
                comments.append("  # BOW: Pull textures use suffixes _0, _1, _2 in ItemsAdder.\n");
                comments.append("  #   MythicCrucible handles bow pull states via resource pack Generation.\n");
                if (!texturePath.isEmpty()) {
                    comments.append("  #   Base texture: ").append(texturePath).append("\n");
                    comments.append("  #   Expected pull textures: ").append(texturePath).append("_0, _1, _2\n");
                }
            } else {
                comments.append("  # CROSSBOW: Pull textures use suffixes _0, _1, _2, _charged, _firework in ItemsAdder.\n");
                comments.append("  #   MythicCrucible handles crossbow states via resource pack Generation.\n");
                if (!texturePath.isEmpty()) {
                    comments.append("  #   Base texture: ").append(texturePath).append("\n");
                }
            }
        }

        // Source reference comment
        if (!namespace.isEmpty()) {
            comments.append("  # Source: ").append(namespace).append(":").append(itemId).append("\n");
        }

        // Comments for manual conversion
        if (comments.length() > 0) {
            result.append(comments);
        }

        return result.toString();
    }

    /**
     * Maps an ItemsAdder furniture entity type to a MythicCrucible furniture entity type.
     * <p>
     * ItemsAdder uses: {@code armor_stand}, {@code item_frame}, {@code item_display}.<br>
     * MythicCrucible uses: {@code ARMOR_STAND}, {@code ITEM_FRAME}, {@code DISPLAY}.
     * </p>
     *
     * @param iaType the ItemsAdder entity type string
     * @return the MythicCrucible entity type string
     */
    private String mapFurnitureEntityType(String iaType) {
        switch (iaType.toLowerCase()) {
            case "armor_stand":
            case "armorstand":
                return "ARMOR_STAND";
            case "item_frame":
            case "itemframe":
                return "ITEM_FRAME";
            case "item_display":
            case "display":
                return "DISPLAY";
            default:
                return "DISPLAY";
        }
    }

    /**
     * Maps an ItemsAdder block placed_model type to a MythicCrucible CustomBlock type.
     * <p>
     * ItemsAdder uses: {@code REAL_NOTE}, {@code REAL}, {@code REAL_WIRE}, {@code REAL_TRANSPARENT}, {@code FIRE}, {@code TILE}.<br>
     * MythicCrucible uses: {@code NOTEBLOCK}, {@code MUSHROOM}, {@code TRIPWIRE}, {@code CHORUS}.
     * </p>
     *
     * @param iaType the ItemsAdder placed_model type string (uppercase)
     * @return the MythicCrucible CustomBlock type string
     */
    private String mapBlockType(String iaType) {
        switch (iaType) {
            case "REAL_NOTE":
                return "NOTEBLOCK";
            case "REAL":
                return "MUSHROOM";
            case "REAL_WIRE":
            case "REAL_TRANSPARENT":
                return "TRIPWIRE";
            case "FIRE":
                return "CHORUS";
            case "TILE":
                // TILE blocks (spawner-based) have no direct MythicCrucible equivalent
                return "NOTEBLOCK";
            default:
                return "NOTEBLOCK";
        }
    }

    /**
     * Infers the MythicMobs equipment slot from the base material name.
     * Used when ItemsAdder's {@code slot_attribute_modifiers} doesn't specify a slot.
     *
     * @param material the Bukkit material name
     * @return the inferred MythicMobs slot name
     */
    private String inferSlotFromMaterial(String material) {
        String upper = material.toUpperCase();
        if (upper.contains("HELMET") || upper.contains("HEAD") || upper.contains("SKULL")) return "Head";
        if (upper.contains("CHESTPLATE") || upper.contains("ELYTRA")) return "Chest";
        if (upper.contains("LEGGINGS")) return "Legs";
        if (upper.contains("BOOTS")) return "Feet";
        if (upper.contains("SHIELD")) return "OffHand";
        return "MainHand";
    }

    // --- Utility methods ---

    /**
     * Extracts a numeric value from a config key, handling double, int, and string representations.
     *
     * @param section the configuration section to read from
     * @param key     the key to extract
     * @return the numeric value, or {@code 0} if the key is missing or non-numeric
     */
    private double extractDouble(ConfigurationSection section, String key) {
        if (section.isDouble(key)) return section.getDouble(key);
        if (section.isInt(key)) return section.getInt(key);
        if (section.isString(key)) {
            try {
                return Double.parseDouble(section.getString(key));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /**
     * Formats a double for YAML output. Whole numbers are rendered without a decimal
     * point (e.g. {@code 5} instead of {@code 5.0}).
     *
     * @param value the numeric value to format
     * @return the formatted string representation
     */
    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
