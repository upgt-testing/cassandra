# FP-GROUP-7: IllegalStateException in AbstractCluster$Wrapper.shutdown

## Classification: FALSE POSITIVE

**Root Cause**: Restart adapter attempts to shutdown an already-shutdown node

## Summary

The failure occurs when the restart framework attempts to perform a graceful restart on a node that has already been shut down. The restart position `after_stop_all_2` (or `after_stop_all`, `after_both_nodes_shutdown`) is triggered after the test has already called `stopAll(cluster)` or equivalent, meaning all nodes are already down.

## Error Stack Trace

```
Caused by: java.lang.IllegalStateException: Instance is not running, so can not be shutdown
    at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.shutdown(AbstractCluster.java:449)
    at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:159)
```

## Affected Tests

1. `HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNodeAndOtherNodeStartsAfter`
   - position: `after_stop_all_2`
2. `HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNode`
   - position: `after_stop_all`
3. `RestartTest_RestartInjected.test`
   - position: `after_both_nodes_shutdown`

## Technical Analysis

### Test Flow (hostReplacementOfDeadNodeAndOtherNodeStartsAfter)

The test at `HostReplacementOfDownedClusterTest.java:120-179` performs:

1. Creates a 3-node cluster
2. Sets up the cluster with data
3. **Line 142**: Calls `stopAll(cluster)` - ALL nodes are shut down
4. The restart position `after_stop_all_2` triggers HERE
5. The test then continues to restart nodes manually

### What Happens During Restart

1. Restart framework invokes `CassandraClusterAdapter.restartNode()` for node at index 0
2. `performGracefulRestart()` is called (line 152-179)
3. At line 159, it calls `instance.shutdown(true)` without checking if already shut down
4. `AbstractCluster$Wrapper.shutdown()` throws `IllegalStateException` because:

```java
// AbstractCluster.java:446-449
public synchronized Future<Void> shutdown(boolean graceful)
{
    if (isShutdown())
        throw new IllegalStateException("Instance is not running, so can not be shutdown");
    // ...
}
```

### Why This Is a False Positive

1. **Invalid Restart Position**: The position `after_stop_all_2` represents a state where all nodes are already shut down. Attempting to "restart" (which implies shutdown + startup) a node that is already down is semantically invalid.

2. **Correct Cassandra Behavior**: The `AbstractCluster$Wrapper.shutdown()` method correctly implements a safety check to prevent double-shutdown. This is defensive programming, not a bug.

3. **Adapter Limitation**: The `CassandraClusterAdapter.performGracefulRestart()` should check `instance.isShutdown()` before attempting shutdown. The missing check is in the restart adapter, not Cassandra source code.

## Potential Fix (In Restart Adapter)

```java
// In CassandraClusterAdapter.performGracefulRestart()
private void performGracefulRestart(Cluster cluster, IInvokableInstance instance) throws Exception {
    int nodeNum = instance.config().num();

    try {
        // Check if node is already shut down
        if (instance.isShutdown()) {
            logger.info("Node {} is already shut down, just starting it up", nodeNum);
            instance.startup();
        } else {
            // Step 1: Gracefully shutdown the node
            logger.info("Gracefully shutting down node {}", nodeNum);
            Future<Void> shutdownFuture = instance.shutdown(true);
            FBUtilities.waitOnFuture(shutdownFuture);

            Thread.sleep(1000);

            // Step 2: Restart the node
            logger.info("Starting up node {}", nodeNum);
            instance.startup();
        }
        // ... rest of method
    } catch (Exception e) {
        throw new RuntimeException("Failed to gracefully restart node " + nodeNum, e);
    }
}
```

## Conclusion

This is a FALSE POSITIVE. The failure is caused by:
1. An invalid restart position (restarting an already-down node)
2. Missing `isShutdown()` check in the restart adapter

The Cassandra source code is working correctly by throwing an exception when attempting to shutdown an already-shutdown instance.
