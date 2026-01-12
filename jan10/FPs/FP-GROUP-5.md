# FP-GROUP-5: RuntimeException in Gossiper.doShadowRound

## Classification: FALSE POSITIVE (FP)

## Summary

This failure is a False Positive caused by an invalid restart injection position. The restart occurs when all peer nodes are down, making it impossible for the restarting node to complete its gossip shadow round - this is expected Cassandra behavior, not a bug.

## Error Details

**Exception**:
```
java.lang.RuntimeException: Unable to gossip with any peers
	at org.apache.cassandra.gms.Gossiper.doShadowRound(Gossiper.java:2075)
	at org.apache.cassandra.service.StorageService.checkForEndpointCollision(StorageService.java:866)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1181)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
```

**Test Execution**:
- Test: `HostReplacementTest_RestartInjected.seedGoesDownBeforeDownHost`
- Position: `after_node_stop`
- Target: `node`
- Mode: `GRACEFUL`
- Index: `2` (node 3)

## Root Cause Analysis

### Test Scenario
The `seedGoesDownBeforeDownHost` test has a 3-node cluster and proceeds as follows:
1. Node 1 (seed), Node 2 (nodeToRemove), Node 3 (nodeToStayAlive) start
2. Setup cluster and populate data
3. `stopUnchecked(seed)` - Node 1 goes DOWN
4. `stopUnchecked(nodeToRemove)` - Node 2 goes DOWN
5. **`after_node_stop` restart injection point** - Node 3 (index 2) is restarted here
6. (After restart) `seed.startup()` - Node 1 comes back up
7. Host replacement proceeds...

### State at Restart Position
At the `after_node_stop` position:
- **Node 1 (seed)**: DOWN
- **Node 2 (nodeToRemove)**: DOWN
- **Node 3 (nodeToStayAlive)**: Being restarted by framework

### Why This Fails

Node 3 is **not a seed node**. When a non-seed node starts, it must perform a "shadow gossip round" to check for endpoint collision (as per Cassandra's safety mechanism). This requires contacting at least one live peer.

From `Gossiper.java:2073-2075`:
```java
// if we got here no peers could be gossiped to. If we're a seed that's OK, but otherwise we stop. See CASSANDRA-13851
if (!isSeed)
    throw new RuntimeException("Unable to gossip with any peers");
```

Since both Node 1 and Node 2 are down at this point, Node 3 has no peers to gossip with, causing the expected failure.

### This is Expected Behavior

This is **not a bug** - it's an intentional safety mechanism documented in CASSANDRA-13851. A non-seed node that cannot contact any peers during startup should fail rather than risk creating a split-brain scenario.

From `StorageService.java:869`:
```java
if (epStates.isEmpty() && DatabaseDescriptor.getSeeds().contains(FBUtilities.getBroadcastAddressAndPort()))
    logger.info("Unable to gossip with any peers but continuing anyway since node is in its own seed list");
```

Only seed nodes are allowed to start without peer contact.

## Conclusion

**This is a FALSE POSITIVE** because:

1. **Invalid restart position**: The `after_node_stop` position is inappropriate for this test - at this point, 2 of 3 nodes are down, leaving no peers for Node 3 to contact
2. **Expected Cassandra behavior**: Non-seed nodes failing when they can't contact any peers is the intended design (CASSANDRA-13851)
3. **No source code bug**: The exception is thrown by correct, intentional code that prevents unsafe node startup scenarios

## Recommendation

This restart position should be excluded from valid test scenarios for this particular test, or the framework should detect that restarting a non-seed node when all peers are down is an invalid scenario.

## Reproduction

```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64 && \
ant test-jvm-dtest-some \
  -Dtest.name=org.apache.cassandra.distributed.test.hostreplacement.HostReplacementTest_RestartInjected \
  -Dtest.methods=seedGoesDownBeforeDownHost \
  -Dno-build-test=true \
  -Dtest.jvm.args="-Drestart.position=after_node_stop -Drestart.target=node -Drestart.mode=GRACEFUL -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar"
```

## Related Test Executions

All 6 failures in this group are likely caused by similar scenarios where non-seed nodes are restarted when their peers are unavailable:

1. `HostReplacementTest_RestartInjected.seedGoesDownBeforeDownHost` - after_node_stop, index 2
2. `JMXFeatureTest_RestartInjected.testShutDownAndRestartInstances` - after_instance_stop, index 1
3. `JMXFeatureTest_RestartInjected.testShutDownAndRestartInstances` - after_ring_status_down, index 1
4. (and 3 more similar scenarios)
