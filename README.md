# Path Sharing (Trailblazer)

A Minecraft mod and server plugin for recording, visualizing, and sharing player raw movement paths. The whole idea is to give players something more than just coordinates or waypoints to save or share with each other. This Allows players to literally record raw path trail that they are walking, and then save, view, and share those paths with other players on the server. A much more practical solution when reaching to a checkpoint in a complex cave system, or navigating to a base through a dense forest.

The system consists of:
*   A Bukkit/Paper/Purpur/Spigot plugin that can work independently in the server or along with the Fabric mod to add enhanced features and logic.
*   A Fabric mod that too can work independently or alongside the server plugin to provide a richer client-server experience.
*   A shared API module for common data structures. It is a dependency for both the server plugin and the client mod. Plays key role in the Client + Server Scenario.

## Features

*   **Path Recording:** Record your paths as you walk in real-time.
*   **Path Visualization:** View paths with client-side rendering beautiful trails or server-side basic particles for vanilla clients.
*   **Path Management:** Save, load, rename, and color-code your paths.
*   **Sharing:** Share your paths with other players on the server. Only possible when server side plugin is installed.
*   **Client & Server Fallback:** Works for players with or without the client mod installed. Works for players on servers with or without the server plugin installed. Works best when both are installed.

## Getting Started

### Download Links
*  Modrinth: [Link]
*  Curseforge: [Link]

### Prerequisites

*   Java 21 or newer
*   Minecraft: Java Edition version Compatible Version
*   Either:
*       (For Server) PaperMC or a compatible fork
*       (For Client) Fabric Loader
*       (For Client + Server) Both of the above
*   Fabric API (if using the Fabric mod)

### Installation

1.  **API:** The `trailblazer-api` module is a dependency and does not need to be installed separately.
2.  **Server Plugin:**
    *   Download the latest `trailblazer-plugin-*.jar` from the releases page of the Curseforge or Modrinth.
    *   Place it in your server's `plugins/` directory.
    *   Restart the server.
3.  **Client Mod:**
    *   Download the latest `trailblazer-fabric-*.jar` from the releases page of the Curseforge or Modrinth.
    *   Place it in your Minecraft's `mods/` directory.
    *   Ensure you have Fabric API installed.
    *   Start Minecraft with the Fabric Loader profile.

## The 3 Scenarios

### **Full Experience:** the Best of Both Worlds!
When the Player has fabric client mod installed and the server has the plugin installed.

#### This Setup unlocks:
- ✅ All Basic Funtionalities like Recording, Saving, Loading, Viewing, Changing Color, Renaming, Deleting, Listing Paths.
- ✅ Sharing Paths with other players on the server.
- ✅ Extensive Path management UI.
- ✅ Commands for all functionalities.
- Enhanced Features like:
  - ✅ Beautiful path rendering.
  - ✅ Keybinds for quick actions.
  - ✅ Live Path rendering when recording.
  - ✅ And more...

### **Client Only:** The Seconds Best
When the Player has fabric client mod installed but the server does not have the plugin installed.

#### This Setup unlocks:
- ✅ All Basic Funtionalities like Recording, Saving, Loading, Viewing, Changing Color, Renaming, Deleting, Listing Paths.
- ❌ Sharing Paths with other players on the server.
- ✅ Extensive Path management UI.
- ✅ Commands for all functionalities.
- Enhanced Features like:
  - ✅ Beautiful path rendering.
  - ✅ Keybinds for quick actions.
  - ✅ Live Path rendering when recording.
  - ❌ And more...

### **Server Only:** Basic Functionality
When the Player does not have fabric client mod installed but the server has the plugin installed.

#### This Setup unlocks:
- ✅ All Basic Funtionalities like Recording, Saving, Loading, Viewing, Changing Color, Renaming, Deleting, Listing Paths.
- ✅ Sharing Paths with other players on the server.
- ❌ Extensive Path management UI.
- ✅ Commands for all functionalities.
- Enhanced Features like:
  - ❌ Beautiful path rendering.
  - ❌ Keybinds for quick actions.
  - ❌ Live Path rendering when recording.
  - ❌ And more...

## Usage

Trailblazer exposes a single root command `/trailblazer` with many subcommands. The client mod provides richer, interactive commands and keybinds; the server plugin supplies a fallback for unmodded clients. Use quotes around path names that contain spaces.

### Client UI - Works in Both Client Only and Client + Server Scenarios
- Press `M` to open the Trailblazer UI menu.
- The Trailblazer UI provides an intuitive interface for managing paths, with options to create, edit, and delete paths easily.

### Client Keybinds - Works in Both Client Only and Client + Server Scenarios
- **R Key**: Toggle local recording.
- **G Key**: Cycle render mode.
- **M Key**: Open the Trailblazer UI menu.

### Client Commands - Works for Both Client Only and Client + Server Scenarios
These commands are available when the Fabric mod is installed. They use client-side rendering and local persistence, with server sync where applicable.

- `/trailblazer help` — Show client-side help.
- `/trailblazer record` — Toggle local recording (shortcut).
- `/trailblazer record start` — Start a new local recording.
- `/trailblazer record stop` — Stop and save the current local recording.
- `/trailblazer record cancel` — Cancel and discard the current local recording.
- `/trailblazer record status` — Show current recording status.
- `/trailblazer list` — List your local and shared paths.
- `/trailblazer view "<path name>"` — Show a path using client renderer.
- `/trailblazer hide ["<path name>"]` — Hide one path, or omit the name to hide all visible paths.
- `/trailblazer info "<path name>"` — Show start/end coordinates for a path.
- `/trailblazer rename "<old name>" "<new name>"` — Rename a local path (syncs to server if server-owned).
- `/trailblazer delete "<path name>"` — Delete locally or request server deletion for server-owned paths.
- `/trailblazer color "<path name>" <colorName|#RRGGBB>` — Change color (local update; syncs to server if server-owned).
- `/trailblazer share "<path name>" <player1,player2,...>` — Send share request to server.
- `/trailblazer rendermode <trail|markers|arrows>` — Change client render mode.
- `/tbl` — Alias for `/trailblazer`.

### Server-Side Commands - Works in Only Server-Side Scenario
These commands are available when the server plugin is installed. They use server-side rendering and persistence.

- `/trailblazer help` — Show server-side help.
- `/trailblazer record start [name]` — Start server-side recording.
- `/trailblazer record stop` — Stop and save the current recording.
- `/trailblazer record cancel` — Cancel and discard the current recording.
- `/trailblazer record status` — Show current recording status.
- `/trailblazer list` — List your saved paths.
- `/trailblazer view "<path name>"` — Show a path using server particle rendering.
- `/trailblazer hide` — Hide the current path.
- `/trailblazer info "<path name>"` — Show start/end coordinates for a path.
- `/trailblazer rename "<old name>" "<new name>"` — Rename a path you own.
- `/trailblazer delete "<path name>"` — Delete a path you own.
- `/trailblazer color "<path name>" <colorName|#RRGGBB>` — Change the color of a path.
- `/trailblazer spacing <blocks>` — Set marker spacing for server rendering (e.g., `3.0`).
- `/trailblazer share "<path name>" <player1,player2,...>` — Share a path with other players.
- `/trailblazer rendermode <trail|arrows>` — Change server render mode.
- `/tbl` — Alias for `/trailblazer`.


Notes:
- Use `/trailblazer help` in-game for the full, contextual list and tab-completion suggestions.
- Client commands sync with the server when managing server-owned paths.

## Building from Source

This project uses a multi-module Gradle setup.

1.  Clone the repository:
    ```sh
    git clone https://github.com/bharatvansh/Path-Sharing
    ```
2.  Navigate to the project directory:
    ```sh
    cd Trailblazer
    ```
3.  Build the project using the Gradle wrapper:
    ```sh
    ./gradlew build
    ```
4.  The compiled JARs will be located in the `build/libs` directory of each submodule (`trailblazer-plugin` and `trailblazer-fabric`).

## Contributing

Any contributions you make will be **greatly appreciated** 🛐🛐

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

## License

Distributed under the MIT License. See `LICENSE` for more information.

