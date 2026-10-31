# Beacon Range

A client-side Fabric mod for Minecraft 1.21.11 and 26.3 that shows exactly where the effects of a beacon reach.

![All four levels](images/levels.png)

## Features

- **Look at a beacon** - even from up to 96 blocks away - to see its range: glowing edges, walls that fade out towards the sky and a line around the range at your height. Level and range show under your crosshair.
- **Exact range**: 20 / 30 / 40 / 50 blocks to each side for level 1 to 4, just as far down and all the way up to the sky - the same box Minecraft uses.
- **Pin beacons** with the *Pin range* button in the beacon screen (or a key) to keep their range visible. Pins are saved per world and server and survive unloaded chunks, dimension changes and rejoining. Broken beacons are unpinned automatically.
- **Colors** per level (aqua, green, gold, red by default) or the color of the beam.
- **Settings** through [Mod Menu](https://modrinth.com/mod/modmenu): wall style and strength, edges, height line, crosshair info, colors.
- **Keys** (unbound by default): pin the beacon you look at, hide all ranges, open the settings.
- English and German, welcome tour on first start.

## Commands

| Command | What it does |
| --- | --- |
| `/beaconrange list` | Pinned beacons in this dimension with level, coordinates and distance; click ✖ to unpin |
| `/beaconrange remove x y z` | Unpin one beacon (Tab completes) |
| `/beaconrange clear` | Unpin all beacons in this world |

## Files

Settings and pins live in `config/KCMods/beaconrange/` (`config.json`, `pins.json`).

## Building

Each Minecraft version has its own folder and branch: `1.21.11` (branch `main`) and `26.3` (branch `26.3`).

```
./gradlew build                 # jar in build/libs
./gradlew runClientGameTest     # automated test in a real client
```

Requires [Fabric API](https://modrinth.com/mod/fabric-api). MIT license.
