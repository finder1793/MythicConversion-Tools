# MythicConverter

A Minecraft Java plugin that converts **MMOItems** item configuration YAML files into **MythicCrucible** (MythicMobs) item configuration format.

## Requirements

- Java 17+
- Spigot/Paper 1.20+
- MMOItems installed (so its item config files exist in `plugins/MMOItems/item/`)

MythicCrucible and MythicMobs do **not** need to be installed for the conversion to run — only the output files are in their format.

## Building

```bash
cd MythicConverter
mvn clean package
```

The compiled JAR will be at `target/MythicConverter-1.0.0.jar`.

## Installation

1. Place `MythicConverter-1.0.0.jar` into your server's `plugins/` folder.
2. Start/restart the server.
3. Use the `/convertitems` command.

## Commands

| Command | Description |
|---|---|
| `/convertitems all` | Convert all MMOItems type files |
| `/convertitems <TYPE>` | Convert a specific type (e.g. `SWORD`, `ARMOR`) |
| `/convertitems list` | List available MMOItems type files |
| `/convertitems help` | Show help |

**Aliases:** `/mconvert`, `/mi2mc`

**Permission:** `mythicconverter.convert` (default: op)

## Output

Converted files are saved to `plugins/MythicConverter/output/`.  
Copy them into `plugins/MythicCrucible/Items/` (or `plugins/MythicMobs/Items/`) to use.

## What Gets Converted

| MMOItems Field | MythicCrucible Equivalent |
|---|---|
| `material` | `Material` |
| `name` | `Display` |
| `lore` | `Lore` |
| `custom-model-data` | `Model` |
| `enchants` | `Enchantments` |
| `attack-damage`, `armor`, `movement-speed`, etc. | `Attributes` (per slot) |
| `critical-strike-chance`, `lifesteal`, `defense`, etc. | `Stats` |
| `unbreakable` | `Options.Unbreakable` |
| `dye-color` | `Options.Color` |
| `hide-enchants` | `Hide: ENCHANTS` |
| `required-level` | `EquipLevel` |
| `element` (fire damage, etc.) | `Stats` (elemental) |

## What Needs Manual Conversion

These MMOItems features have no direct MythicCrucible equivalent and are logged as comments in the output:

- **Abilities** → Must be recreated as MythicMobs Skills
- **Gem Sockets** → Use MythicCrucible Augments
- **Soulbound** → Use MythicMobs Skills/Conditions
- **Item Sets** → Use MythicCrucible Equipment Sets
- **Item Tiers** → Manual configuration
- **Permanent Potion Effects** → Use MythicMobs Skills with `~onEquip` trigger
- **Two-Handed** → No direct equivalent

## Configuration

Edit `plugins/MythicConverter/config.yml`:

```yaml
# Custom path to MMOItems item directory (leave empty to auto-detect)
mmoitems-path: ""
```

## License

Free to use and modify.
