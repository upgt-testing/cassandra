# FP-GROUP-9: UnavailableException During Counter Mutation

## Classification: FALSE POSITIVE

## Summary
The `UnavailableException` occurs because the test uses a keyspace with `replication_factor=1`, meaning each piece of data is stored on only one node. When that node is restarted and not yet detected as "alive" by the FailureDetector, counter mutations correctly fail with `UnavailableException`. This is expected Cassandra behavior - there is zero fault tolerance with RF=1.

## Failure Details

**Test Executions**:
1. `CountersTest_RestartInjected.testUpdateCounter` - position: `after_table_create`, target: node, mode: GRACEFUL, index: 0
2. `CountersTest_RestartInjected.testUpdateCounterWithDroppedCompactStorage` - position: `after_counter_increment`, target: node, mode: GRACEFUL, index: 0

**Stack Trace**:
```
org.apache.cassandra.exceptions.UnavailableException: Cannot achieve consistency level ONE
    at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:37)
    at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:31)
    at org.apache.cassandra.service.StorageProxy.findSuitableReplica(StorageProxy.java:1782)
    at org.apache.cassandra.service.StorageProxy.mutateCounter(StorageProxy.java:1725)
    at org.apache.cassandra.service.StorageProxy.mutate(StorageProxy.java:892)
    ...
```

## Root Cause Analysis

### Test Setup
The test (`CountersTest.java`) creates:
1. A 2-node cluster with GOSSIP and NATIVE_PROTOCOL
2. A keyspace with `replication_factor = 1`
3. A counter table

```java
cluster.schemaChange("CREATE KEYSPACE k WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
```

### Counter Mutation Flow
Counter mutations in Cassandra have special handling. In `StorageProxy.mutateCounter()`, the code calls `findSuitableReplica()` to find a live replica to coordinate the counter update:

```java
// StorageProxy.java:1767-1782
private static Replica findSuitableReplica(String keyspaceName, DecoratedKey key,
                                           String localDataCenter, ConsistencyLevel cl)
                                           throws UnavailableException {
    Keyspace keyspace = Keyspace.open(keyspaceName);
    IEndpointSnitch snitch = DatabaseDescriptor.getEndpointSnitch();
    AbstractReplicationStrategy replicationStrategy = keyspace.getReplicationStrategy();
    EndpointsForToken replicas = replicationStrategy.getNaturalReplicasForToken(key);

    // CASSANDRA-13043: filter out those endpoints not accepting clients yet
    replicas = replicas.filter(replica -> StorageService.instance.isRpcReady(replica.endpoint()));

    // CASSANDRA-17411: filter out endpoints that are not alive
    replicas = replicas.filter(replica -> FailureDetector.instance.isAlive(replica.endpoint()));

    // TODO have a way to compute the consistency level
    if (replicas.isEmpty())
        throw UnavailableException.create(cl, cl.blockFor(replicationStrategy), 0);  // LINE 1782
    ...
}
```

### What Happens During Restart
1. **Before restart**: Node 1 is operational, FailureDetector marks it as "alive"
2. **Restart position `after_table_create`**: Node 1 (index=0) is restarted
3. **After restart completes**: The restart adapter starts the node back up, but there's a window where:
   - Node 1 is starting up
   - Node 2's FailureDetector hasn't yet received enough heartbeats to mark Node 1 as "alive"
   - `FailureDetector.instance.isAlive(node1)` returns `false`
4. **Counter mutation attempt**: When the test continues executing counter updates via node 2:
   - For keys that hash to node 1's token range (with RF=1, that's the ONLY replica)
   - `findSuitableReplica()` filters out node 1 (not alive)
   - `replicas.isEmpty()` becomes true
   - `UnavailableException` is thrown

### Why This is Expected Behavior

1. **RF=1 means no fault tolerance**: With replication_factor=1, each piece of data exists on exactly one node. If that node is unavailable, the data is unavailable.

2. **Counter mutations require a live replica**: Unlike regular writes that can use hints/hinted handoff, counter mutations must be coordinated by a live replica because counters have special consistency requirements.

3. **FailureDetector correctly identifies unavailable nodes**: The FailureDetector marks nodes as "down" when heartbeats stop arriving. This is the correct safety mechanism to prevent routing requests to crashed nodes.

4. **Cassandra correctly enforces consistency requirements**: When `ConsistencyLevel.ONE` is requested but zero replicas are alive, throwing `UnavailableException` is the correct behavior.

## Why This is Not a Bug

This is how Cassandra is designed to work:

1. **Documented behavior**: With RF=1, you have no redundancy. Any node outage makes that node's data unavailable.

2. **Consistency guarantees**: Cassandra cannot return success for a write that didn't meet the requested consistency level. Silently accepting writes that can't be replicated would violate consistency.

3. **CASSANDRA-13043 and CASSANDRA-17411**: The filtering logic that causes this exception was explicitly added to prevent routing requests to nodes that aren't ready to serve traffic.

## Conclusion

The failure is a **False Positive** because:
- The restart framework injects a restart at a position that causes expected temporary unavailability
- The test's use of `replication_factor=1` provides zero fault tolerance
- Cassandra correctly throws `UnavailableException` when the consistency level cannot be satisfied
- This is expected operational behavior, not a bug

The proper fix would be for the restart framework to either:
1. Skip restart positions that would cause obvious unavailability (like restarting the only replica)
2. Or for tests using RF=1 to be excluded from restart injection
