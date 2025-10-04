# Trailblazer – AI Coding Assistant Instructions

These instructions capture the current architecture, conventions, and workflows of the Trailblazer repository so an AI assistant can contribute productively without rediscovery. Keep answers specific to this codebase; avoid generic Java/Minecraft advice.

## Core Principles

1.  **Extended Thinking**: For complex problems requiring deep analysis, use your **extended thinking mode** to reason about the solution before acting. Take the time necessary to build a solid plan and anticipate potential issues.
2.  **Critical Reasoning and Honesty**: Do not assume the user's request is perfect. Identify and question false premises, acknowledge the limits of your knowledge, and if a requirement is ambiguous or unsafe, ask clarifying questions instead of making assumptions. Your goal is maximum autonomy, but clarity is crucial for success.
3.  **Iterative Self-Improvement**: Don't settle for the first functional solution. After testing, reflect on the quality of your work. Can it be more robust, efficient, or secure? Iterate on your own solution to improve it, just as you would to improve a framework or process.
4.  **Security Focus**: Security is paramount. In all coding tasks, proactively consider potential vulnerabilities and security best practices. Write code that is not only functional but also secure.


## 1. Architecture Overview
Multi-module Gradle project:
- `trailblazer-api`: Pure Java shared model layer (no Bukkit/Fabric deps). Contains immutable-ish domain objects (`PathData`, `Vector3d`, `PathColors`, `Protocol`). All cross‑environment data serialization should rely on these classes.
- `trailblazer-plugin`: Paper/Bukkit server plugin. Responsibilities: persistence of paths (JSON via Gson), command handling, server→client plugin messaging bridge, path recording & rendering management, sharing logic, permission / ownership enforcement. Depends on `trailblazer-api`.
- `trailblazer-fabric`: Client-side Fabric mod. Responsibilities: client UI, rendering, local persistence, keybinds, receiving server sync, issuing C2S payloads (handshake, metadata updates, share requests). Depends on and also *includes* (`include project(':trailblazer-api')`) the API so it is bundled inside the mod jar.

Separation rule: NEVER introduce Bukkit/Fabric/Minecraft classes into `trailblazer-api`; keep it platform agnostic so both sides serialize/deserialize consistently.

## 2. Data & Synchronization Flow
1. Client joins → Fabric mod sends handshake (`HandshakePayload` C2S) if channel available.
2. Server (`ServerPacketHandler`) marks player as modded and asynchronously loads relevant `PathData` JSON files (ownership or shared) then filters out duplicate shared copies the player already owns.
3. Server sends batched path list (`PathDataSyncPayload`) via plugin messaging → Fabric client applies with `ClientPathManager.applyServerSync`.
4. Live recording: server pushes incremental points over `trailblazer:live_path_update`; client updates transient live path until `StopLivePathPayload`.
5. Sharing: server either sends `SharePathPayload` (immediate share to modded client) or for unmodded targets starts server-side rendering fallback (see `ServerPacketHandler.handleSharePathWithPlayers`).
6. Client metadata edits (`UpdatePathMetadataPayload`) or share requests travel C2S; server validates ownership and replies with `PathActionResultPayload` (may include updated path for optimistic UI refresh).

## 3. Networking Conventions
- Server (plugin) side uses legacy plugin message channels with lowercase names like `trailblazer:live_path_update`, `trailblazer:delete_path`, `trailblazer:update_path_metadata`.
- Fabric client side uses modern Fabric `CustomPayload` identifiers (e.g. `HandshakePayload.ID`). Mirror channel names exactly; adding a new message requires: define payload (server + client representation), register outgoing/incoming channel in `ServerPacketHandler` and `TrailblazerNetworking.registerPayloadTypes()` / `ClientPacketHandler.registerS2CPackets`.
- Keep payloads minimal: prefer JSON for lists (`List<PathData>` & `List<Vector3d>`) and manual binary (`ByteBuffer`, varints) for mixed metadata messages (see metadata & share code) to reduce size.
- When adding fields to `PathData`, ensure: (a) defaulting logic in constructors doesn’t break old JSON; (b) client + server both see the new field (bump `serialVersionUID` only if truly incompatible); (c) color defaults via `PathColors.assignColorFor` stay deterministic.

## 4. Persistence Rules
- Server stores each path as a separate JSON file named `<uuid>.json` under `plugins/Trailblazer/paths/` (created by `PathDataManager`).
- Deletion & renaming always performed through `PathDataManager`; don’t manipulate files directly elsewhere.
- Client maintains its own persistence (Fabric side) per-world or per-server (see `TrailblazerFabricClient.registerWorldLifecycle` & `PathPersistenceManager`). Treat server sync as authoritative overlay for owned/shared paths.

## 5. Naming & Ownership Semantics
- `PathData.pathId` is unique per stored path. Shared copies retain `originPathId` + `originOwnerUUID/Name` to trace lineage. Always set origin fields when creating a shared copy (server ensures via `ensureSharedCopy`).
- Ownership check pattern: `p.getPathId().equals(targetId) && p.getOwnerUUID().equals(requesterUUID)` before destructive operations.
- Color: `colorArgb == 0` triggers lazy assignment; never persist 0 unless you want deterministic reassignment.

## 6. Command & Tick Patterns (Server)
- Single root command `trailblazer` with subcommands (view, hide, delete, rename, share, rendermode, color, info, record). Add new subcommands by updating executor & tab completer classes (`TrailblazerCommand`, `PathTabCompleter`).
- Recording ticks every 2 server ticks (scheduled in `TrailblazerPlugin.registerEventListeners`). If adding high-frequency logic, coalesce or piggyback this task rather than spawning new repeating tasks.

## 7. Build & Packaging
Top-level Gradle (Java 21) – subprojects inherit group/version:
- Build all: `./gradlew build` (produces: plugin shaded jar via `shadowJar`, Fabric mod remapped jar, API jar).
- Plugin shading: `trailblazer-plugin` relocates Gson & Netty; if adding libraries, relocate to `com.trailblazer.plugin.libs.*` to avoid conflicts with server or other plugins.
- Fabric module includes API: keep `flatDir` repo in Fabric `build.gradle` in sync if API publication approach changes. After API changes, ensure its jar is built before Fabric `processIncludeJars` (already enforced by explicit `dependsOn`).

## 8. Adding a New Shared Model Field
Example workflow:
1. Add field + getter/setter or constructor param to `PathData` (server-neutral defaults; avoid platform refs).
2. Update constructors to maintain backward JSON compatibility (null-safe, optional).
3. Where server creates or mutates `PathData` (e.g. in `RecordingManager`, `PathDataManager.ensureSharedCopy`), populate the new field.
4. Client: adjust UI / renderer to safely handle absence (null / default) before accessing.
5. Test: create path → share → rename/update metadata → reconnect client → verify field persists.

## 9. Error & Result Signaling
- Use `PathActionResultPayload` for user-visible feedback (success boolean + message + optional updated path). Any new mutating server action should return one; keep messages concise and color-coded client side (success=green, failure=red).
- For silent failures in background sync, log with plugin logger / Fabric LOGGER but avoid spamming players.

## 10. Rendering & Performance Considerations
- Live path updates are throttled by recording tick (every 2 ticks). If adding interpolation or denser sampling, adjust client smoothing instead of increasing network frequency.
- Avoid large single JSON payloads: batch sends only at handshake or explicit resync. For potential large histories, consider future pagination—but current design expects moderate path counts.

## 11. Extension Guidelines
When implementing new features:
- Reuse `PathDataManager` for any file I/O; if a new aggregate is needed, consider a parallel manager class but keep serialization in API models.
- Ensure both environments (plugin + fabric) compile with no cyclic deps; if a concept is shared, move the POJO/enum to `trailblazer-api`.
- Register any new message channels in both directions explicitly; forgetting server registration silently drops packets.

## 12. Quick Reference
- Root build: `./gradlew clean build`
- Main entrypoints: `TrailblazerPlugin` (server), `TrailblazerFabricClient` (client)
- Central networking classes: `ServerPacketHandler`, `ClientPacketHandler`
- Shared domain: `trailblazer-api/src/main/java/com/trailblazer/api/*`
- Persistence server: `PathDataManager`
- Client handshake trigger: Fabric JOIN event → `HandshakePayload`


## 13. Context7 MCP Integration (Reminder)

Context7 is key to your success. Using it provides:
- **Real-time documentation**: Avoids relying on your outdated knowledge.
- **Accurate code examples**: Reduces errors and increases development speed.
- **Version compatibility**: Ensures your code works with the project's specific versions.

**Always use Context7 when interacting with an external dependency.**

---
If any of these sections become stale (e.g., build script refactors, renamed channels), update this file in the same commit as the change.
