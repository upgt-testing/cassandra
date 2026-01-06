# FP-GROUP-4: RuntimeException - Unable to gossip with any peers

## Summary
**Type:** False Positive (FP)
**Failure:** `java.lang.RuntimeException: Unable to gossip with any peers`
**Root Cause:** Improper restart position - attempting to restart a node after gossip isolation/filtering has been deliberately set up by the test

## Stack Trace
```
java.lang.RuntimeException: Unable to gossip with any peers
    at org.apache.cassandra.gms.Gossiper.doShadowRound(Gossiper.java:2075)
    at org.apache.cassandra.service.StorageService.checkForEndpointCollision(StorageService.java:866)
    at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1181)
    at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
    at org.apache.cassandra.service.StorageService.initServer(StorageService.java:947)
    at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java:708)
```

## Affected Test Executions (10 total)

| Test | Position | Node Index | Pattern |
|------|----------|------------|---------|
| NodeNotInRingTest.nodeNotInRingTest | after_node_removal | 2 | Node removed from ring + gossip filtered |
| GossipShutdownTest.shutdownStayDownTest | after_filter_setup | 1 | Gossip messages dropped by filter |
| HostReplacementTest.seedGoesDownBeforeDownHost | after_node_stop | 2 | Node stopped, cannot reach peers |
| JMXFeatureTest.testShutDownAndRestartInstances | after_instance_stop | 1 | Instance already stopped |
| JMXFeatureTest.testShutDownAndRestartInstances | after_ring_status_down | 1 | Node marked as down |
| JMXFeatureTest.testShutDownAndRestartInstances | after_nodetool_status_down | 1 | Node marked as down |
| GossipShutdownTest.emptyStateAndShutdownEvent | after_filter_setup | 1 | Gossip messages dropped |
| HintedHandoffAddRemoveNodesTest.shouldStreamHintsDuringDecommission | after_final_decommissions | 3 | Node decommissioned |
| GossipShutdownTest.emptyStateAndHostJoin | after_filter_setup_host | 1 | Gossip messages dropped |
| GossipSettlesTest.testGossipSettles | after_verification | 1 | Various gossip scenarios |

## Analysis

### Why This Is a False Positive

1. **Gossip Isolation is Intentional Test Behavior**

   All affected tests deliberately set up gossip isolation scenarios before the restart position:

   - **NodeNotInRingTest**: Sets up a filter to drop gossip messages from node 3, then removes node 3 from the ring view of other nodes
   - **GossipShutdownTest**: Sets up filters to drop `GOSSIP_DIGEST_SYN` and `GOSSIP_DIGEST_ACK` messages
   - **HostReplacementTest**: Stops specific nodes to simulate failure scenarios
   - **Decommission tests**: Decommission nodes which makes them unable to rejoin

2. **Shadow Round Correctly Fails**

   When a Cassandra node starts up, it performs a "shadow round" to check for endpoint collision:
   ```java
   // Gossiper.java:2071-2075
   if (slept > shadowRoundDelay) {
       // if we got here no peers could be gossiped to
       if (!isSeed)
           throw new RuntimeException("Unable to gossip with any peers");
   }
   ```

   This is correct behavior when:
   - The node's gossip messages are being dropped by filters
   - The node has been removed from the ring
   - Other nodes consider this node as down/left

3. **Example: NodeNotInRingTest**

   ```java
   // Set up gossip filter to drop messages FROM node 3
   cluster.filters().verbs(Verb.GOSSIP_DIGEST_ACK.id, Verb.GOSSIP_DIGEST_SYN.id)
          .from(3).outbound().drop().on();

   // Remove node 3 from ring view of nodes 1 and 2
   cluster.run(GossipHelper.removeFromRing(cluster.get(3)), 1, 2);

   // Restart at "after_node_removal" tries to restart node 3
   // Node 3 cannot gossip because:
   //   a) Its outbound gossip messages are dropped by filter
   //   b) Other nodes consider it removed from the ring
   ```

4. **Example: GossipShutdownTest**

   ```java
   // Drop ALL gossip SYN messages from node 2 to node 1
   cluster.filters().outbound().from(2).to(1)
          .verbs(GOSSIP_DIGEST_SYN.id).messagesMatching(...).drop();

   // Drop gossip ACK messages from node 2 to node 1
   cluster.filters().outbound().from(2).to(1)
          .verbs(GOSSIP_DIGEST_ACK.id).messagesMatching(...).drop();

   // Restart at "after_filter_setup" tries to restart node 2
   // Node 2 cannot gossip because its messages to node 1 are dropped
   ```

### The Restart Position is Semantically Invalid

The restart positions in Group 4 all share a common characteristic: they occur **after** the test has deliberately isolated a node from gossip communication. Restarting a node at these positions is semantically invalid because:

1. The test's purpose is to verify behavior when nodes are isolated/removed
2. The isolation is a prerequisite for the test's verification logic
3. Restarting the isolated node defeats the purpose of the test
4. The failure is expected behavior, not a bug

## Conclusion

This is a **False Positive** because:
- The tests deliberately create gossip isolation scenarios
- The "Unable to gossip with any peers" error is the **correct and expected** behavior for a node that has been isolated
- The restart positions are improper because they attempt to restart nodes that have been intentionally cut off from cluster communication
- There is no bug in Cassandra's source code - the gossip shadow round correctly detects that no peers can be reached

## Recommendation

These restart positions should be excluded from restart testing because they represent intentionally invalid cluster states where nodes are expected to fail during restart.
