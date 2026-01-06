# FP-GROUP-10: RuntimeException - Simulated decommission error

## Classification: FALSE POSITIVE

## Summary
This failure is a **False Positive** because the exception "simulated error in prepareUnbootstrapStreaming" is intentionally thrown by test infrastructure code (ByteBuddy interceptor), not by Cassandra source code. The restart framework injection disrupts the test's carefully designed generation-based ByteBuddy installation sequence.

## Root Cause
```
java.lang.RuntimeException: simulated error in prepareUnbootstrapStreaming
at org.apache.cassandra.distributed.test.DecommissionTest$BB.prepareUnbootstrapStreaming(DecommissionTest.java:208)
at org.apache.cassandra.service.StorageService.prepareUnbootstrapStreaming(StorageService.java)
```

## Analysis

### Test Design
The test `testDecommissionAfterNodeRestart` is specifically designed to verify that:
1. A decommission fails (simulated by ByteBuddy interceptor)
2. The node is manually restarted
3. After restart, the node can successfully decommission

### ByteBuddy Interceptor
The test uses a ByteBuddy interceptor (`BB.prepareUnbootstrapStreaming`) that:
```java
private static int invocations = 0;

public static Supplier<Future<StreamState>> prepareUnbootstrapStreaming(...) {
    ++invocations;
    if (invocations == 1)
        throw new RuntimeException("simulated error in prepareUnbootstrapStreaming");
    // Otherwise delegate to the real method
    return zuper.call();
}
```

### Generation-Based Installation
The test installs the interceptor conditionally based on node generation:
```java
.withInstanceInitializer((classLoader, threadGroup, num, generation) -> {
    // we do not want to install BB after restart of a node which
    // failed to decommission which is the second generation, here
    // as "1" as it is counted from 0.
    if (num == 1 && generation != 1)
        BB.install(classLoader, num);
})
```

This means:
- Generation 0 (initial start): BB installed → first decommission fails (simulated)
- Generation 1 (after first restart): BB NOT installed → second decommission succeeds
- Generation 2+: BB installed again

### Restart Framework Interference
When the restart framework injects a restart at `after_failed_decommission`:
1. The framework performs a graceful restart → generation increments
2. The manual `stopUnchecked(instance); instance.startup();` → generation increments again
3. The generation counting becomes misaligned with what the test expects
4. BB may be installed when it shouldn't be, OR the static counter is reset due to ClassLoader changes

### Evidence
1. **Original test passes**: Running `DecommissionTest.testDecommissionAfterNodeRestart` without restart injection completes successfully
2. **Error source is test code**: The exception originates from `DecommissionTest$BB.prepareUnbootstrapStreaming` at line 208 - this is test infrastructure code, not Cassandra source code
3. **Intentional simulation**: The error message "simulated error in prepareUnbootstrapStreaming" explicitly states this is a simulated error for testing purposes

## Reason for False Positive Classification
1. **No bug in Cassandra source code**: The exception is thrown by test infrastructure (ByteBuddy interceptor), not by actual Cassandra code
2. **Test-specific infrastructure**: The BB interceptor is specifically designed to simulate a failure for testing recovery scenarios
3. **Restart injection disrupts test design**: The additional restart from the framework throws off the generation-based installation sequence that the test carefully orchestrates
4. **Original test works correctly**: The test passes when run without restart injection

## Affected Test Executions
1. Test: `DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart`
   - position: `after_failed_decommission`
   - target: `node`
   - mode: `GRACEFUL`
   - index: `0`

2. Test: `DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart`
   - position: `after_manual_restart`
   - target: `node`
   - mode: `GRACEFUL`
   - index: `0`
