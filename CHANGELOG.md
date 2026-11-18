# Changelog

## 2.1.0
- Made for Minecraft 1.21.4
- Conduits: look at a conduit to see its range as a sphere - 32 to 96 blocks, depending on its frame. With a full frame a red box shows where it attacks mobs
- New `/beaconrange pin`: pins the beacon or conduit you look at (the pin key does the same)
- New lock button next to "Pin range": turn off automatic unpinning on servers that hide blocks (anti-xray shows deepslate instead of your beacon), so your pins stay. Also in the settings
- `/beaconrange list` marks pins whose block is not there right now
- The tour and the pictures now show that the range also reaches below the beacon - new, clearer pictures without overlapping ranges
- Look at a beacon's pyramid (or a conduit's frame) to see its range - handy when you stand below a beacon and only see the pyramid
- Fixed: a pin was removed as soon as the server showed another block in its place (anti-xray) - now it is only removed when the beacon disappears while you are near it
- Fixed: a beacon whose beam is blocked was shown as active, although it gives no effects
- Fixed: on servers, "Pin range" could pin a neighbouring beacon when the beacon screen opened a moment late
- Fixed: a half-loaded pyramid could be remembered with a too small level, and a dismantled beacon came back at full size from memory
- Fixed: a crash while saving could lose all pins, and one broken entry dropped every pin
- "Unpin all" in the settings asks first; settings are also saved when the screen closes unexpectedly
- Walls are no longer drawn beyond your render distance, where they glowed through the fog

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
- Made for Minecraft 1.21.11

## 1.0.2
- `/beaconrange list` shows all pinned beacons - click the red ✖ next to one to unpin it

## 1.0.1
- `/beaconrange clear` unpins all beacons, `/beaconrange remove <x,y,z>` unpins one

## 1.0.0
- First release: look at a beacon to see its range as a colored box, pin it with the button in the beacon screen
