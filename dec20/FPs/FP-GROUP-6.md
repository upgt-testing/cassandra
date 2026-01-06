# False Positive Report - Group 6: ConfigurationException - Decommissioned Node Rejoin

## Summary
This failure is a **False Positive** caused by attempting to restart a node that has been intentionally decommissioned. Cassandra correctly prevents decommissioned nodes from rejoining the ring.

## Error Details
```
org.apache.cassandra.exceptions.ConfigurationException: This node was decommissioned and will not rejoin the ring unless -Dcassandra.override_decommission=true has been set, or all existing data is removed and the node is bootstrapped again
at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1144)
```

## Affected Test Executions
1. `DecommissionTest_RestartInjected.testDecommission` - position: `after_decommission_tests`
2. `HintedHandoffAddRemoveNodesTest_RestartInjected.shouldAvoidHintTransferOnDecommission` - position: `after_decommission`
3. `DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart` - position: `after_successful_decommission`

## Root Cause Analysis

### Test Flow in `testDecommission()`
1. Creates a 2-node cluster
2. Node 1 first decommission attempt fails (simulated error via ByteBuddy)
3. Node 1 second decommission attempt **succeeds**
4. Node 1's bootstrap state becomes `DECOMMISSIONED`
5. Restart framework attempts to restart node 1 at position `after_decommission_tests`

### Cassandra's Protection Mechanism
The failure originates from `StorageService.prepareToJoin()` (line 1135-1147):

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

This is an **intentional safety mechanism** in Cassandra:
- A decommissioned node has given up its tokens and data responsibilities
- Allowing it to rejoin without explicit operator approval could cause data inconsistencies
- The node's state is persisted in system keyspace, so this check survives restarts

## Why This Is a False Positive

1. **Expected Cassandra Behavior**: The ConfigurationException is the correct response when a decommissioned node attempts to rejoin without the override flag.

2. **Test Intentionally Decommissions the Node**: The test explicitly calls `StorageService.instance.decommission(true)` and verifies `assertEquals(DECOMMISSIONED.name(), StorageService.instance.getBootstrapState())`.

3. **Inappropriate Restart Position**: The restart point `after_decommission_tests` is placed after the node has been successfully decommissioned. Restarting a decommissioned node is an invalid operation without the override flag.

4. **Not a Bug**: This is not a bug in Cassandra source code or test code. Cassandra is functioning exactly as designed.

## Conclusion
This is a False Positive because:
- The restart framework attempted to restart a node in an invalid state (DECOMMISSIONED)
- Cassandra correctly rejected the restart attempt with an informative error message
- The override flag (`-Dcassandra.override_decommission=true`) exists for legitimate use cases where an operator intentionally wants to bring back a decommissioned node

The restart framework should either:
1. Skip restart injection at positions after node decommission
2. Or recognize that restarting decommissioned nodes is an expected failure scenario
