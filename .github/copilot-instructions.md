## Trailblazer – AI Coding Assistant Instructions

Purpose: Help you make correct, idiomatic changes fast. This repo is a multi‑module Gradle (Java 21) project that ships both a Fabric client mod and a Paper server plugin sharing a small cross‑module API.

### Module Overview
- `trailblazer-api`: Pure Java (no MC deps). Shared immutable-ish data + protocol constants: `PathData`, `Vector3d`, `PathColors`, `Protocol`. Only put versioned, binary‑safe model logic here.
- `trailblazer-fabric`: Client mod (Fabric Loom). Handles local recording, rendering, network handshakes, persistence, keybinds, UI, and path visibility.
- `trailblazer-plugin`: Paper plugin. Persists player paths server‑side, runs controlled recording tasks, exposes `/trailblazer` command, and (eventually) handles packet relay.

### Core Domain: Path Lifecycle (Client)
1. Recording starts via keybinding/command → `ClientPathManager.startRecordingLocal()` creates a `PathData` name `Path-N`, increments internal counter (`recalculateNextPathNumber()` ensures uniqueness scanning existing local/imported paths whose names start with `Path-`).
2. Each client tick (`TrailblazerFabricClient.registerClientTick`) calls `ClientPathManager.tickRecording()`, appending a new `Vector3d` when moved ≥0.2 blocks (dist² ≥ 0.04).
3. Autosave every `autosaveIntervalSeconds` (config) calls `PathPersistenceManager.saveDirty()`; individual modifications invoke `markDirty()` for local/imported (non server-backed) paths only.
4. Thinning: If point count exceeds `maxPointsPerPath` (config), `PathPersistenceManager.enforcePointLimit()` keeps every Nth point + final point.
5. Sharing/import: Imported or shared server paths tagged via `ClientPathManager.PathOrigin` control persistence + visibility defaults.

### Persistence (Client Singleplayer / No Plugin)
- Stored under `<world>/trailblazer/paths/` or per‑server directory for remote servers without plugin.
- One JSON per path (`<uuid>.json`) plus `index.json` listing known IDs (allows extra orphan detection scanning the directory).
- Schema handled by inner `PathFileRecord` (see `PathPersistenceManager`): includes origin fields for provenance; color stored if non‑zero.
- Modify persistence: keep atomic write pattern (write `.tmp`, then `Files.move` with `ATOMIC_MOVE`). Always update `index.json` after adds/removes.

### Networking & Protocol
- `Protocol.PROTOCOL_VERSION` (currently 1) + `Protocol.Capability` bit flags (live updates, shared storage, permissions, canonical colors, server thinning, multidimension filter).
- Client handshake: on join, `TrailblazerFabricClient.registerHandshakeSender()` sends `HandshakePayload` if channel available.
- Live path updates: `ClientPathManager.updateLivePath()` maintains an ephemeral live path (UUID `00000000-0000-0000-0000-000000000001`). Stopping clears via `stopLivePath()`.
- When adding a new packet/payload: define payload class in `fabric.networking.payload.[c2s|s2c]`, register in `TrailblazerNetworking.registerPayloadTypes()`, update server handler mirror in plugin (future), and if `PathData` shape changes, add new constructor variant preserving existing serialization assumptions.

### Rendering & Visual Settings
- Renderer: `PathRenderer` (initialized in `TrailblazerFabricClient.onInitializeClient`) pulls visible paths via `ClientPathManager.getVisiblePaths()` and uses `RenderSettingsManager` + per‑player config.
- Colors: `PathData.getColorArgb()` lazily assigns using `PathColors.assignColorFor(UUID)` if zero (hash → palette index). Respect this lazy contract; do not pre‑assign 0 unless you want auto‑palette.
- Unique naming: `ClientPathManager.uniquePathName()` ensures imported/shared duplicates get suffix `(2)`, `(3)`, etc.

### Configuration
- Client JSON: `TrailblazerClientConfig` stored in Fabric config dir (`trailblazer-client.json`). Save via `config.save()` on disconnect. If adding a field: supply sensible default; backwards compatibility relies on Gson defaulting.

### Server Plugin Structure
- Main: `TrailblazerPlugin` sets static `instance` + `pluginLogger`; initializes managers: `PathDataManager`, `PlayerRenderSettingsManager`, `ServerPacketHandler`, `PathRendererManager`, `RecordingManager`.
- Recording tick scheduled every 2 ticks; quitting players trigger cleanup (`onPlayerQuit`). Maintain this frequency if extending to avoid server load spikes.
- Commands: `/trailblazer` central command with subcommands (see executor + tab completer classes). When adding subcommands, register inside existing command tree rather than new root commands.
- Persistence (server) currently basic (see `PathDataManager`); if you change file layout, preserve legacy reads or write a one‑time migration.

### Conventions & Guardrails
- Always treat `PathData` list fields (`points`, `sharedWith`) as mutable on the owning side; external APIs pass defensive copies only when persisting.
- Use `markDirty(pathId)` after any in‑place mutation of local/imported `PathData` points or metadata to ensure autosave.
- Never persist server‑backed (`SERVER_OWNED` / `SERVER_SHARED`) paths locally—`PathPersistenceManager.markDirty()` already filters; keep it that way.
- Handle naming collisions only through existing helper (`uniquePathName`), not ad hoc string concatenation.
- When changing color logic, update both `assignColorFor` and `nameOrHex` mappings; keep palette ARGB format (alpha in high byte).

### Build & Tasks
- Root Gradle sets shared group/version, Java 21. Build everything: `./gradlew build`.
- Fabric mod jar (remapped) produced via Loom standard tasks; ensure `trailblazer-api` jar is built before `processIncludeJars` (already enforced by explicit `dependsOn`).
- Plugin shaded jar: `trailblazer-plugin:shadowJar` (classifier removed so final artifact is in `libs/Trailblazer-plugin-1.0.0.jar`). Relocations for Gson + Netty live in plugin build script—add further libs there to avoid namespace clashes.

### SAFE CHANGE CHECKLIST (mental)
1. Does it alter `PathData` fields? → Update persistence + network (both sides) + color assignment if relevant.
2. Does it change recording cadence or distance threshold? → Reconsider autosave / thinning limits and performance.
3. Introducing new threading? → Current code is largely single‑threaded (client tick / server main); keep file IO atomic but synchronous.

Ask if anything beyond this needs clarification or if a section is missing for a feature you plan to modify.