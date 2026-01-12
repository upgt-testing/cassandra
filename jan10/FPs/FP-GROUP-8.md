# FP-GROUP-8: CasWriteTimeoutException (Paxos)

## Summary

**Verdict**: FALSE POSITIVE - In-memory ring state lost after restart

**Root Cause**: The test uses `addToRingBootstrapping()` to set up specific in-memory ring topology where node 4 is in "bootstrapping" state. When the restart adapter restarts a node, this in-memory state is lost, and the restarted node reverts to its persisted ring state. This causes ring view inconsistency across nodes, leading to Paxos CAS timeout.

## Failure Details

**Exception**: `CasWriteTimeoutException: CAS operation timed out: received 1 of 2 required responses after 0 contention retries`

**Test**: `CASTest_RestartInjected.testIncompleteWriteFollowedBySuccessfulWriteWithStaleRingDuringRangeMovementFollowedByRead`

**Restart Position**: `after_bootstrap_setup`

**Target**: Node 2 (index=1)

**Mode**: GRACEFUL

## Technical Analysis

### Test Setup

The test (`testIncompleteWriteFollowedBySuccessfulWriteWithStaleRingDuringRangeMovementFollowedByRead`) is designed to test Paxos behavior during range movement. It sets up a specific ring topology:

1. Remove node 4 from ring on all nodes:
```java
for (int i = 1 ; i <= 4 ; ++i)
{
    FOUR_NODES.get(i).acceptsOnInstance(CASTestBase::removeFromRing).accept(FOUR_NODES.get(4));
}
```

2. Add node 4 as "bootstrapping" on nodes 2, 3, 4 (but NOT on node 1):
```java
for (int i = 2 ; i <= 4 ; ++i)
{
    FOUR_NODES.get(i).acceptsOnInstance(CASTestBase::addToRingBootstrapping).accept(FOUR_NODES.get(4));
}
```

This creates a specific ring view:
- Node 1: Doesn't see node 4
- Node 2, 3, 4: See node 4 as "bootstrapping"

### Ring State Manipulation Methods

The `addToRingBootstrapping()` method in `CASTestBase.java` manipulates in-memory Gossiper state:

```java
public static void addToRing(boolean bootstrapping, IInstance peer)
{
    Gossiper.runInGossipStageBlocking(() -> {
        Gossiper.instance.initializeNodeUnsafe(address, hostId, 1);
        Gossiper.instance.injectApplicationState(address, ApplicationState.TOKENS, ...);
        VersionedValue status = bootstrapping
            ? new VersionedValue.VersionedValueFactory(partitioner).bootstrapping(...)
            : new VersionedValue.VersionedValueFactory(partitioner).normal(...);
        Gossiper.instance.injectApplicationState(address, ApplicationState.STATUS, status);
        StorageService.instance.onChange(address, ApplicationState.STATUS, status);
        Gossiper.instance.realMarkAlive(address, ...);
    });
}
```

This is purely **in-memory manipulation** - it modifies Gossiper's runtime state without persisting to disk or propagating via actual gossip protocol.

### What Happens on Restart

When node 2 is restarted at position `after_bootstrap_setup`:

1. Node 2 shuts down gracefully
2. Node 2 restarts and reloads its ring state from:
   - System tables (persisted peer info)
   - Gossip protocol (live peer discovery)
3. The programmatically-set "bootstrapping" state is NOT preserved
4. Node 2 sees node 4 as a **full member** (the original state when cluster was created)

### Debug Evidence

Debug logging confirmed the ring state change:

**Before Restart:**
```
DEBUG: Node 1 sees node4: alive=false, member=false, bootstrapping=false
DEBUG: Node 2 sees node4: alive=true, member=false, bootstrapping=true  <-- Correct
DEBUG: Node 3 sees node4: alive=true, member=false, bootstrapping=true
DEBUG: Node 4 sees node4: alive=true, member=false, bootstrapping=true
```

**After Restart:**
```
DEBUG: Node 1 sees node4: alive=false, member=false, bootstrapping=false
DEBUG: Node 2 sees node4: alive=true, member=true, bootstrapping=false  <-- Changed!
DEBUG: Node 3 sees node4: alive=true, member=false, bootstrapping=true
DEBUG: Node 4 sees node4: alive=true, member=false, bootstrapping=true
```

### Why CAS Timeout Occurs

With inconsistent ring views:
- Node 2 sees 4 members (1, 2, 3, 4) - calculates quorum as 3
- Node 3, 4 see node 4 as bootstrapping - different quorum calculation

When node 4 coordinates a CAS operation with message filters dropping certain messages, the quorum calculations are inconsistent across nodes. The Paxos protocol cannot reach consensus because different nodes have different views of who should participate in the quorum.

## Why This Is a False Positive

1. **Invalid Restart Position**: The restart position `after_bootstrap_setup` is invalid for this test because:
   - The test specifically relies on programmatically-set in-memory ring state
   - This state is intentionally NOT persisted (it simulates gossip propagation scenarios)
   - Restarting a node at this point invalidates the test's assumptions

2. **Test Design**: The test is designed to test Paxos behavior in specific ring topology scenarios. It manipulates in-memory state to create these scenarios without actually bootstrapping a node. This is a valid test design for the original test, but incompatible with the restart framework.

3. **Not a Cassandra Bug**: The CAS timeout is a direct consequence of:
   - Ring state inconsistency introduced by the restart
   - Not a bug in Cassandra's Paxos implementation
   - Cassandra correctly rejects CAS operations when quorum cannot be achieved

4. **Expected Behavior After Restart**: It is correct behavior for a restarted node to reload its ring state from persisted/gossiped data. The programmatic manipulation was never meant to survive restarts.

## Affected Tests

All three failures in Group 8 are from CAS tests with ring manipulation:
1. `testIncompleteWriteFollowedBySuccessfulWriteWithStaleRingDuringRangeMovementFollowedByRead`
2. `testSucccessfulWriteDuringRangeMovementFollowedByRead`
3. `testSuccessfulWriteDuringRangeMovementFollowedByConflicting`

All share the same pattern: using `addToRingBootstrapping()` to set up specific ring topology, then restarting at `after_bootstrap_setup`.

## Recommendation

The restart framework should avoid injecting restarts at positions where:
1. In-memory state has been programmatically manipulated
2. The test relies on specific gossip/ring topology that isn't persisted

For CAS tests specifically, valid restart positions would be:
- Before any ring manipulation
- After the test logic completes (during cleanup)
- NOT during mid-test ring topology setup
