# Fabric Module Build Notes

## Issue: fabric-loom SNAPSHOT Resolution

The `trailblazer-fabric` module currently uses `fabric-loom` version `1.10-SNAPSHOT`, which may not be consistently available from Maven repositories. This can cause build failures with errors like:

```
Plugin [id: 'fabric-loom', version: '1.10-SNAPSHOT'] was not found
```

## Workarounds

### Option 1: Use when fabric-loom repository is accessible
The build will work when the Fabric Maven repository has the SNAPSHOT available. This typically works in environments with full internet access to `https://maven.fabricmc.net/`.

### Option 2: Temporarily exclude fabric module for testing
If you only need to run tests for `trailblazer-api` and `trailblazer-plugin`, you can temporarily comment out the fabric module in `settings.gradle`:

```gradle
include 'trailblazer-api'
include 'trailblazer-plugin'
// include 'trailblazer-fabric'  // Temporarily commented
```

### Option 3: Try a stable release version
Update `trailblazer-fabric/build.gradle` to use a stable release instead of SNAPSHOT. However, this may require compatibility adjustments.

## Recommendations

1. **For CI/CD**: Ensure the build environment has reliable access to `https://maven.fabricmc.net/`
2. **For local development**: If fabric builds fail, you can still test the API and plugin modules
3. **Future improvement**: Pin to a stable fabric-loom version once compatibility is verified

## Testing without Fabric

The fabric module has limited testability due to:
- Minecraft client runtime dependencies
- Rendering system dependencies  
- Fabric API integration requirements

For most testing needs, focus on:
- `trailblazer-api` - Pure Java, fully testable
- `trailblazer-plugin` - Server-side logic, testable with MockBukkit
- Fabric UI - Manual testing in-game recommended
