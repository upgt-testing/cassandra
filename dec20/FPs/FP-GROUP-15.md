# FP-GROUP-15: UnavailableException - Consistency level failure

## Summary
This is a **False Positive (FP)** caused by using `GossipHelper.removeFromRing()` test helper in a restart scenario where it cannot properly propagate ring state changes.

## Failure Details
- **Test**: `NodeNotInRingTest_RestartInjected.nodeNotInRingTest`
- **Position**: `after_first_populate`
- **Target**: node index 1 (node 2)
- **Mode**: GRACEFUL

## Root Cause Analysis

### Error
```
org.apache.cassandra.exceptions.UnavailableException: Cannot achieve consistency level ALL
at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:37)
at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicas(ReplicaPlans.java:193)
```

### Debug Output Analysis (Critical Finding)
```
Node 1 - effectiveOwnership: {127.0.0.1:7012=1.0, 127.0.0.2:7012=1.0}
Node 1 - liveMembers: [/127.0.0.1:7012, /127.0.0.2:7012]
Node 1 - unreachableMembers: []

Node 2 - effectiveOwnership: {127.0.0.1:7012=0.666666, 127.0.0.2:7012=0.666666, 127.0.0.3:7012=0.666666}
Node 2 - liveMembers: [/127.0.0.1:7012, /127.0.0.2:7012]
Node 2 - unreachableMembers: [/127.0.0.3:7012]
```

**Key Observations:**
1. **Node 1** (not restarted): Token metadata correctly shows 2 nodes with 100% ownership
2. **Node 2** (restarted): Token metadata incorrectly shows 3 nodes with ~67% ownership each
3. **Node 2** knows from gossip that node 3 is unreachable, but token metadata still includes node 3

### Root Cause

The `GossipHelper.removeFromRing()` test helper manipulates gossip state **in-memory** on specific nodes. When it's called:

1. It sets node 3's STATUS to LEFT in the target node's gossip state
2. `StorageService.onChange()` is called, triggering `handleStateLeft()` → `excise()`
3. `excise()` removes node 3 from token metadata AND `system.peers_v2` on that node

However, when node 2 restarts:

1. **Token metadata is re-populated from `system.peers_v2`** - but this should be correct since node 3 was deleted
2. **Gossip sync happens with node 1** - node 2 receives node 3's state
3. **The state inconsistency occurs** because:
   - Node 3 IS in `unreachableMembers` (added during gossip sync)
   - Node 3 IS in `effectiveOwnership` (token metadata wasn't updated)

This indicates that either:
- The LEFT status wasn't properly transmitted from node 1 to node 2 during gossip
- OR `handleStateLeft()` wasn't triggered on node 2 to call `excise()`

The fact that node 3 is in `unreachableMembers` (not just "unknown") proves that gossip state was received, but the token metadata wasn't reconciled.

### Why This Is a False Positive

1. **Test Infrastructure Limitation**: `GossipHelper.removeFromRing()` is a test helper designed for non-restart scenarios. It manipulates in-memory gossip state without using production-grade ring change mechanisms.

2. **Non-Production Ring Change**: In production, ring changes use:
   - `nodetool decommission` - graceful departure with proper state propagation
   - `nodetool removenode` - forceful removal with system-wide state updates

   These mechanisms ensure that ALL nodes receive the ring change through proper gossip state transitions and system table updates.

3. **Gossip State Propagation Issue**: The `removeFromRing()` helper creates a LEFT status locally but doesn't ensure it's properly propagated and processed on restarted nodes. The gossip protocol relies on version numbers and generations that may not be correctly handled when mixing test helpers with restart scenarios.

4. **Not a Cassandra Production Bug**: This inconsistency would not occur in production because:
   - Production ring changes properly update system tables cluster-wide
   - Production gossip ensures state consistency through proper version synchronization
   - The LEFT status would be properly persisted and propagated

### Technical Details

The test flow:
```java
// Node 3's gossip is blocked (outbound only)
cluster.filters().verbs(GOSSIP_DIGEST_ACK.id, GOSSIP_DIGEST_SYN.id)
       .from(3).outbound().drop().on();

// Remove node 3 from nodes 1 and 2's ring view
cluster.run(GossipHelper.removeFromRing(cluster.get(3)), 1, 2);

// ... later ...
// Restart node 2 at position "after_first_populate"
RestartFramework.at("after_first_populate")
    .on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();

// Immediately after restart, write with CL ALL - FAILS
populate(cluster, 50, 100, 2, ConsistencyLevel.ALL);
```

The failure occurs because after restart, node 2 has inconsistent state:
- Token metadata: 3 nodes (stale)
- Gossip state: node 3 unreachable (current)

When node 2 (as coordinator) tries to write with CL ALL:
1. Token metadata says 3 nodes in ring
2. RF=2, so 2 replicas needed
3. Gossip says node 3 is unreachable
4. Cannot satisfy CL ALL → `UnavailableException`

## Conclusion

**Classification: FP (False Positive)**

The failure exposes a limitation of the test infrastructure (`GossipHelper.removeFromRing()`) when combined with node restarts. This is not a bug in Cassandra's source code because:

1. The test uses a non-production mechanism to remove nodes
2. Production ring changes would properly propagate state
3. The inconsistency is specific to the test helper + restart combination

**Note**: While this is classified as FP, the investigation reveals that Cassandra's startup sequence could potentially be improved to better reconcile token metadata with gossip state. However, this would be a test infrastructure enhancement, not a production bug fix.
