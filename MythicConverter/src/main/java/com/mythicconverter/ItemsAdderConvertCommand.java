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
 * Handles the {@code /convertia} command for converting ItemsAdder items to MythicCrucible.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>{@code all}    — Convert all ItemsAdder YAML files in the data directory</li>
 *   <li>{@code <file>} — Convert a specific YAML file by name (without extension)</li>
 *   <li>{@code list}   — List available ItemsAdder YAML files</li>
 *   <li>{@code help}   — Display in-game usage information</li>
 * </ul>
 * <p>
 * Input is read from the ItemsAdder contents directory (auto-detected or configured via
 * {@code itemsadder-path} in config.yml). Output is written to the MythicMobs Items
 * directory (or a custom path via {@code ia-output-path}).
 *
 * @see ItemsAdderConverter
 */
public class ItemsAdderConvertCommand implements CommandExecutor, TabCompleter {

    /** Chat prefix prepended to all player-facing messages. */
    private static final String PREFIX = ChatColor.GOLD + "[MythicConverter] " + ChatColor.RESET;

    /** Reference to the owning plugin instance for config and logger access. */
    private final MythicConverterPlugin plugin;

    /**
     * Constructs a new command handler.
     *
     * @param plugin the owning plugin instance
     */
    public ItemsAdderConvertCommand(MythicConverterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Dispatches the {@code /convertia} command to the appropriate subcommand handler.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            listFiles(sender);
            return true;
        }

        // Find the ItemsAdder contents directory
        File iaDir = findItemsAdderDir();
        if (iaDir == null || !iaDir.isDirectory()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Could not find ItemsAdder contents directory!");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Expected at: plugins/ItemsAdder/contents/");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Set itemsadder-path in config.yml to specify a custom path.");
            return true;
        }

        // Output directory
        File outputDir = findOutputDir();
        outputDir.mkdirs();

        ItemsAdderConverter converter = new ItemsAdderConverter(plugin.getLogger());

        if (args[0].equalsIgnoreCase("all")) {
            convertAll(sender, iaDir, outputDir, converter);
        } else {
            convertFile(sender, iaDir, outputDir, converter, args[0]);
        }

        return true;
    }

    /**
     * Recursively finds all {@code .yml} files under the ItemsAdder contents directory
     * that contain an {@code items:} section, and converts them all.
     *
     * @param sender    the command sender to receive progress messages
     * @param iaDir     the ItemsAdder contents directory
     * @param outputDir the target directory for converted output files
     * @param converter the converter instance to use
     */
    private void convertAll(CommandSender sender, File iaDir, File outputDir, ItemsAdderConverter converter) {
        List<File> ymlFiles = new ArrayList<>();
        findYmlFilesRecursive(iaDir, ymlFiles);

        if (ymlFiles.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No .yml files found in ItemsAdder contents directory.");
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Scanning " + ymlFiles.size() + " file(s) for items...");

        int totalItems = 0;
        int totalFiles = 0;

        for (File ymlFile : ymlFiles) {
            try {
                // Build a relative output name based on the file's relative path
                String relativePath = getRelativePath(iaDir, ymlFile);
                String outputName = relativePath.replace(File.separatorChar, '_');
                if (outputName.startsWith("_")) outputName = outputName.substring(1);
                File outputFile = new File(outputDir, outputName);

                int count = converter.convertFile(ymlFile, outputFile);
                if (count > 0) {
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "  " + relativePath + ": " + count + " item(s) converted");
                    totalItems += count;
                    totalFiles++;
                }
            } catch (Exception e) {
                String relativePath = getRelativePath(iaDir, ymlFile);
                sender.sendMessage(PREFIX + ChatColor.RED + "  " + relativePath + ": ERROR - " + e.getMessage());
                plugin.getLogger().severe("Error converting " + ymlFile.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Done! Converted " + totalItems + " items from " + totalFiles + " file(s).");
        sender.sendMessage(PREFIX + ChatColor.GRAY + "Output: " + outputDir.getAbsolutePath());
    }

    /**
     * Converts a single ItemsAdder YAML file by name.
     * Searches recursively for a matching filename.
     *
     * @param sender    the command sender to receive progress messages
     * @param iaDir     the ItemsAdder contents directory
     * @param outputDir the target directory for the converted output file
     * @param converter the converter instance to use
     * @param fileName  the file name to search for (without .yml extension)
     */
    private void convertFile(CommandSender sender, File iaDir, File outputDir, ItemsAdderConverter converter, String fileName) {
        // Search recursively for the file
        String searchName = fileName.endsWith(".yml") ? fileName : fileName + ".yml";
        File found = findFileRecursive(iaDir, searchName);

        if (found == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "File not found: " + searchName);
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Use /convertia list to see available files.");
            return;
        }

        File outputFile = new File(outputDir, found.getName());
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Converting: " + getRelativePath(iaDir, found) + "...");

        try {
            int count = converter.convertFile(found, outputFile);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Converted " + count + " item(s)");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Output: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Error: " + e.getMessage());
            plugin.getLogger().severe("Error converting " + found.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Lists all YAML files found in the ItemsAdder contents directory.
     *
     * @param sender the command sender to receive the list
     */
    private void listFiles(CommandSender sender) {
        File iaDir = findItemsAdderDir();
        if (iaDir == null || !iaDir.isDirectory()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Could not find ItemsAdder contents directory!");
            return;
        }

        List<File> ymlFiles = new ArrayList<>();
        findYmlFilesRecursive(iaDir, ymlFiles);

        if (ymlFiles.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No .yml files found.");
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Available ItemsAdder files (" + ymlFiles.size() + "):");
        for (File f : ymlFiles) {
            String relativePath = getRelativePath(iaDir, f);
            sender.sendMessage(PREFIX + ChatColor.GRAY + "  - " + ChatColor.WHITE + relativePath);
        }
        sender.sendMessage(PREFIX + ChatColor.GRAY + "Use /convertia <filename> or /convertia all");
    }

    /**
     * Sends the help/usage message to the sender.
     *
     * @param sender the command sender to receive the help text
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== MythicConverter - ItemsAdder Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/convertia all" + ChatColor.GRAY + " - Convert all ItemsAdder item files");
        sender.sendMessage(ChatColor.YELLOW + "/convertia <file>" + ChatColor.GRAY + " - Convert a specific file (e.g. swords)");
        sender.sendMessage(ChatColor.YELLOW + "/convertia list" + ChatColor.GRAY + " - List available ItemsAdder files");
        sender.sendMessage(ChatColor.YELLOW + "/convertia help" + ChatColor.GRAY + " - Show this help");
        sender.sendMessage(ChatColor.GRAY + "Output: " + findOutputDir().getAbsolutePath());
        sender.sendMessage(ChatColor.GRAY + "Configure itemsadder-path and ia-output-path in config.yml.");
    }

    /**
     * Locates the ItemsAdder contents directory.
     * Checks config first, then auto-detects standard paths.
     *
     * @return the ItemsAdder contents directory, or a default path for error messaging
     */
    private File findItemsAdderDir() {
        // Check configured path first
        String configPath = plugin.getConfig().getString("itemsadder-path", "");
        if (!configPath.isEmpty()) {
            File custom = new File(configPath);
            if (custom.isDirectory()) return custom;
        }

        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();

        // Standard path: plugins/ItemsAdder/contents/
        File standard = new File(serverRoot, "plugins/ItemsAdder/contents");
        if (standard.isDirectory()) return standard;

        // Alternative: plugins/ItemsAdder/data/items_packs/
        File alt = new File(serverRoot, "plugins/ItemsAdder/data/items_packs");
        if (alt.isDirectory()) return alt;

        return standard;
    }

    /**
     * Determines the output directory for converted ItemsAdder files.
     *
     * @return the output directory
     */
    private File findOutputDir() {
        // Check config for custom output path
        String configPath = plugin.getConfig().getString("ia-output-path", "");
        if (!configPath.isEmpty()) {
            return new File(configPath);
        }

        // Default: plugins/MythicMobs/Items/ItemsAdder/
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        return new File(serverRoot, "plugins/MythicMobs/Items/ItemsAdder");
    }

    /**
     * Recursively finds all {@code .yml} files in a directory tree.
     *
     * @param dir   the directory to search
     * @param result the list to add found files to
     */
    private void findYmlFilesRecursive(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                findYmlFilesRecursive(f, result);
            } else if (f.getName().endsWith(".yml")) {
                result.add(f);
            }
        }
    }

    /**
     * Recursively searches for a file by name in a directory tree.
     *
     * @param dir      the directory to search
     * @param fileName the file name to find
     * @return the found file, or {@code null} if not found
     */
    private File findFileRecursive(File dir, String fileName) {
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File f : files) {
            if (f.isFile() && f.getName().equalsIgnoreCase(fileName)) {
                return f;
            }
            if (f.isDirectory()) {
                File found = findFileRecursive(f, fileName);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Gets the relative path of a file from a base directory.
     *
     * @param base the base directory
     * @param file the file to get the relative path for
     * @return the relative path string
     */
    private String getRelativePath(File base, File file) {
        String basePath = base.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(basePath)) {
            String rel = filePath.substring(basePath.length());
            if (rel.startsWith(File.separator)) rel = rel.substring(1);
            return rel;
        }
        return file.getName();
    }

    /**
     * Provides tab-completion suggestions for the first argument.
     * Suggests subcommands plus discovered file names, filtered by current input.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("all", "list", "help"));

            // Add available file names
            File iaDir = findItemsAdderDir();
            if (iaDir != null && iaDir.isDirectory()) {
                List<File> ymlFiles = new ArrayList<>();
                findYmlFilesRecursive(iaDir, ymlFiles);
                for (File f : ymlFiles) {
                    completions.add(f.getName().replace(".yml", ""));
                }
            }

            String prefix = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
