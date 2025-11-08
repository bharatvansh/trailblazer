# Pre-Release Checklist for Trailblazer Mod

## 🔴 CRITICAL ISSUES (Must Fix Before Release)

### 1. Missing LICENSE File
**Status:** ❌ Missing  
**Location:** Root directory  
**Issue:** README.md mentions "MIT License" but no LICENSE file exists  
**Impact:** Users cannot verify license terms; distribution platforms require license files  
**Fix:** Create LICENSE file with MIT license text

### 2. Missing Mod Icon
**Status:** ❌ Missing  
**Location:** `trailblazer-fabric/src/main/resources/assets/trailblazer/icon.png`  
**Issue:** `fabric.mod.json` references `icon.png` but file doesn't exist  
**Impact:** Mod listing will show no icon; unprofessional appearance  
**Fix:** Create 128x128 or 256x256 PNG icon for the mod

### 3. Placeholder Download Links
**Status:** ❌ Placeholder  
**Location:** `README.md` lines 21-22  
**Issue:** Download links show `[Link]` placeholders  
**Impact:** Users cannot download the mod  
**Fix:** Update with actual Modrinth and CurseForge URLs

### 4. Placeholder Author Name
**Status:** ❌ Placeholder  
**Location:** 
- `fabric.mod.json` line 8
- `plugin.yml` line 6 (both files)
- `README.md` (should verify)

**Issue:** Author is set to "Mod MC" which appears to be a placeholder  
**Impact:** Unprofessional; users need to know the real author  
**Fix:** Replace with actual author name/username

### 5. Empty Contact Information
**Status:** ❌ Empty  
**Location:** `fabric.mod.json` line 10  
**Issue:** `"contact": {}` is empty  
**Impact:** Users cannot contact you for support/issues  
**Fix:** Add contact information (homepage, issues URL, sources URL, etc.)

### 6. Incomplete Version Information in README
**Status:** ❌ Incomplete  
**Location:** `README.md` line 27  
**Issue:** Says "Compatible Version" instead of actual version "1.21.8"  
**Impact:** Confusing for users  
**Fix:** Replace with "Minecraft 1.21.8"

### 7. Duplicate plugin.yml File
**Status:** ❌ Duplicate  
**Location:** Root directory `plugin.yml`  
**Issue:** There's a duplicate `plugin.yml` in root with hardcoded version "1.0.0"  
**Impact:** Confusion; the one in `trailblazer-plugin/src/main/resources/` is the correct one  
**Fix:** Delete root `plugin.yml` file (it's not used by the build system)

## 🟡 IMPORTANT ISSUES (Should Fix)

### 8. printStackTrace() Calls
**Status:** ⚠️ Should Fix  
**Location:** 
- `PathDataManager.java` lines 58, 107, 132, 297
- `ServerPacketHandler.java` line 803

**Issue:** Using `printStackTrace()` instead of proper logging  
**Impact:** Error messages go to stderr instead of logger; harder to debug in production  
**Fix:** Replace with proper logger calls (e.g., `LOGGER.error("message", e)`)

### 9. Debug Log Messages
**Status:** ⚠️ Consider Removing  
**Location:** Multiple files with `LOGGER.debug()` calls  
**Issue:** Debug logs may clutter production logs  
**Impact:** Minor performance impact; log file bloat  
**Fix:** Ensure debug logging is properly configured or remove unnecessary debug statements

## ✅ GOOD PRACTICES FOUND

### Security
- ✅ Path name sanitization implemented (`PathNameSanitizer`)
- ✅ File path construction uses UUIDs (prevents path traversal)
- ✅ Input validation on user-provided data
- ✅ Ownership checks before deletion/editing
- ✅ Maximum path length and point limits enforced

### Code Quality
- ✅ Proper error handling in most places
- ✅ Thread-safe file operations with locks
- ✅ Good separation of concerns (API, Plugin, Fabric modules)
- ✅ Comprehensive test coverage for sanitization

### Build System
- ✅ Proper Gradle multi-module setup
- ✅ Version management centralized in `gradle.properties`
- ✅ Resource processing for version substitution
- ✅ Proper dependency management

## 📋 ADDITIONAL RECOMMENDATIONS

### Documentation
- Consider adding a CHANGELOG.md for version history
- Add screenshots to README showing the mod in action
- Consider adding a FAQ section

### Testing
- Verify the mod builds successfully: `./gradlew build`
- Test on both client-only and client+server scenarios
- Test path sharing between players
- Verify file persistence works correctly
- Test error scenarios (network failures, invalid data, etc.)

### Release Preparation
- Ensure all version numbers are consistent (currently 1.0.0)
- Create release notes for the first version
- Prepare screenshots/videos for Modrinth/CurseForge
- Tag the release in git
- Build release JARs and verify they work

## 🔍 FILES TO REVIEW BEFORE RELEASE

1. `README.md` - Verify all information is accurate
2. `fabric.mod.json` - Verify metadata is complete
3. `plugin.yml` - Verify plugin metadata
4. `gradle.properties` - Verify version numbers
5. Build outputs - Test the actual JAR files

## 📝 NOTES

- The deleted `net/minecraft/util/WorldSavePath.java` file is fine - you're using Minecraft's built-in class
- The security implementation looks solid with proper input sanitization
- The mod architecture is well-designed with clear separation between API, plugin, and client
- Error handling is generally good, but could be improved by replacing printStackTrace calls

---

**Priority Order:**
1. LICENSE file (critical for legal distribution)
2. Icon file (required for mod listings)
3. Download links (required for users to download)
4. Author/contact info (professional appearance)
5. Version information (user clarity)
6. Code cleanup (printStackTrace replacement)
7. Remove duplicate files

