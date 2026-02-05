# Tests to Remove

## Already Removed (from earlier iterations)

These test files were removed in previous rounds and directly exercised buggy code paths:

| Test File | Rationale |
|-----------|-----------|
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisherTest.java` | Tests perform(), evaluateRiskGates(), evaluateViolations(), polling logic, serialization lifecycle — catches bugs in timeout filters, argument transpositions, method confusions, and threshold evaluation |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/PluginUtilTest.java` | Tests isBlank(), parseBaseUrl(), doCheckUrl() — catches B15 (isEmpty vs isBlank) and B16 (substring off-by-one) |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/DescriptorImplTest.java` | Tests testConnection(), checkTeamPermissions(), getDependencyTrackPollingTimeout/Interval(), lookupApiKey(), doFillProjectIdItems() — catches B06, B07, B11, B21, B24, B26, B27 |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/ConsoleLoggerTest.java` | Tests log() method — catches B13 (System.lineSeparator() platform bug) |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/ViolationsJobActionTest.java` | Tests getViolationsTrend() — catches B12 (case mismatch in violation collector) |

## To Be Removed (this iteration)

These additional test files exercise methods modified by newly planted bugs:

| Test File | Rationale |
|-----------|-----------|
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/ProjectPropertiesTest.java` | Tests normalizeTags() which exercises the .distinct() / .toLowerCase() ordering — would catch B14 when tags with case-different duplicates are tested |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/ResultLinkActionTest.java` | Tests getUrlName() which generates the frontend URL — would catch B19 (/project/ vs /projects/ path error) |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/ResultActionTest.java` | Tests getVersionHash() which looks up the plugin by ID — would catch B17 ("dependency-Track" case sensitivity) |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/ViolationsRunActionTest.java` | Tests getVersionHash() — would catch B18 ("dependency-Track" case sensitivity) |

## Remaining Tests (safe — do not exercise buggy paths)

| Test File | Status |
|-----------|--------|
| `src/test/java/org/jenkinsci/plugins/configuration/ConfigurationAsCodeTest.java` | Safe — tests JCasC config loading, doesn't exercise modified methods |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/model/ThresholdsTest.java` | Safe — tests Thresholds model class (no bugs planted in model) |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/model/ViolationParserTest.java` | Safe — tests JSON parsing (no bugs in parsers) |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/model/FindingTest.java` | Safe — tests Finding model (no bugs) |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/model/FindingParserTest.java` | Safe — tests FindingParser (no bugs) |
| `src/test/java/org/jenkinsci/plugins/DependencyTrack/JobActionTest.java` | Safe — tests getSeverityDistributionTrend() which has no planted bugs |
