## Trailblazer – AI Coding Assistant Cheat‑Sheet

Essential project knowledge for fast, correct changes (keep <50 lines; update when behavior or structure shifts).

### Architecture
Modules: `trailblazer-api` (pure shared model: `PathData`, `Vector3d`, `Protocol`, `PathColors`), `trailblazer-plugin` (Paper bridge + fallback rendering + persistence + packet relay), `trailblazer-fabric` (client mod: recording, UI, rich rendering, local persistence). API must remain free of Bukkit/Fabric imports.

### Core Model
`PathData`: immutable identity (`pathId`), mutable `pathName`, owner + origin lineage (for shared copies), `points` (ordered), `sharedWith`, lazy `colorArgb` (assign via `PathColors.assignColorFor(pathId)` if zero – keep for backward compatibility).

### Networking
Plugin messaging channels (server side: `ServerPacketHandler` constructor). Handshake (`HandshakePayload`) marks a player as modded → server async loads + syncs paths (`sendAllPathData`). Only send packets to modded players (`isModdedPlayer`). Bump `Protocol.PROTOCOL_VERSION` only on incompatible wire change.

### Recording & Persistence
Client: `ClientPathManager.tickRecording` + `PathPersistenceManager` (world/server‑scoped directory). Autosave interval in config JSON. Server fallback: `RecordingManager` scheduled every 2 ticks; user controls via `/trailblazer record <...>` in `PathCommand` (unmodded only guidance for modded players).

### Sharing Semantics
Owner holds canonical copy; shared copies store origin IDs. Owner edits (rename/color) propagate by re‑sync (server sends updated list). Shared copies may be locally renamed by recipients (treat as alias only for them).

### Rendering
Client: `PathRenderer` + `RenderSettingsManager` + HUD (`RecordingOverlay`). Fallback (unmodded): server particle / marker via `PathRendererManager`. Add render mode → extend `RenderMode`, update server fallback + keybinding cycle (`KeyBindingManager`). Keep enum names stable (used in commands).

### Commands Pattern
Root `/trailblazer` (see `TrailblazerPlugin.registerCommands`). Manual switch in `PathCommand`; replicate style & early modded vs unmodded branch. Always validate ownership for destructive ops; shared copy rename only affects that copy.

### Packet Extension Checklist
1) Channel name `trailblazer:<action>` snake_case. 2) Mirror payload class both sides (`<ActionName>Payload`). 3) Prefer compact binary (length‑prefixed varints) unless JSON needed (list of paths). 4) Register in server `ServerPacketHandler` + client networking registrar. 5) Gate with `isModdedPlayer`. 6) Incompat change → bump protocol.

### Persistence / Compatibility
Never remove lazy color initialization until all historical files guarantee non‑zero color. Preserve deterministic ordering when returning path lists (sorting changes can cause client diff churn). Provide new `PathData` constructor overloads instead of breaking existing ones.

### Build / Tooling
Java 21 root. Build all: `gradlew build` → shaded plugin + remapped Fabric jar + API jar. Iterating plugin only: `gradlew :trailblazer-plugin:shadowJar` (relocates Gson/Netty under `com.trailblazer.plugin.libs.*`). Fabric module ensures `processIncludeJars` depends on API jar.

### Guardrails
No Bukkit/Fabric refs in API. No cyclical module deps. Avoid per‑tick spam for unchanged data (send deltas or batch). Keep packet + payload naming aligned. Use UUIDs (never rely on usernames for identity logic).

### Quick Extension Examples
Add simple ID event → copy pattern of `PathDeletedPayload`. Add metadata field → add to `PathData`, serialize in persistence + (if client needs it) include in sync JSON & metadata update packet.

### Manual Sanity Checklist (pre PR)
1) Modded + vanilla client both: record, stop, share, delete still work. 2) New packets appear only for modded players. 3) Shaded plugin jar has relocated deps (no raw `com/google/gson`).

---
Refine when recurring reviewer guidance appears. If something feels ambiguous, surface it in PR description for future inclusion here.