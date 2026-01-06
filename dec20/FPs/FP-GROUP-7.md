# FP-GROUP-7: IllegalStateException - Shutdown instance delegate null

## Summary
**Classification: FALSE POSITIVE (FP)**

This failure occurs because the restart framework attempts to gracefully restart a node that has already been intentionally shut down by the test logic. The restart positions (`after_stop_all_2`, `after_stop_all`, `after_both_nodes_shutdown`) are placed at points where all cluster nodes have been stopped, making a "graceful restart" semantically meaningless.

## Root Cause
```
java.lang.IllegalStateException: Can't use shutdown instances, delegate is null
at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.delegate(AbstractCluster.java:285)
at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.nodetoolResult(AbstractCluster.java:474)
```

Or in the reproduced case:
```
java.lang.IllegalStateException: Instance is not running, so can not be shutdown
at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.shutdown(AbstractCluster.java:449)
```

## Reproduction
**Test:** `HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNodeAndOtherNodeStartsAfter`
**Position:** `after_stop_all_2`
**Mode:** `GRACEFUL`
**Index:** `0`

Command:
```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64 && ant test-jvm-dtest-some \
    -Dtest.name=org.apache.cassandra.distributed.test.hostreplacement.HostReplacementOfDownedClusterTest_RestartInjected \
    -Dtest.methods=hostReplacementOfDeadNodeAndOtherNodeStartsAfter \
    -Dno-build-test=true \
    -Dtest.jvm.args="-Drestart.position=after_stop_all_2 -Drestart.target=node -Drestart.mode=GRACEFUL"
```

**Result:** Failure reproduced

## Analysis

### Test Flow Analysis

Looking at `HostReplacementOfDownedClusterTest_RestartInjected.java`:

```java
// Line 221-222: Stop all nodes in the cluster
stopAll(cluster);

// Line 224-229: Restart point placed AFTER all nodes are stopped
RestartFramework.at("after_stop_all_2")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

The test scenario is:
1. A 3-node cluster is created and configured
2. `stopAll(cluster)` is called, which shuts down all 3 nodes
3. The restart point `after_stop_all_2` attempts to restart node 0
4. But node 0 is already stopped - you can't "gracefully" shut down and restart something that's not running

### Why This Is a False Positive

1. **Semantically Invalid Restart Position**: The restart position `after_stop_all_2` occurs AFTER all nodes have been intentionally stopped by the test. At this point:
   - All nodes are in a shutdown state
   - The test expects all nodes to be down
   - The next test step is to selectively restart the seed node

2. **Graceful Restart Requires Running Instance**: A "graceful restart" operation assumes the node is currently running so it can:
   - Drain pending requests
   - Announce shutdown to the cluster
   - Flush data to disk
   - Then restart

   None of this is possible on an already-stopped node.

3. **Test Intent**: The test is specifically testing the scenario of host replacement after a complete cluster outage. The `stopAll()` call is intentional and represents a simulated DC outage. Injecting a restart at this point contradicts the test's purpose.

4. **Same Pattern in All Affected Tests**: All three test executions in this group follow the same pattern:
   - `after_stop_all_2` in `hostReplacementOfDeadNodeAndOtherNodeStartsAfter`
   - `after_stop_all` in `hostReplacementOfDeadNode`
   - `after_both_nodes_shutdown` in `RestartTest.test`

   All positions are placed right after all nodes are intentionally stopped.

### Affected Test Executions (3 total)
1. `HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNodeAndOtherNodeStartsAfter`
   - Position: `after_stop_all_2`
2. `HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNode`
   - Position: `after_stop_all`
3. `RestartTest_RestartInjected.test`
   - Position: `after_both_nodes_shutdown`

## Conclusion

This is a **False Positive** caused by improper restart position placement. The restart framework should not attempt to restart nodes at positions where they have been intentionally stopped by the test logic.

### Recommendation
The restart positions `after_stop_all`, `after_stop_all_2`, and `after_both_nodes_shutdown` should be removed from the restart configuration, or the restart framework should detect that the target node is already stopped and skip the restart operation gracefully.
