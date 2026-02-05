# Bug Insertion Proposal for Dependency-Track Jenkins Plugin

## Repository Map

### Overview
This is a Jenkins plugin for integrating with OWASP Dependency-Track, a software composition analysis (SCA) platform. The plugin uploads BOMs (Bill of Materials), retrieves vulnerability findings, evaluates risk gates/thresholds, and displays results in Jenkins builds.

### Major Subsystems

#### 1. Core Publisher (`DependencyTrackPublisher.java`)
- **Responsibility**: Main build step that orchestrates BOM upload, polling for results, and threshold evaluation
- **Key paths**: `perform()`, `publishAnalysisResult()`, `evaluateRiskGates()`, `evaluateViolations()`
- **I/O**: File reading (BOM artifacts), API calls, build result modification

#### 2. API Client (`api/ApiClient.java`)
- **Responsibility**: HTTP communication with Dependency-Track server
- **Key paths**: `upload()`, `getFindings()`, `getViolations()`, `lookupProject()`, `isTokenBeingProcessed()`
- **I/O**: HTTP requests/responses, retry logic, pagination handling

#### 3. Risk Gate Evaluation (`model/RiskGate.java`)
- **Responsibility**: Evaluates vulnerability thresholds to determine build result
- **Key paths**: `evaluate()` - compares current vs previous distributions against thresholds

#### 4. Model Parsers (`model/*Parser.java`)
- **Responsibility**: Parse JSON responses from Dependency-Track API
- **Key files**: `FindingParser.java`, `ViolationParser.java`, `ProjectParser.java`, `ComponentParser.java`
- **Key logic**: Alias deduplication, severity extraction, null handling

#### 5. Severity Distribution (`model/SeverityDistribution.java`)
- **Responsibility**: Counts vulnerabilities by severity level for threshold comparison

#### 6. Descriptor/Configuration (`DescriptorImpl.java`)
- **Responsibility**: Global plugin configuration, connection testing, project listing
- **Key paths**: `testConnection()`, `doFillProjectIdItems()`, `checkTeamPermissions()`

#### 7. Result Actions (`ResultAction.java`, `ViolationsRunAction.java`)
- **Responsibility**: Store and display findings/violations per build
- **Key paths**: `getProjectActions()`, `getFindingsJson()`, `getViolationsJson()`

#### 8. Job Actions (`JobAction.java`, `ViolationsJobAction.java`)
- **Responsibility**: Display trend charts across builds
- **Key paths**: `getSeverityDistributionTrend()`, `getViolationsTrend()`

#### 9. Utility Classes (`PluginUtil.java`, `ProjectProperties.java`)
- **Responsibility**: URL parsing, validation, HTTP client creation, tag normalization

---

## Bug Candidates (B01-B30)

### B01: Pagination Off-by-One in Project Fetching
- **Location**: `api/ApiClient.java` → `getProjects()` → lines 161-172
- **Core relevance**: Project listing is used in job configuration dropdown and is a core API interaction
- **Bug type**: Off-by-one / infinite loop potential
- **Proposed change**: Change `int page = 1;` to `int page = 0;` - Dependency-Track API uses 1-based pagination, so starting at 0 would cause the first page to be skipped or return unexpected results
- **Trigger conditions**: When there are projects to list in the dropdown
- **Expected symptom**: First page of projects missing from dropdown, or API error if server rejects page=0
- **Why it's hard**: The bug only manifests when there are projects; empty project lists work fine. The pagination logic looks correct at a glance.
- **Static-analysis discoverability**: Low - requires understanding of external API contract
- **Suggested detection**: Integration test with mock server returning paginated results

### B02: Retry Policy Max Attempts Misconfiguration
- **Location**: `api/ApiClient.java` → `executeWithRetry()` → line 441
- **Core relevance**: Retry logic affects all API calls - critical for reliability
- **Bug type**: Incorrect configuration / silent failure
- **Proposed change**: Change `new MaxAttemptsRetryPolicy(2)` to `new MaxAttemptsRetryPolicy(1)` - this effectively disables retries since 1 attempt means no retry
- **Trigger conditions**: Transient network failures during API calls
- **Expected symptom**: Builds fail on first transient error instead of retrying
- **Why it's hard**: Works perfectly when network is stable; only fails under transient conditions
- **Static-analysis discoverability**: Low - requires understanding that MaxAttemptsRetryPolicy(1) means "no retries"
- **Suggested detection**: Test with mock that fails first call, succeeds second

### B03: Timeout Calculation Integer Overflow
- **Location**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → line 377
- **Core relevance**: Timeout calculation is critical for synchronous BOM processing
- **Bug type**: Numeric overflow / incorrect calculation
- **Proposed change**: Change `60000L * getEffectivePollingTimeout()` to `60000 * getEffectivePollingTimeout()` (remove L suffix) - with large timeout values, integer overflow could occur before promotion to long
- **Trigger conditions**: Large polling timeout values (e.g., > 35 minutes)
- **Expected symptom**: Timeout occurs immediately or at wrong time due to overflow
- **Why it's hard**: Works correctly for typical timeout values (5-30 minutes); only fails with larger values
- **Static-analysis discoverability**: Medium - some static analyzers catch integer overflow patterns
- **Suggested detection**: Unit test with large timeout value

### B04: Polling Interval Sleep Before First Check
- **Location**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → lines 379-380
- **Core relevance**: Polling loop is the core synchronous processing mechanism
- **Bug type**: Logic error / unnecessary delay
- **Proposed change**: Move `Thread.sleep(interval)` from line 380 to after the while loop condition check (line 382) - this causes an unnecessary initial delay before the first token check
- **Trigger conditions**: All synchronous builds
- **Expected symptom**: Builds take longer than necessary (extra polling interval delay)
- **Why it's hard**: Build still succeeds; the delay is subtle and may be attributed to server processing time
- **Static-analysis discoverability**: Low - requires understanding of intended behavior
- **Suggested detection**: Timing test comparing expected vs actual duration

### B05: Incorrect Threshold Comparison Operator for New Findings
- **Location**: `model/RiskGate.java` → `evaluate()` → lines 64-68
- **Core relevance**: Risk gate evaluation determines build pass/fail - critical security feature
- **Bug type**: Wrong comparison operator
- **Proposed change**: In the new findings FAILURE check (line 64), change `>=` to `>` for one severity level (e.g., `currentDistribution.getCritical() > previousDistribution.getCritical() + thresholds.newFindings.failedCritical`)
- **Trigger conditions**: When new critical findings exactly equal the threshold
- **Expected symptom**: Build passes when it should fail (threshold boundary case)
- **Why it's hard**: Only triggers at exact boundary; off-by-one in security threshold is subtle
- **Static-analysis discoverability**: Low - requires understanding of business logic intent
- **Suggested detection**: Parameterized test with boundary values

### B06: Missing Null Check in Vulnerability Alias Comparison
- **Location**: `model/Vulnerability.java` → `isAliasOf()` → lines 55-57
- **Core relevance**: Alias deduplication prevents double-counting vulnerabilities
- **Bug type**: Null pointer exception potential
- **Proposed change**: Change `return other.aliases != null && other.aliases.contains(vulnId);` to `return other.aliases != null && other.aliases.contains(vulnId) && vulnId != null;` but actually introduce bug by removing the `other.aliases != null` check
- **Trigger conditions**: When comparing vulnerabilities where one has null aliases
- **Expected symptom**: NullPointerException during finding parsing
- **Why it's hard**: Only occurs with specific data patterns; many findings have aliases populated
- **Static-analysis discoverability**: Medium - null safety analyzers might catch this
- **Suggested detection**: Unit test with null aliases

### B07: Incorrect JSON Key for Last BOM Import Date
- **Location**: `model/ProjectParser.java` → `parse()` → line 38
- **Core relevance**: Project parsing is used for project listing and lookup
- **Bug type**: Typo / wrong field name
- **Proposed change**: The code already uses `"lastBomImportStr"` but the actual API returns `"lastBomImport"` - change to use wrong key like `"lastBomImportDate"`
- **Trigger conditions**: When parsing project data with lastBomImport field
- **Expected symptom**: lastBomImport always null, trend data incomplete
- **Why it's hard**: Field is optional and not critical for core functionality
- **Static-analysis discoverability**: Low - requires API documentation knowledge
- **Suggested detection**: Integration test with real API response

### B08: Thread Interrupt Flag Not Preserved
- **Location**: `DependencyTrackPublisher.java` → `perform()` → lines 328-334
- **Core relevance**: Proper interrupt handling is critical for build cancellation
- **Bug type**: Concurrency / interrupt handling
- **Proposed change**: Remove the `Thread.currentThread().interrupt();` call on line 332
- **Trigger conditions**: When build is cancelled during BOM reading
- **Expected symptom**: Build cancellation may not propagate correctly; thread pool issues
- **Why it's hard**: Only manifests during cancellation; hard to reproduce consistently
- **Static-analysis discoverability**: Medium - some analyzers check for interrupt flag preservation
- **Suggested detection**: Test with build cancellation during file read

### B09: URL Encoding Missing for Project ID in Link
- **Location**: `ResultLinkAction.java` → `getUrlName()` → line 74
- **Core relevance**: Project links are displayed in build results
- **Bug type**: Security / encoding issue
- **Proposed change**: Remove `URLEncoder.encode(projectId, StandardCharsets.UTF_8)` and use raw `projectId`
- **Trigger conditions**: Project IDs with special characters (though UUIDs typically don't have them)
- **Expected symptom**: Broken links or potential XSS if project ID contains special chars
- **Why it's hard**: UUIDs are typically safe; only fails with unusual project IDs
- **Static-analysis discoverability**: Medium - security scanners might flag missing encoding
- **Suggested detection**: Test with special characters in project ID

### B10: Incorrect Empty Check for Tags in Project Update
- **Location**: `api/ApiClient.java` → `updateProjectProperties()` → line 350
- **Core relevance**: Project property updates are used to set tags, description, etc.
- **Bug type**: Logic error / incorrect condition
- **Proposed change**: Change `if (!tags.isEmpty())` to `if (tags.isEmpty())` - this inverts the logic
- **Trigger conditions**: When updating project with tags
- **Expected symptom**: Tags never get updated; empty tags list sent instead
- **Why it's hard**: Silent failure - no error thrown, just wrong behavior
- **Static-analysis discoverability**: Low - requires understanding of intended behavior
- **Suggested detection**: Integration test verifying tags are sent

### B11: Violation State Ordinal Used for Comparison
- **Location**: `model/Violation.java` → `getStateRank()` → lines 39-41
- **Core relevance**: Violation state ranking affects display ordering
- **Bug type**: Fragile code / enum ordering dependency
- **Proposed change**: The enum `ViolationState` has order FAIL, WARN, INFO. Change `getStateRank()` to return `3 - state.ordinal()` (inverting the rank)
- **Trigger conditions**: When displaying violations sorted by severity
- **Expected symptom**: Violations sorted in wrong order (INFO before FAIL)
- **Why it's hard**: Sorting still works, just in wrong order; may not be immediately noticed
- **Static-analysis discoverability**: Low - requires understanding of display requirements
- **Suggested detection**: UI test verifying sort order

### B12: Missing Suppressed Finding Filter
- **Location**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → line 393
- **Core relevance**: Severity distribution calculation affects threshold evaluation
- **Bug type**: Missing filter / incorrect counting
- **Proposed change**: The code counts all findings including suppressed ones. Add a filter that incorrectly filters OUT non-suppressed findings: `.filter(f -> f.getAnalysis().isSuppressed())`
- **Trigger conditions**: When there are suppressed findings
- **Expected symptom**: Only suppressed findings counted, thresholds incorrectly evaluated
- **Why it's hard**: Inverted filter logic; builds may pass when they should fail
- **Static-analysis discoverability**: Low - requires understanding of business logic
- **Suggested detection**: Test with mix of suppressed and non-suppressed findings

### B13: Credential Lookup Returns Empty String Instead of Null
- **Location**: `DescriptorImpl.java` → `lookupApiKey()` → lines 417-422
- **Core relevance**: API key lookup is critical for authentication
- **Bug type**: Incorrect default value
- **Proposed change**: Change `.orElse("")` to `.orElse(null)` - this could cause NPE downstream or change behavior
- **Trigger conditions**: When credential ID doesn't match any stored credential
- **Expected symptom**: NullPointerException or authentication failure with confusing error
- **Why it's hard**: Only fails when credential is misconfigured; normal path works
- **Static-analysis discoverability**: Medium - null safety analyzers might flag
- **Suggested detection**: Test with invalid credential ID

### B14: HTTP Client Timeout Applied to Wrong Unit
- **Location**: `PluginUtil.java` → `newHttpClient()` → lines 76-81
- **Core relevance**: HTTP client configuration affects all API calls
- **Bug type**: Unit confusion
- **Proposed change**: Change `Duration.ofSeconds(connectionTimeout)` to `Duration.ofMillis(connectionTimeout)` - timeout values are in seconds but would be interpreted as milliseconds
- **Trigger conditions**: All API calls
- **Expected symptom**: Connections timeout almost immediately (e.g., 30ms instead of 30s)
- **Why it's hard**: Error message mentions timeout but cause is non-obvious
- **Static-analysis discoverability**: Low - requires understanding of configuration units
- **Suggested detection**: Integration test with slow server response

### B15: Form Validation Returns OK for Malformed URL
- **Location**: `PluginUtil.java` → `doCheckUrl()` → lines 41-54
- **Core relevance**: URL validation is used in configuration UI
- **Bug type**: Validation bypass
- **Proposed change**: Change `if (isBlank(value)) { return FormValidation.ok(); }` to always return OK regardless of URL validity
- **Trigger conditions**: When entering invalid URL in configuration
- **Expected symptom**: Invalid URLs accepted, runtime errors during API calls
- **Why it's hard**: Configuration saves successfully; error only at runtime
- **Static-analysis discoverability**: Low - requires understanding of validation intent
- **Suggested detection**: UI test with invalid URL

### B16: Pagination Total Count Header Parsing Fallback
- **Location**: `api/ApiClient.java` → `getTotalCountValue()` → lines 427-431
- **Core relevance**: Pagination handling affects project and violation listing
- **Bug type**: Incorrect fallback logic
- **Proposed change**: Change `Optional.ofNullable(res.header(PAGINATED_RES_TOTAL_COUNT_HEADER)).map(Integer::parseInt).orElse(defaultValue)` to `.orElse(0)` - this would cause pagination to stop after first page if header is missing
- **Trigger conditions**: When server doesn't return X-Total-Count header
- **Expected symptom**: Only first page of results returned
- **Why it's hard**: Works when header is present; only fails with certain server configurations
- **Static-analysis discoverability**: Low - requires understanding of pagination protocol
- **Suggested detection**: Test with mock server not returning total count header

### B17: Version Comparison Using String Instead of VersionNumber
- **Location**: `DescriptorImpl.java` → `testConnection()` → lines 275-278
- **Core relevance**: Version checking ensures compatibility with Dependency-Track server
- **Bug type**: Incorrect comparison
- **Proposed change**: Change `actualVersion.isOlderThan(requiredVersion)` to string comparison `actualVersion.toString().compareTo(requiredVersion.toString()) < 0` - this breaks semantic versioning (e.g., "4.9.0" > "4.12.0" alphabetically)
- **Trigger conditions**: When connecting to server with version like 4.9.x
- **Expected symptom**: Version check passes when it should fail, or vice versa
- **Why it's hard**: Works for many version numbers; only fails with specific patterns
- **Static-analysis discoverability**: Medium - some analyzers flag string version comparison
- **Suggested detection**: Test with version numbers that differ alphabetically vs semantically

### B18: CWE ID Extraction from Wrong Array Index
- **Location**: `model/FindingParser.java` → `parseVulnerability()` → lines 63-65
- **Core relevance**: CWE information is displayed in vulnerability details
- **Bug type**: Array index error
- **Proposed change**: Change `a.optJSONObject(0)` to `a.optJSONObject(1)` - this would skip the first CWE and use the second one (if exists)
- **Trigger conditions**: When vulnerability has multiple CWEs
- **Expected symptom**: Wrong CWE displayed, or null if only one CWE exists
- **Why it's hard**: Many vulnerabilities have single CWE; only fails with multiple
- **Static-analysis discoverability**: Low - requires understanding of data structure
- **Suggested detection**: Test with vulnerability having multiple CWEs

### B19: Build Number Not Passed to Severity Distribution
- **Location**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → line 392
- **Core relevance**: Build number is used for trend chart display
- **Bug type**: Wrong parameter
- **Proposed change**: Change `new SeverityDistribution(build.getNumber())` to `new SeverityDistribution(0)` - this would make all trend chart points show build #0
- **Trigger conditions**: All synchronous builds
- **Expected symptom**: Trend chart shows wrong build numbers
- **Why it's hard**: Functionality works; only display is wrong
- **Static-analysis discoverability**: Low - requires understanding of UI requirements
- **Suggested detection**: UI test verifying trend chart build numbers

### B20: Permission Check Uses Wrong Constant
- **Location**: `DependencyTrackPublisher.java` → `publishAnalysisResult()` → line 402
- **Core relevance**: Permission checking determines if violations can be fetched
- **Bug type**: Wrong constant / typo
- **Proposed change**: Change `VIEW_POLICY_VIOLATION.toString()` to `VIEW_VULNERABILITY.toString()` - this checks wrong permission
- **Trigger conditions**: When team has VIEW_VULNERABILITY but not VIEW_POLICY_VIOLATION
- **Expected symptom**: Violations fetched when they shouldn't be, or vice versa
- **Why it's hard**: Both permissions often granted together; only fails with specific permission sets
- **Static-analysis discoverability**: Low - requires understanding of permission model
- **Suggested detection**: Test with team having only one of the permissions

### B21: JSON Element vs ElementOpt Confusion
- **Location**: `api/ApiClient.java` → `updateProjectProperties()` → lines 354-360
- **Core relevance**: Project property updates send data to server
- **Bug type**: Null handling error
- **Proposed change**: Change `updates.elementOpt("swidTagId", properties.swidTagId())` to `updates.element("swidTagId", properties.swidTagId())` - this would include null values in JSON
- **Trigger conditions**: When swidTagId is null
- **Expected symptom**: Server receives explicit null, potentially clearing existing value
- **Why it's hard**: Works when value is set; only fails when null
- **Static-analysis discoverability**: Low - requires understanding of JSON library behavior
- **Suggested detection**: Test with null property values

### B22: Stream Collector Creates Immutable List
- **Location**: `model/ViolationParser.java` → `parse()` → lines 29-34
- **Core relevance**: Violation parsing is used for policy violation display
- **Bug type**: Immutability issue
- **Proposed change**: Change `.collect(Collectors.toList())` to `.toList()` - the comment says list must not be immutable for marshalling, but `.toList()` creates immutable list
- **Trigger conditions**: When violations are serialized (e.g., build persistence)
- **Expected symptom**: UnsupportedOperationException during serialization
- **Why it's hard**: Works during build; only fails during persistence/reload
- **Static-analysis discoverability**: Low - requires understanding of serialization requirements
- **Suggested detection**: Test serialization/deserialization of violations

### B23: Previous Build Lookup Skips Unstable Builds
- **Location**: `DependencyTrackPublisher.java` → `getPreviousBuildWithAnalysisResult()` → lines 593-599
- **Core relevance**: Previous build comparison is used for new findings threshold
- **Bug type**: Incorrect filter condition
- **Proposed change**: Change `r.getResult() == Result.NOT_BUILT` to `r.getResult() != Result.SUCCESS` - this would skip unstable builds when looking for previous results
- **Trigger conditions**: When previous build was unstable but had results
- **Expected symptom**: New findings threshold compared against older build, potentially wrong result
- **Why it's hard**: Only manifests when previous build was unstable; success path works
- **Static-analysis discoverability**: Low - requires understanding of build result semantics
- **Suggested detection**: Test with unstable previous build

### B24: Tag Normalization Loses Original Case
- **Location**: `ProjectProperties.java` → `normalizeTags()` → lines 173-183
- **Core relevance**: Tags are sent to Dependency-Track server
- **Bug type**: Data transformation error
- **Proposed change**: The code already lowercases tags (line 177). Change to uppercase: `.map(String::toUpperCase)` - this would send uppercase tags when server expects lowercase
- **Trigger conditions**: When setting tags with mixed case
- **Expected symptom**: Tags don't match expected values on server
- **Why it's hard**: Tags are set but with wrong case; may not be immediately noticed
- **Static-analysis discoverability**: Low - requires understanding of server expectations
- **Suggested detection**: Integration test verifying tag case

### B25: Effective Timeout Filter Allows Zero
- **Location**: `DependencyTrackPublisher.java` → `getEffectivePollingTimeout()` → line 560
- **Core relevance**: Polling timeout controls synchronous processing duration
- **Bug type**: Boundary condition error
- **Proposed change**: Change `.filter(v -> v > 0)` to `.filter(v -> v >= 0)` - this allows zero timeout, causing immediate timeout
- **Trigger conditions**: When polling timeout is explicitly set to 0
- **Expected symptom**: Synchronous builds timeout immediately
- **Why it's hard**: Zero is an unusual but valid-looking configuration value
- **Static-analysis discoverability**: Low - requires understanding of timeout semantics
- **Suggested detection**: Test with zero timeout value

### B26: Findings Deduplication Uses Wrong Method
- **Location**: `model/FindingParser.java` → `parse()` → lines 36-42
- **Core relevance**: Finding deduplication prevents double-counting vulnerabilities
- **Bug type**: Logic error in deduplication
- **Proposed change**: Change `findings.stream().noneMatch(finding::isAliasOf)` to `findings.stream().noneMatch(f -> f.isAliasOf(finding))` - this inverts the alias check direction
- **Trigger conditions**: When findings include aliases
- **Expected symptom**: Aliases not properly deduplicated, vulnerability counts inflated
- **Why it's hard**: Subtle logic inversion; both directions seem plausible
- **Static-analysis discoverability**: Low - requires understanding of alias semantics
- **Suggested detection**: Test with findings that are aliases of each other

### B27: Violations Trend Missing Build Number
- **Location**: `ViolationsJobAction.java` → `getViolationsTrend()` → line 77
- **Core relevance**: Violations trend chart displays policy violations over time
- **Bug type**: Wrong data source
- **Proposed change**: Change `result.getRun().getNumber()` to `0` or a hardcoded value
- **Trigger conditions**: When viewing violations trend chart
- **Expected symptom**: All trend points show same build number
- **Why it's hard**: Chart renders but with wrong x-axis values
- **Static-analysis discoverability**: Low - requires understanding of UI requirements
- **Suggested detection**: UI test verifying trend chart data

### B28: Connection Timeout Used for Read Timeout
- **Location**: `DescriptorImpl.java` → `createClient()` → lines 409-413
- **Core relevance**: HTTP client configuration affects all API calls
- **Bug type**: Variable swap / copy-paste error
- **Proposed change**: Change `final int readTimeout = Math.max(dependencyTrackReadTimeout, 0);` to use `dependencyTrackConnectionTimeout` instead
- **Trigger conditions**: When connection and read timeouts are configured differently
- **Expected symptom**: Read timeout uses wrong value, potentially timing out too early or late
- **Why it's hard**: Works when both timeouts are same; only fails when different
- **Static-analysis discoverability**: Low - variable names are similar
- **Suggested detection**: Test with different timeout values

### B29: Project Lookup Caches Wrong ID
- **Location**: `DependencyTrackPublisher.java` → `lookupProjectId()` → lines 645-655
- **Core relevance**: Project ID lookup is used for findings retrieval
- **Bug type**: Caching error
- **Proposed change**: Change `projectIdCache = apiClient.lookupProject(effectiveProjectName, effectiveProjectVersion).getUuid();` to cache the name instead: `projectIdCache = effectiveProjectName;`
- **Trigger conditions**: When using project name/version instead of UUID
- **Expected symptom**: API calls fail because name is used where UUID is expected
- **Why it's hard**: First call might work if name happens to be valid UUID format
- **Static-analysis discoverability**: Low - requires understanding of data flow
- **Suggested detection**: Test with project name lookup

### B30: Permissions Set Modification During Iteration
- **Location**: `DescriptorImpl.java` → `checkTeamPermissions()` → lines 311-313
- **Core relevance**: Permission checking is used for connection test validation
- **Bug type**: Concurrent modification
- **Proposed change**: Change `final Set<String> allPermissions = new TreeSet<>(team.getPermissions());` to directly use `team.getPermissions()` and then add to it - this could cause ConcurrentModificationException
- **Trigger conditions**: When checking team permissions
- **Expected symptom**: ConcurrentModificationException during connection test
- **Why it's hard**: Depends on Set implementation returned by team.getPermissions()
- **Static-analysis discoverability**: Medium - some analyzers detect collection modification patterns
- **Suggested detection**: Test with various permission sets

---

## Top 10 Recommended Bug Set

Based on difficulty for LLM detection, exercise value, stealth, and scorability:

| Rank | ID | Bug Type | Justification |
|------|-----|----------|---------------|
| 1 | **B26** | Logic inversion in alias deduplication | Subtle semantic inversion; both directions look plausible; affects security counting |
| 2 | **B02** | Retry policy disabled | MaxAttemptsRetryPolicy(1) looks like "1 retry" but means "no retries"; affects reliability |
| 3 | **B05** | Threshold boundary off-by-one | Security-critical; only triggers at exact boundary; `>=` vs `>` is subtle |
| 4 | **B12** | Inverted suppressed finding filter | Filter logic inversion; affects security threshold evaluation |
| 5 | **B14** | Timeout unit confusion | Duration.ofSeconds vs ofMillis; causes immediate timeouts; non-obvious error |
| 6 | **B16** | Pagination fallback to zero | Only fails when header missing; causes incomplete data silently |
| 7 | **B23** | Previous build lookup skips unstable | Affects new findings comparison; subtle condition change |
| 8 | **B22** | Immutable list in violation parsing | Only fails during serialization; runtime vs persistence timing |
| 9 | **B20** | Wrong permission constant | VIEW_VULNERABILITY vs VIEW_POLICY_VIOLATION; similar names, different meanings |
| 10 | **B28** | Connection/read timeout swap | Variable name similarity; only fails when values differ |

### Selection Criteria Applied:
1. **Core-path impact**: All affect main functionality (API calls, threshold evaluation, data parsing)
2. **Diversity**: Covers logic errors, configuration, concurrency, boundary conditions, data transformation
3. **Scorable**: Each has specific file/function location and identifiable issue
4. **Not trivially detectable**: Requires understanding of business logic, API contracts, or subtle semantics
5. **Avoids existing bugs**: None overlap with bugs listed in `not_to_add_bugs.json`

---

## Bugs Already Excluded (from not_to_add_bugs.json)

The following bug patterns are already documented and should NOT be used:
1. Case sensitivity in `FindingParser.parseAliases()` - vulnId::equals vs equalsIgnoreCase
2. Control flow reordering in `RiskGate.evaluate()` - UNSTABLE before FAILURE
3. Missing blank version validation in `ApiClient.lookupProject()`
4. Zero timeout boundary in `DescriptorImpl.getDependencyTrackPollingTimeout()`
5. Boundary condition in `DependencyTrackPublisher.publishAnalysisResult()` timeout check
6. Severity counting in `SeverityDistribution.add()` else branch
7. Inverted sort order in `JobAction.getSeverityDistributionTrend()`
8. Case sensitivity for "null" string in `ApiClient.lookupProject()`

---

## Next Steps

After you select exactly 8 bugs from the 30 candidates above, I will:
1. Implement exactly those 8 bugs in the codebase
2. Provide a rubric list with one criterion per bug (weight = 1 each)
3. Include file + function/class for each criterion
4. Ensure criteria are precise, verifiable, and non-redundant
5. Verify the instance remains plausible without cascading failures

