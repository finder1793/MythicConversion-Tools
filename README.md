# MythicConverter

A Bukkit/Paper plugin that converts [MMOItems](https://www.spigotmc.org/resources/mmoitems.39267/) item configuration files into [MythicCrucible](https://mythiccraft.io/index.php?pages/official-mythiccrucible-wiki/) / [MythicMobs](https://mythiccraft.io/index.php?pages/official-mythicmobs-wiki/) item format.

## Overview

MythicConverter reads MMOItems YAML item files (e.g. `SWORD.yml`, `ARMOR.yml`) and produces equivalent MythicCrucible-compatible YAML output. It handles:

- **Vanilla attributes** (attack damage, armor, movement speed, etc.) mapped to MythicMobs Attribute format with correct equipment slots
- **Custom stats** (critical strike chance, lifesteal, elemental damage, etc.) mapped to MythicMobs Stats format
- **Percent-based stats** automatically converted from whole numbers (e.g. `25`) to decimals (e.g. `0.25`)
- **Enchantments**, **custom model data**, **display names**, **lore**, **dye colors**, **skull textures**, **hide flags**, and **unbreakable** status
- **Unsupported features** (abilities, gem sockets, soulbound, item sets, permanent effects) are preserved as YAML comments with migration guidance

## Requirements

- Java 21+
- Paper API 1.21.4+
- MMOItems installed (for source item files)
- MythicMobs / MythicCrucible installed (for output destination)

## Building

```bash
gradle build
```

The compiled JAR will be output to `build/libs/MythicConverter-<version>.jar`.

> **Note:** Do not run `gradle clean` on this project. Use incremental `gradle build` only.

## Installation

1. Place the compiled JAR into your server's `plugins/` folder
2. Start or restart the server
3. The plugin will generate a default `config.yml` in `plugins/MythicConverter/`

## Commands

| Command | Description |
|---|---|
| `/convertitems all` | Convert all MMOItems type files |
| `/convertitems <TYPE>` | Convert a specific type (e.g. `SWORD`, `ARMOR`) |
| `/convertitems list` | List available MMOItems type files |
| `/convertitems reload` | Reload config and stat mappings |
| `/convertitems help` | Show in-game help |

**Aliases:** `/mconvert`, `/mi2mc`

**Permission:** `mythicconverter.convert` (default: op)

## Configuration

All mappings are fully configurable in `config.yml`. The plugin ships with sensible defaults covering all standard MMOItems stats.

### Paths

| Key | Description | Default |
|---|---|---|
| `mmoitems-path` | Custom path to MMOItems item directory | Auto-detects `plugins/MMOItems/item/` |
| `output-path` | Output directory for converted files | `plugins/MythicMobs/Items/` |

If `mmoitems-path` is set, it takes priority over auto-detection.

### Attribute Mappings

Maps MMOItems stat keys to MythicMobs vanilla attribute names. Values can optionally include an operation override:

```yaml
attribute-mappings:
  attack-damage: ATTACK_DAMAGE
  attack-speed: ATTACK_SPEED          # defaults to ADD_SCALAR in MythicMobs
  movement-speed: MOVEMENT_SPEED      # defaults to ADD_SCALAR in MythicMobs
  armor: ARMOR
```

### Stat Mappings

Maps MMOItems stat keys to MythicMobs custom stat keys (MythicLib stats):

```yaml
stat-mappings:
  critical-strike-chance: CriticalStrikeChance
  lifesteal: Lifesteal
  fire-damage: FireDamage
```

### Percent Stats

Stats listed here are stored as whole percentages in MMOItems (e.g. `25` = 25%) and will be divided by 100 in the output:

```yaml
percent-stats:
  - critical-strike-chance
  - lifesteal
  - dodge-rating
```

### Slot Mappings

Maps MMOItems type names to MythicMobs equipment slots. Empty values indicate no slot (attributes are written without a slot wrapper):

```yaml
slot-mappings:
  SWORD: MainHand
  SHIELD: OffHand
  HELMET: Head
  CHESTPLATE: Chest
  LEGGINGS: Legs
  BOOTS: Feet
  ACCESSORY: ""        # no slot
  CONSUMABLE: ""       # no slot
```

## Output Format

The converter produces MythicCrucible-compatible YAML. Example output:

```yaml
FIRE_SWORD:
  Material: DIAMOND_SWORD
  Display: '<red>Fire Sword'
  Model: 10001
  Lore:
  - '<gray>A blazing blade of fire'
  Enchantments:
  - SHARPNESS:5
  - FIRE_ASPECT:2
  Attributes:
    MainHand:
      ATTACK_DAMAGE: 12
      ATTACK_SPEED: 1.6
  Stats:
  - CriticalStrikeChance 0.25
  - FireDamage 0.15
  Options:
    Unbreakable: true
```

Features that cannot be directly converted (abilities, gem sockets, etc.) are included as YAML comments with guidance on how to recreate them in MythicMobs/MythicCrucible.

## Project Structure

```
MythicConverter/
├── src/main/java/com/mythicconverter/
│   ├── MythicConverterPlugin.java   # Plugin entry point and lifecycle
│   ├── ConvertCommand.java          # Command handler and tab completion
│   ├── ItemConverter.java           # Core conversion logic
│   └── StatMappings.java            # Config-driven mapping tables
├── src/main/resources/
│   ├── config.yml                   # Default configuration with all mappings
│   └── plugin.yml                   # Bukkit plugin descriptor
├── build.gradle
└── settings.gradle
```

## License

Private project.
