# Changelog

## 2.0.0
- The range now matches Minecraft exactly: 20 to 50 blocks to each side and all the way up to the sky (before, the box stopped as high above the beacon as it reached to the sides)
- New look: glowing edges, walls that fade out towards the sky and a line around the range at your height
- Look at a beacon from up to 96 blocks away to see its range - level and range show under your crosshair
- Pinned beacons are saved per world and server and stay pinned when you leave, change dimension or rejoin
- Fixed: pinned beacons were lost when you walked away and their chunk unloaded
- New "Pin range" button above the beacon screen, with level and range next to it
- New keys (unbound by default): pin the beacon you look at, hide all ranges, open the settings
- Settings in Mod Menu: a color per level or the color of the beam, wall style and strength, edges and the height line
- `/beaconrange list` shows level, coordinates and distance; `/beaconrange remove` takes `x y z`
- A broken beacon is unpinned automatically
- German translation
- Welcome tour on first start and a "What's new" screen after updates
- New icon
- Made for Minecraft 26.3

## 1.0.2
- `/beaconrange list` shows all pinned beacons - click the red ✖ next to one to unpin it

## 1.0.1
- `/beaconrange clear` unpins all beacons, `/beaconrange remove <x,y,z>` unpins one

## 1.0.0
- First release: look at a beacon to see its range as a colored box, pin it with the button in the beacon screen
