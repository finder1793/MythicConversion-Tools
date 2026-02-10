package com.mythicconverter;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /convertitems command.
 *
 * Usage:
 *   /convertitems all              - Convert all MMOItems type files
 *   /convertitems <TYPE>           - Convert a specific type (e.g. SWORD, ARMOR)
 *   /convertitems list             - List available MMOItems type files
 *   /convertitems help             - Show help
 */
public class ConvertCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.GOLD + "[MythicConverter] " + ChatColor.RESET;

    private final MythicConverterPlugin plugin;

    public ConvertCommand(MythicConverterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            listTypes(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            StatMappings.loadFromConfig(plugin.getConfig(), plugin.getLogger());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Config and mappings reloaded!");
            return true;
        }

        // Find the MMOItems item directory
        File mmoitemsDir = findMMOItemsDir();
        if (mmoitemsDir == null || !mmoitemsDir.isDirectory()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Could not find MMOItems item directory!");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Expected at: plugins/MMOItems/item/");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Make sure MMOItems is installed and has generated item files.");
            return true;
        }

        // Output directory - defaults to MythicCrucible Items folder
        File outputDir = findOutputDir();
        outputDir.mkdirs();

        ItemConverter converter = new ItemConverter(plugin.getLogger());

        if (args[0].equalsIgnoreCase("all")) {
            convertAll(sender, mmoitemsDir, outputDir, converter);
        } else {
            convertType(sender, mmoitemsDir, outputDir, converter, args[0].toUpperCase());
        }

        return true;
    }

    private void convertAll(CommandSender sender, File mmoitemsDir, File outputDir, ItemConverter converter) {
        File[] ymlFiles = mmoitemsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (ymlFiles == null || ymlFiles.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No .yml files found in MMOItems item directory.");
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Converting " + ymlFiles.length + " type file(s)...");

        int totalItems = 0;
        int totalFiles = 0;

        for (File ymlFile : ymlFiles) {
            String typeName = ymlFile.getName().replace(".yml", "").toUpperCase();
            File outputFile = new File(outputDir, typeName + ".yml");

            try {
                int count = converter.convertFile(ymlFile, outputFile, typeName);
                if (count > 0) {
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "  " + typeName + ": " + count + " item(s) converted");
                    totalItems += count;
                    totalFiles++;
                } else {
                    sender.sendMessage(PREFIX + ChatColor.GRAY + "  " + typeName + ": no items found");
                }
            } catch (Exception e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "  " + typeName + ": ERROR - " + e.getMessage());
                plugin.getLogger().severe("Error converting " + typeName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Done! Converted " + totalItems + " items from " + totalFiles + " type(s).");
        sender.sendMessage(PREFIX + ChatColor.GRAY + "Output: " + outputDir.getAbsolutePath());
    }

    private void convertType(CommandSender sender, File mmoitemsDir, File outputDir, ItemConverter converter, String typeName) {
        File inputFile = new File(mmoitemsDir, typeName + ".yml");
        if (!inputFile.exists()) {
            // Try lowercase
            inputFile = new File(mmoitemsDir, typeName.toLowerCase() + ".yml");
        }
        if (!inputFile.exists()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Type file not found: " + typeName + ".yml");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Use /convertitems list to see available types.");
            return;
        }

        File outputFile = new File(outputDir, typeName + ".yml");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Converting type: " + typeName + "...");

        try {
            int count = converter.convertFile(inputFile, outputFile, typeName);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Converted " + count + " item(s) from " + typeName);
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Output: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Error converting " + typeName + ": " + e.getMessage());
            plugin.getLogger().severe("Error converting " + typeName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void listTypes(CommandSender sender) {
        File mmoitemsDir = findMMOItemsDir();
        if (mmoitemsDir == null || !mmoitemsDir.isDirectory()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Could not find MMOItems item directory!");
            return;
        }

        File[] ymlFiles = mmoitemsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (ymlFiles == null || ymlFiles.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No type files found.");
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Available MMOItems types:");
        for (File f : ymlFiles) {
            String name = f.getName().replace(".yml", "");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "  - " + ChatColor.WHITE + name);
        }
        sender.sendMessage(PREFIX + ChatColor.GRAY + "Use /convertitems <type> or /convertitems all");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== MythicConverter Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/convertitems all" + ChatColor.GRAY + " - Convert all MMOItems type files");
        sender.sendMessage(ChatColor.YELLOW + "/convertitems <type>" + ChatColor.GRAY + " - Convert a specific type (e.g. SWORD)");
        sender.sendMessage(ChatColor.YELLOW + "/convertitems list" + ChatColor.GRAY + " - List available MMOItems type files");
        sender.sendMessage(ChatColor.YELLOW + "/convertitems reload" + ChatColor.GRAY + " - Reload config and mappings");
        sender.sendMessage(ChatColor.YELLOW + "/convertitems help" + ChatColor.GRAY + " - Show this help");
        sender.sendMessage(ChatColor.GRAY + "Output: " + findOutputDir().getAbsolutePath());
        sender.sendMessage(ChatColor.GRAY + "Configure output-path in config.yml to change.");
    }

    /**
     * Locate the MMOItems item config directory.
     * Tries standard paths relative to the server root.
     */
    private File findMMOItemsDir() {
        // Check configured path first — if the user set it, use it
        String configPath = plugin.getConfig().getString("mmoitems-path", "");
        if (!configPath.isEmpty()) {
            File custom = new File(configPath);
            if (custom.isDirectory()) return custom;
        }

        // Standard path: plugins/MMOItems/item/
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        File standard = new File(serverRoot, "plugins/MMOItems/item");
        if (standard.isDirectory()) return standard;

        // Alternative: plugins/MMOItems/items/
        File alt = new File(serverRoot, "plugins/MMOItems/items");
        if (alt.isDirectory()) return alt;

        return standard; // Return standard even if not found, for error messaging
    }

    /**
     * Determine the output directory for converted files.
     * Checks config for custom path, then defaults to MythicMobs/Items/.
     */
    private File findOutputDir() {
        // Check config for custom output path
        String configPath = plugin.getConfig().getString("output-path", "");
        if (!configPath.isEmpty()) {
            return new File(configPath);
        }

        // Default: plugins/MythicMobs/Items/
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        return new File(serverRoot, "plugins/MythicMobs/Items");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("all", "list", "reload", "help"));

            // Add available type names
            File mmoitemsDir = findMMOItemsDir();
            if (mmoitemsDir != null && mmoitemsDir.isDirectory()) {
                File[] ymlFiles = mmoitemsDir.listFiles((dir, name) -> name.endsWith(".yml"));
                if (ymlFiles != null) {
                    for (File f : ymlFiles) {
                        completions.add(f.getName().replace(".yml", "").toUpperCase());
                    }
                }
            }

            String prefix = args[0].toUpperCase();
            return completions.stream()
                    .filter(s -> s.toUpperCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
