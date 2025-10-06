# Testing Guide

This document describes how to run tests in the Trailblazer project.

## Overview

The Trailblazer project uses JUnit 5, Mockito, and AssertJ for testing across three modules:

- **trailblazer-api**: Pure Java module with comprehensive unit tests
- **trailblazer-plugin**: Paper/Bukkit plugin with serialization and payload tests
- **trailblazer-fabric**: Fabric mod (limited testing - see below)

## Running Tests

### Run all tests
```bash
./gradlew test
```

### Run tests for a specific module
```bash
./gradlew :trailblazer-api:test
./gradlew :trailblazer-plugin:test
./gradlew :trailblazer-fabric:test
```

### Run tests with coverage
```bash
./gradlew test jacocoTestReport
```

### View coverage reports
After running tests with coverage, open:
- `trailblazer-api/build/reports/jacoco/test/html/index.html`
- `trailblazer-plugin/build/reports/jacoco/test/html/index.html`

## Coverage Goals

- **trailblazer-api**: 70%+ coverage (currently ~99%)
- **trailblazer-plugin**: Focus on core logic
- **trailblazer-fabric**: Limited due to Minecraft client dependencies

## Test Structure

### trailblazer-api
```
src/test/java/com/trailblazer/api/
├── PathDataTest.java              # PathData constructors, getters, equality
├── PathColorsTest.java            # Color palette and parsing
├── PathNameMatcherTest.java       # Name matching and suggestions
├── PathNameSanitizerTest.java     # Name sanitization
├── ProtocolTest.java              # Protocol constants and capabilities
└── Vector3dTest.java              # Vector immutability and equality
```

### trailblazer-plugin
```
src/test/java/com/trailblazer/plugin/
├── PathDataSerializationTest.java # JSON serialization round-trip
└── networking/
    └── PayloadSerializationTest.java  # Network payload encoding
```

### trailblazer-fabric
```
Fabric module testing is limited due to:
- Client-side rendering dependencies on Minecraft classes
- Fabric Loom integration complexities
- Full runtime environment requirements

For fabric module, we recommend:
- Manual testing in-game
- Unit tests for non-Minecraft logic (e.g., utility classes)
- Integration tests if/when Fabric test harness becomes available
```

## Test Dependencies

The following test libraries are configured:

- **JUnit 5** (5.10.2): Test framework
- **Mockito** (5.11.0): Mocking framework
- **AssertJ** (3.25.3): Fluent assertions
- **MockBukkit** (3.96.0): Paper/Bukkit mocking (plugin module)
- **Gson** (2.10.1): JSON serialization testing

## CI/CD

Tests run automatically on every push and pull request via GitHub Actions:

- Builds all modules
- Runs all tests
- Generates coverage reports
- Uploads coverage to Codecov
- Uploads test results and build artifacts

See `.github/workflows/ci.yml` for configuration.

## Writing New Tests

### Best Practices

1. **Follow existing patterns**: Look at existing tests for examples
2. **Test one thing**: Each test method should verify one behavior
3. **Use descriptive names**: Test names should describe what they verify
4. **Arrange-Act-Assert**: Structure tests clearly
5. **No external dependencies**: Tests should not require network or file system (except temp files)

### Example Test

```java
@Test
void pathNameIsSanitizedOnConstruction() {
    PathData path = new PathData(
        UUID.randomUUID(),
        "Invalid<>Name!!",
        UUID.randomUUID(),
        "Owner",
        System.currentTimeMillis(),
        "minecraft:overworld",
        new ArrayList<>()
    );
    
    assertEquals("Invalid__Name__", path.getPathName());
}
```

## Troubleshooting

### Tests fail with "Paper API not found"
The Paper API repository may be temporarily unavailable. Tests will work in CI or when the repository is accessible.

### Fabric module won't build
Ensure fabric-loom version is compatible with your Gradle version. See `trailblazer-fabric/build.gradle`.

### Coverage verification fails
If coverage drops below 70% for the API module, the build will fail. Add tests to improve coverage.

## Future Improvements

- Add MockBukkit integration tests for PathDataManager file operations
- Add Fabric runtime tests when test harness becomes available
- Add integration tests for client-server communication
- Add performance tests for large path datasets
