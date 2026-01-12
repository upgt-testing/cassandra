# FP-GROUP-6: ConfigurationException in StorageService.prepareToJoin

## Verdict: FALSE POSITIVE

## Summary

The restart framework attempts to restart a node **after it has been successfully decommissioned**. Cassandra correctly rejects this restart because a decommissioned node cannot rejoin the cluster without explicit override flags. This is expected Cassandra behavior, not a bug.

## Error Details

```
ConfigurationException: This node was decommissioned and will not rejoin the ring unless -Dcassandra.override_decommission=true has been set, or all existing data is removed and the node is bootstrapped again
    at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1144)
```

## Root Cause Analysis

### Cassandra Source Code (StorageService.java:1135-1146)

```java
if (SystemKeyspace.wasDecommissioned())
{
    if (OVERRIDE_DECOMMISSION.getBoolean())
    {
        logger.warn("This node was decommissioned, but overriding by operator request.");
        SystemKeyspace.setBootstrapState(SystemKeyspace.BootstrapState.COMPLETED);
    }
    else
    {
        throw new ConfigurationException("This node was decommissioned and will not rejoin the ring unless -D" + OVERRIDE_DECOMMISSION.getKey() +
                                         "=true has been set, or all existing data is removed and the node is bootstrapped again");
    }
}
```

This is **intentional safety behavior** in Cassandra. When a node is decommissioned:
1. It leaves the cluster permanently
2. Its tokens are reassigned to other nodes
3. It marks itself as DECOMMISSIONED in `system.local`
4. It should NOT rejoin without operator intervention

### Test Execution Flow

Looking at `testDecommission()` in `DecommissionTest_RestartInjected.java`:

```java
instance.runOnInstance(() -> {
    // First decommission attempt fails (simulated)
    // ...

    // Second decommission attempt SUCCEEDS
    StorageService.instance.decommission(true);
    assertEquals(DECOMMISSIONED.name(), StorageService.instance.getBootstrapState());  // Line 98
    // ...
});

// Restart injection point is AFTER the node has been decommissioned
RestartFramework.at("after_decommission_tests")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

The restart position `after_decommission_tests` is placed **after** the node has been successfully decommissioned (bootstrap state = DECOMMISSIONED). At this point:

1. Node 1 has completed decommission
2. Its bootstrap state is `DECOMMISSIONED`
3. The restart framework tries to restart it
4. Cassandra correctly rejects the restart

## Why This Is A False Positive

1. **Expected Cassandra Behavior**: The `ConfigurationException` is a deliberate safety mechanism to prevent accidental rejoining of decommissioned nodes. This protects data integrity.

2. **Invalid Restart Position**: The restart positions in all failing tests (`after_decommission_tests`, `after_decommission`, `after_successful_decommission`) are placed after the node has been decommissioned. Restarting a decommissioned node is semantically invalid - a decommissioned node is meant to be permanently removed from the cluster.

3. **Not A Bug**: The source code is working correctly. The test framework is attempting an operation that Cassandra explicitly disallows by design.

4. **Design Intent**: This behavior is documented in Cassandra and requires the `-Dcassandra.override_decommission=true` flag to override, confirming this is a conscious design decision.

## Affected Test Executions (4 total)

All share the same characteristic: restart injection **after** decommission:

| Test | Position | Index |
|------|----------|-------|
| `DecommissionTest_RestartInjected.testDecommission` | after_decommission_tests | 0 |
| `HintedHandoffAddRemoveNodesTest_RestartInjected.shouldAvoidHintTransferOnDecommission` | after_decommission | 0 |
| `DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart` | after_successful_decommission | 0 |
| `LCSStreamingKeepLevelTest_RestartInjected.testDecom` | after_decommission | 3 |

## Recommendation

These restart positions should be filtered out by the restart testing framework since restarting a decommissioned node is not a valid test scenario. Alternatively, these positions could be renamed to clearly indicate they should not be used for restart injection (e.g., `after_decommission_DO_NOT_RESTART`).
