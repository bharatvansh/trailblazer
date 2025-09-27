# Trailblazer Copilot Instructions

## Project Overview
Trailblazer is a Minecraft mod system for recording and sharing player paths, consisting of three Gradle modules:
- `trailblazer-api`: Shared Java library with core data structures and protocols
- `trailblazer-fabric`: Fabric client mod for recording, rendering, and local persistence
- `trailblazer-plugin`: Paper/Purpur server plugin for shared storage and synchronization

## Architecture Patterns

### Multi-Module Shared API Design
- **trailblazer-api** contains pure Java classes used by both client and server
- Key shared classes: `PathData` (core path structure), `Protocol` (network constants), `Vector3d` (3D coordinates)
- Client mod works standalone but auto-detects server plugin presence for enhanced features
- **Example**: `PathData` uses `serialVersionUID = 2L` for JSON serialization compatibility

### Client-Server Communication Protocol
- Uses Fabric's networking API with custom payload types
- Protocol versioning with capability bitmasks (`Protocol.Capability.LIVE_UPDATES`, etc.)
- Handshake detects server features; client gracefully degrades without plugin
- **Example**: Server advertises `SHARED_STORAGE` capability to enable sharing features

### Path Persistence Strategy
- Client: Per-world folders for singleplayer (`saves/<world>/trailblazer/paths/`) or server cache (`.minecraft/trailblazer_client_servers/<serverKey>/`)
- Server: Authoritative storage with player permissions and sharing
- JSON format with `schemaVersion=1` and `index.json` for metadata
- **Example**: Paths stored as `<uuid>.json` with automatic orphan cleanup

### Rendering System Architecture
- Three display modes: particle trails, spaced markers, directional arrows
- Client-side rendering with performance profiling (`balanced` default)
- Server broadcasts live updates when `LIVE_UPDATES` capability present
- **Example**: `PathRenderer` manages particle spawning based on `RenderSettingsManager` configuration

## Development Workflows

### Building the Project
```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :trailblazer-fabric:build
./gradlew :trailblazer-plugin:build

# Run client with Minecraft
./gradlew :trailblazer-fabric:runClient

# Build plugin JAR (includes shadowed dependencies)
./gradlew :trailblazer-plugin:shadowJar
```

### Testing Client Mod
- Use `./gradlew :trailblazer-fabric:runClient` to launch Minecraft with the mod
- Test recording with keybind (default: unassigned) or `/tblocal` commands
- Verify standalone mode (no server plugin) and enhanced mode (with plugin)
- Check persistence in `run/saves/<world>/trailblazer/paths/`

### Testing Server Plugin
- Drop built JAR in `plugins/` folder of Paper/Purpur server
- Use `/path` command for server-side path management
- Test client-server synchronization and sharing features

## Code Patterns & Conventions

### Data Structure Design
- `PathData` uses defensive copying and null validation in constructors
- UUID-based identification with creation timestamps
- Color assignment via `PathColors.assignColorFor(pathId)` for consistency
- **Example**: Always use `Objects.requireNonNull()` for required constructor parameters

### Networking Implementation
- Custom payload classes in `networking.payload.*` packages
- Client-to-server (`c2s`) and server-to-client (`s2c`) separation
- Packet handlers registered via `TrailblazerNetworking.registerPayloadTypes()`
- **Example**: Handshake payload checks for `trailblazer:handshake` channel presence

### Configuration Management
- Client: JSON config in `config/trailblazer-client.json` with fields like `maxPointsPerPath: 5000`
- Server: Standard Bukkit config with player-specific render settings
- Hot-reload support for render settings without restart
- **Example**: `TrailblazerClientConfig.load()` handles file creation with defaults

### Mixin Usage
- Minimal mixins for UI integration (currently only `EntryListWidgetMixin`)
- Package: `com.trailblazer.fabric.mixin`
- JSON configuration in `src/main/resources/trailblazer.mixins.json`
- **Example**: Use `@Inject` for method hooks, avoid `@Overwrite` when possible

### Dependency Management
- API module has zero external dependencies (pure Java)
- Fabric mod uses Fabric API and includes API module via `include project(':trailblazer-api')`
- Plugin shadows Gson and Netty with relocation to avoid conflicts
- **Example**: `relocate 'com.google.gson', 'com.trailblazer.plugin.libs.gson'`

## Key Files to Reference
- `PathData.java`: Core path data structure and serialization
- `Protocol.java`: Network protocol constants and versioning
- `TrailblazerFabricClient.java`: Client mod initialization and manager setup
- `TrailblazerPlugin.java`: Server plugin lifecycle and manager initialization
- `build.gradle` files: Module-specific build configurations and dependencies</content>
<parameter name="filePath">c:\MinecraftModding\Trailblazer\Trailblazer\.github\copilot-instructions.md