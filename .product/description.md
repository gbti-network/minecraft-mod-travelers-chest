# Traveler's Chest

A Minecraft server mod for Fabric 1.21.1 that introduces dynamic, refillable chests for enhanced gameplay and server management.

Chests are automatically replenished after a configurable cooldown period.

## Key Features

- **Automated Chest Refills**: Create chests that automatically replenish their contents after a customizable cooldown period
- **Admin Controls**: Simple command system for creating, editing, and managing special chests
- **Protection System**: Prevents unauthorized players from breaking or modifying managed chests
- **Flexible Configuration**: Customize cooldown times and contents for each chest individually
- **Double Chest Support**: Seamlessly works with both single and double chest configurations

## Technical Details

- Built for Fabric Loader (>=0.16.5)
- Requires Minecraft 1.21.1
- Java 21 compatibility
- Server-side mod
- Includes comprehensive configuration system
- Debug logging capabilities for troubleshooting

## Perfect For

- Server administrators looking to create dynamic loot systems
- Adventure maps requiring automated resource distribution
- RPG-style servers needing controlled item dispensing
- Community servers wanting to maintain balanced resource availability

## Installation Requirements

- Fabric Loader
- Fabric API
- Minecraft Server 1.21.1
- Java 21 or higher

## Commands
```bash
/travelers_chest create [cooldown_in_seconds]  # Create a new chest
/travelers_chest edit [new_cooldown_in_seconds]  # Modify existing chest
/travelers_chest destroy  # Remove a chest
```
