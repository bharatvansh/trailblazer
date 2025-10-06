# Trailblazer

A Minecraft path recording and visualization system with client-server synchronization.

## Features

- Record and visualize player movement paths
- Client-side (Fabric mod) and server-side (Paper plugin) components
- Path sharing between players
- Multiple render modes
- Cross-dimension path support
- Real-time path synchronization

## Modules

### trailblazer-api
Pure Java API module containing shared domain models (PathData, Vector3d, PathColors).
No Minecraft dependencies - can be used by both plugin and mod.

### trailblazer-plugin
Paper/Bukkit server plugin for path persistence, permissions, and server-client sync.

### trailblazer-fabric
Fabric client mod for recording, rendering, and UI.

## Building

Requires Java 21.

```bash
./gradlew build
```

Artifacts will be in:
- `trailblazer-plugin/build/libs/trailblazer-plugin-1.0.0.jar`
- `trailblazer-fabric/build/libs/trailblazer-fabric-1.0.0.jar`

## Testing

Run all tests:
```bash
./gradlew test
```

Generate coverage reports:
```bash
./gradlew test jacocoTestReport
```

See [TESTING.md](TESTING.md) for detailed testing guide.

## Installation

### Server (Paper)
1. Copy `trailblazer-plugin-1.0.0.jar` to your Paper server's `plugins/` directory
2. Restart the server

### Client (Fabric)
1. Install Fabric Loader for Minecraft 1.21
2. Install Fabric API
3. Copy `trailblazer-fabric-1.0.0.jar` to your `.minecraft/mods/` directory

## Usage

See [PLAYER_FRIENDLY_REVIEW.md](PLAYER_FRIENDLY_REVIEW.md) for detailed feature guide.

## Development

### Project Structure
```
.
├── trailblazer-api/        # Shared domain models
├── trailblazer-plugin/     # Server-side implementation
├── trailblazer-fabric/     # Client-side mod
├── .github/
│   └── workflows/
│       └── ci.yml          # Automated testing and builds
└── TESTING.md              # Testing documentation
```

### Code Quality

- JUnit 5 + Mockito + AssertJ for testing
- JaCoCo for coverage (70%+ target for API module)
- GitHub Actions CI for automated testing

## Architecture

See [COMPREHENSIVE_REVIEW.md](COMPREHENSIVE_REVIEW.md) for detailed architecture review.

## License

[Add your license here]

## Contributing

See [TESTING.md](TESTING.md) for testing requirements.

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request
