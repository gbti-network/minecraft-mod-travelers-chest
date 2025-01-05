# Traveler's Chest

A Minecraft mod that enables administrators to manage refillable chests dynamically. Designed for multiplayer servers using Fabric for Minecraft 1.21.1, the mod provides control over chest content, cooldowns, and interactions.

## ✨ Features

- Refillable chests with customizable cooldowns
- Administrator tools for creating, editing, and destroying refillable chests
- Protected chests to prevent unauthorized modifications or destruction

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the latest version of Traveler's Chest from the [releases page](https://github.com/gbti-network/minecraft-mod-travelers-chest/releases).
3. Place the `.jar` file in your server's `mods` folder.
4. Start your server to generate configuration files.

## 🔧 Configuration

### Config Files

- `config/travelers-chest/travelers_chest_config.txt`
  - **Example Contents**:
    ```properties
    debug_logging=false
    ```
  - Enables or disables debug logging for detailed troubleshooting.

- `config/travelers-chest/travelers_chests.json`
  - Stores data about all managed chests, including their cooldowns, items, and positions.

## 🛠️ Usage

### Commands
Administrators can manage chests using commands:

1. **Create a Traveler's Chest**:
   - Command: `/travelers_chest create [cooldown_in_seconds]`
   - Default cooldown: 30 minutes (1800 seconds)
   - Example: `/travelers_chest create 600` creates a chest with a 10-minute cooldown.

2. **Edit a Traveler's Chest**:
   - Command: `/travelers_chest edit [new_cooldown_in_seconds]`
   - Updates the cooldown of an existing chest.
   - Example: `/travelers_chest edit 1200` sets the cooldown to 20 minutes.

3. **Destroy a Traveler's Chest**:
   - Command: `/travelers_chest destroy`
   - Removes a Traveler's Chest and its data.

### Chest Functionality

- **Protected Chests**: Players without sufficient permissions cannot break or modify these chests.
- **Refill Mechanism**: Automatically replenishes chest contents based on saved configurations after the cooldown period expires.
- **Double Chest Support**: Handles single and double chests seamlessly.

### Debug Logging

Enable debug logging in `travelers_chest_config.txt` for detailed logs of mod operations, such as chest refills and command execution:
```properties
debug_logging=true
```

Logs are output to the server console.

## 📄 JSON Structure

Chests are stored in `travelers_chests.json` with the following schema:
- **lastRefreshed**: Timestamp of the last refill
- **chestCooldown**: Cooldown in seconds
- **worldKey**: World identifier
- **items**: List of stored items
- **itemPositions**: Item slot positions

Example:
```json
{
  "123456789": {
    "lastRefreshed": 12345678,
    "chestCooldown": 1800,
    "worldKey": "minecraft:overworld",
    "items": [{"nbt": "{id:\"minecraft:diamond\",Count:1b}"}],
    "itemPositions": [0]
  }
}
```

## 🤝 Support

To support the development of Traveler's Chest, please join the [GBTI Network](https://gbti.network/) for access to:
- 🚀 Exclusive tools and resources
- 🛠️ Development support
- 🎮 Private Minecraft Server

### Other Ways to Contribute

- ⭐ Star the project on [GitHub](https://github.com/gbti-network/minecraft-mod-travelers-chest)
- 🐛 Report issues via the [Issue Tracker](https://github.com/gbti-network/minecraft-mod-travelers-chest/issues)
- 💡 Submit improvements via [Pull Requests](https://github.com/gbti-network/minecraft-mod-travelers-chest/pulls)

## 📄 License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for details.