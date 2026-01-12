# FP-GROUP-10: RuntimeException in DecommissionTest

## Summary

**Verdict**: FALSE POSITIVE - Restart adapter shifts generation counter, breaking test's ByteBuddy installation logic

**Test**: `DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart`
**Position**: `after_failed_decommission`
**Target**: `node`
**Mode**: `GRACEFUL`
**Index**: `0`

## Error Message

```
java.lang.RuntimeException: simulated error in prepareUnbootstrapStreaming
    at org.apache.cassandra.distributed.test.DecommissionTest$BB.prepareUnbootstrapStreaming(DecommissionTest.java:208)
    at org.apache.cassandra.service.StorageService.prepareUnbootstrapStreaming(StorageService.java)
    at org.apache.cassandra.service.StorageService.unbootstrap(StorageService.java:5427)
    at org.apache.cassandra.service.StorageService.decommission(StorageService.java:5351)
```

## Root Cause Analysis

### Test Design

The test `testDecommissionAfterNodeRestart` uses ByteBuddy to intercept `StorageService.prepareUnbootstrapStreaming()` and simulate a decommission failure:

```java
.withInstanceInitializer((classLoader, threadGroup, num, generation) -> {
    // we do not want to install BB after restart of a node which
    // failed to decommission which is the second generation, here
    // as "1" as it is counted from 0.
    if (num == 1 && generation != 1)
        BB.install(classLoader, num);
})
```

The test expects:
- **Generation 0** (initial): BB installed → first decommission fails (intentional)
- **Generation 1** (after test restart): BB NOT installed → second decommission succeeds

### How the Restart Adapter Breaks This

When the restart adapter injects a restart at position "after_failed_decommission":

| Step | Action | Generation | BB Installed | invocations |
|------|--------|------------|--------------|-------------|
| 1 | Initial cluster start | 0 | YES (`0 != 1` → true) | 0 |
| 2 | First decommission | 0 | YES | 1 → throws |
| 3 | **Adapter restarts node** | 1 | NO (`1 != 1` → false) | reset to 0 |
| 4 | Test's own restart | **2** | **YES (`2 != 1` → true)** | reset to 0 |
| 5 | Second decommission | 2 | YES | 1 → **throws!** |

The restart adapter shifts the generation counter by 1, so when the test performs its own restart (expecting generation 1), it actually gets generation 2, which causes BB to be installed again.

### Why This is a False Positive

1. **The test intentionally uses ByteBuddy** to simulate decommission failures based on generation counting
2. **The restart adapter adds an extra restart** that the test logic doesn't account for
3. **The generation counter shift** causes the BB interceptor to be installed when it shouldn't be
4. **The second decommission fails** not because of a bug in Cassandra, but because the test's fault injection logic is triggered again

This is similar to FP-GROUP-17 where shared JVM state (in this case, generation counting) causes unexpected behavior when the restart adapter injects restarts.

## Why This Cannot Be a Real Bug

The failure occurs in test-specific ByteBuddy bytecode that:
1. Is not part of Cassandra's production code
2. Is specifically designed to fail on first invocation
3. Depends on generation counting that the restart adapter modifies

Without the ByteBuddy interceptor, the decommission would succeed normally.

## Recommendation

The restart position "after_failed_decommission" is incompatible with tests that use generation-dependent fault injection. Tests that rely on specific generation values for ByteBuddy installation should be excluded from restart testing at positions that add extra restarts.

Alternatively, the restart adapter could track whether a restart has been injected and adjust behavior accordingly, or the test framework could be enhanced to account for injected restarts in generation counting.
