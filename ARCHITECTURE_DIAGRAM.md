# Trailblazer System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                      TRAILBLAZER SYSTEM                             │
│                   (Minecraft Path Recording Tool)                    │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                        MODULE STRUCTURE                              │
└─────────────────────────────────────────────────────────────────────┘

    ┌──────────────────┐
    │  TRAILBLAZER-API │  ← Pure Java, Platform Agnostic
    │    (Shared)      │
    └────────┬─────────┘
             │
         ┌───┴───┐
         │       │
    ┌────▼────┐ ┌▼──────────────┐
    │ PLUGIN  │ │ FABRIC CLIENT │
    │(Server) │ │   (Client)    │
    └─────────┘ └───────────────┘
    
    📦 API Module:
       - PathData
       - Vector3d
       - PathColors
       - Protocol
       - PathNameMatcher
    
    🔌 Plugin Module:
       - PathDataManager (JSON persistence)
       - RecordingManager (server recording)
       - ServerPacketHandler (networking)
       - PathRendererManager (particle fallback)
       - Commands (server-side)
    
    🎮 Fabric Module:
       - ClientPathManager (client state)
       - PathRenderer (GL rendering)
       - ClientPacketHandler (networking)
       - UI Screens (path management)
       - PathPersistenceManager (local storage)

┌─────────────────────────────────────────────────────────────────────┐
│                     DATA FLOW DIAGRAM                                │
└─────────────────────────────────────────────────────────────────────┘

    CLIENT                          SERVER
    ┌────┐                          ┌────┐
    │Join│                          │    │
    └──┬─┘                          │    │
       │                            │    │
       │ 1. HandshakePayload        │    │
       ├──────────────────────────► │    │
       │                            │    │
       │                        ┌───┴───┐│
       │                        │ Load  ││
       │                        │ Paths ││
       │                        └───┬───┘│
       │                            │    │
       │ 2. PathDataSyncPayload     │    │
       │◄───────────────────────────┤    │
       │                            │    │
    ┌──┴──┐                         │    │
    │Apply│                         │    │
    │Sync │                         │    │
    └──┬──┘                         │    │
       │                            │    │
       │ 3. Start Recording         │    │
       ├──────────────────────────► │    │
       │                            │    │
       │ 4. LivePathUpdatePayload   │    │
       │◄───────────────────────────┤    │
       │    (every 2 ticks)         │    │
       │                            │    │
       │ 5. Stop Recording          │    │
       ├──────────────────────────► │    │
       │                            │    │
       │ 6. PathActionResultPayload │    │
       │◄───────────────────────────┤    │
       │                            │    │
    ┌──┴──┐                      ┌──┴──┐ │
    │ UI  │                      │Save │ │
    │Shows│                      │ to  │ │
    │Path │                      │JSON │ │
    └─────┘                      └─────┘ │
                                    └────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    PERSISTENCE STRATEGY                              │
└─────────────────────────────────────────────────────────────────────┘

    SERVER SIDE:
    plugins/Trailblazer/paths/
        ├── {uuid-1}.json  ← Individual path files
        ├── {uuid-2}.json
        └── {uuid-3}.json
    
    CLIENT SIDE (Singleplayer):
    saves/{world}/trailblazer/paths/
        ├── index.json     ← Path index
        ├── {uuid-1}.json
        └── {uuid-2}.json
    
    CLIENT SIDE (Multiplayer):
    trailblazer_client_servers/{server}/
        ├── index.json
        ├── {uuid-1}.json
        └── {uuid-2}.json

┌─────────────────────────────────────────────────────────────────────┐
│                      RENDERING MODES                                 │
└─────────────────────────────────────────────────────────────────────┘

    CLIENT (Fabric Mod):
    ┌─────────────────┐
    │ DASHED_LINE     │ ← GL quads (default)
    ├─────────────────┤
    │ SPACED_MARKERS  │ ← Particle effects
    ├─────────────────┤
    │ DIRECTIONAL     │ ← Arrow particles
    │ ARROWS          │
    └─────────────────┘
    
    SERVER (Fallback for vanilla clients):
    ┌─────────────────┐
    │ Particle        │ ← 10Hz spawn rate
    │ Rendering       │   (configurable spacing)
    └─────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    NETWORKING CHANNELS                               │
└─────────────────────────────────────────────────────────────────────┘

    Client → Server (C2S):
    • trailblazer:handshake
    • trailblazer:delete_path
    • trailblazer:update_path_metadata
    • trailblazer:share_path_with_players
    • trailblazer:share_request
    • trailblazer:save_path
    
    Server → Client (S2C):
    • trailblazer:path_data_sync
    • trailblazer:hide_all_paths
    • trailblazer:live_path_update
    • trailblazer:stop_live_path
    • trailblazer:share_path
    • trailblazer:path_deleted
    • trailblazer:path_action_result

┌─────────────────────────────────────────────────────────────────────┐
│                    THREAD SAFETY MODEL                               │
└─────────────────────────────────────────────────────────────────────┘

    Plugin (Server):
    • PathDataManager: Coarse ioLock for file I/O
    • RecordingManager: Single-threaded tick scheduler
    • PlayerRenderSettingsManager: ConcurrentHashMap
    • PathRendererManager: ConcurrentHashMap for tasks
    
    Fabric (Client):
    • ClientPathManager: Not thread-safe (client thread only)
    • PathRenderer: World render thread
    • PathPersistenceManager: Async file I/O
    
    API (Shared):
    • PathData: Mostly immutable (except setters)
    • Vector3d: Fully immutable ✓

┌─────────────────────────────────────────────────────────────────────┐
│                       REVIEW FINDINGS                                │
└─────────────────────────────────────────────────────────────────────┘

    ✅ STRENGTHS:
    ✓ Clean separation of concerns
    ✓ Platform abstraction via API
    ✓ Bidirectional sync works well
    ✓ Multiple rendering strategies
    ✓ Good inline documentation
    
    ⚠️ HIGH PRIORITY ISSUES:
    ! Thread safety in PathDataManager
    ! Missing input validation
    ! Inconsistent point limit enforcement
    ! Potential memory leaks
    ! Missing null checks
    
    📈 OPTIMIZATION OPPORTUNITIES:
    ↑ Server rendering performance (10Hz → 4-5Hz)
    ↑ Network payload compression
    ↑ Path point delta encoding
    ↑ Batch file operations
    ↑ View frustum culling
    
    💡 FEATURE SUGGESTIONS:
    ★ Path analytics (distance, speed, elevation)
    ★ Waypoint system
    ★ Path categories/tags
    ★ Collaborative paths
    ★ Path replay/playback
    ★ Export/import (GPX, GeoJSON)
    ★ Advanced rendering modes
    ★ Integration with Minecraft maps

┌─────────────────────────────────────────────────────────────────────┐
│                       PRIORITY ROADMAP                               │
└─────────────────────────────────────────────────────────────────────┘

    PHASE 1: Security & Stability (CRITICAL)
    ├─ Fix thread safety issues
    ├─ Add input validation
    ├─ Implement disk quotas
    ├─ Add null safety checks
    └─ Fix memory leak potential
    
    PHASE 2: Performance (HIGH)
    ├─ Optimize server rendering
    ├─ Add network retry logic
    ├─ Implement path compression
    ├─ Add view frustum culling
    └─ Batch file operations
    
    PHASE 3: Testing & Docs (MEDIUM)
    ├─ Create test infrastructure
    ├─ Add unit tests
    ├─ Write API documentation
    ├─ Create user guide
    └─ Add developer docs
    
    PHASE 4: New Features (LOW)
    ├─ Path analytics
    ├─ Waypoint system
    ├─ Path categories
    ├─ Advanced rendering
    └─ Export/import formats

┌─────────────────────────────────────────────────────────────────────┐
│                         STATISTICS                                   │
└─────────────────────────────────────────────────────────────────────┘

    📊 Codebase Metrics:
    • Total Files: 65 Java files
    • Total LOC: ~6,500 lines
    • Modules: 3 (API, Plugin, Fabric)
    • Inline Comments: 141 (all helpful)
    
    🔍 Review Coverage:
    • Problems Found: 10
    • Optimizations: 8 areas
    • New Features: 15 proposals
    • Security Items: 6 enhancements
    • DX Improvements: 8 suggestions
    
    ⭐ Overall Rating: GOOD (4/5)
    • Architecture: ⭐⭐⭐⭐⭐
    • Code Quality: ⭐⭐⭐⭐☆
    • Documentation: ⭐⭐⭐⭐⭐
    • Performance: ⭐⭐⭐☆☆
    • Security: ⭐⭐⭐☆☆
    • Testing: ⭐☆☆☆☆
```

---

**Legend:**
- ✓ = Strength
- ! = Issue
- ↑ = Optimization
- ★ = Feature
- → = Data flow direction
