# Beacon Range (Fabric 1.21.1)

A utility mod that visualizes the range of beacons with color-coded transparent boxes. Perfect for technical building and ensuring full base coverage.

### Features
* **Visual Indicators:** Look at any active beacon to instantly see its effective range.
* **Level-Based Colors:**
  * **Level 1:** Blue (20 block range)
  * **Level 2:** Green (30 block range)
  * **Level 3:** Yellow (40 block range)
  * **Level 4:** Red (50 block range)
* **Pinning:** Open the Beacon GUI and click the **Range: OFF/ON** button to pin the box. Pinned boxes stay visible even when you walk away or look elsewhere.
* **Multi-Beacon Support:** Pin as many beacons as you need to visualize overlapping areas.

### Commands
Manage your pinned beacons via the chat interface:

* **`/beaconrange list`**
  * Displays a list of all currently pinned beacons and their coordinates.
* **`/beaconrange remove [coordinates]`**
  * Removes a specific beacon from your pinned list.
  * *Note:* This command supports **Tab-Completion**. Press Tab to cycle through the list of your pinned beacon coordinates automatically.
* **`/beaconrange clear`**
  * Instantly removes all pinned beacons from your view.

### Requirements
* Fabric Loader
* Fabric API


### Upcomming
* Color Changing
* Better Button
* Better Command System (Clickable Text instead of List)
* Support for Various Extender Mods
* Support for Older and Newer Minecraft Versions