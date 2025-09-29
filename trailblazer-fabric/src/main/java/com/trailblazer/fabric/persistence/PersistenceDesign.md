Persistence Plan (Developer Notes)
=================================

Directory layout (client local, per world):
  .minecraft/saves/<world>/trailblazer/paths/
      index.json                -> array of path file metadata { "pathId": "..", "file": "<uuid>.json" }
      <uuid>.json               -> serialized PathDataRecord (JSON)

Path JSON schema (versioned):
  {
    "schemaVersion": 1,
    "pathId": "<uuid>",
    "name": "<string>",
    "ownerUUID": "<uuid>",
    "ownerName": "<string>",
    "creationTimestamp": <long>,
    "dimension": "minecraft:overworld",
    "color": 4294901760,           // int ARGB
    "points": [ {"x":0.0,"y":64.0,"z":0.0}, ... ]
  }

Index file is advisory; missing path files are skipped. Orphan path files (present but absent from index) are auto-added.

Autosave Strategy:
  - Mark dirty when a point is added or metadata changes.
  - Background tick every N seconds (config) flushes changed paths.
  - On world leave -> flush all dirty, rewrite index.

Safety:
  - Write to temp file then atomic move for each path ( <uuid>.json.tmp -> <uuid>.json ).
  - Catch and log IO errors; do not crash client.

Thinning (point limit enforcement):
  - If path exceeds maxPointsPerPath: create a new list keeping every k-th point (k grows until size within limit), always keep first and last.

