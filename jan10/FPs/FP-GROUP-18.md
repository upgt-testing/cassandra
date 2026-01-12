# FP-GROUP-18: KeyspaceNotDefinedException - Restart Framework Stale Reference Refresh Bug

## Summary

This failure is a **False Positive** caused by the restart framework's stale reference refresh mechanism incorrectly recreating a different node (node3) when node2 is restarted. The framework's "on-demand refresh" feature replays the bootstrap chain for node3, creating a fresh instance without the test keyspace schema.

## Error Details

**Exception**: `org.apache.cassandra.db.KeyspaceNotDefinedException`

**Stack Trace**:
```
org.apache.cassandra.db.KeyspaceNotDefinedException: keyspace distributed_test_keyspace does not exist
    at org.apache.cassandra.schema.Schema.validateTable(Schema.java:436)
    at org.apache.cassandra.cql3.statements.SelectStatement$RawStatement.prepare(SelectStatement.java:1215)
    at org.apache.cassandra.cql3.statements.SelectStatement$RawStatement.prepare(SelectStatement.java:1210)
    at org.apache.cassandra.cql3.statements.SelectStatement$RawStatement.prepare(SelectStatement.java:1182)
    at org.apache.cassandra.cql3.QueryProcessor.parseAndPrepare(QueryProcessor.java:458)
    at org.apache.cassandra.cql3.QueryProcessor.parseAndPrepare(QueryProcessor.java:438)
    at org.apache.cassandra.cql3.QueryProcessor.prepareInternal(QueryProcessor.java:431)
    at org.apache.cassandra.distributed.impl.Instance.unsafeExecuteInternalWithResult(Instance.java:274)
    at org.apache.cassandra.distributed.impl.Instance.lambda$executeInternalWithResult$1(Instance.java:265)
    ...
```

## Failure Context

| Field | Value |
|-------|-------|
| Test Class | `org.apache.cassandra.distributed.test.ring.PendingWritesTest_RestartInjected` |
| Test Method | `testPendingWrites` |
| Restart Position | `after_node_join` |
| Restart Target | `node` |
| Restart Mode | `GRACEFUL` |
| Restart Index | `1` (node2) |

## Test Flow

1. Cluster starts with 2 nodes (node1, node2)
2. `BootstrapTest.populate()` creates `distributed_test_keyspace` with table `tbl` on node1 and node2
3. A third node (node3) is bootstrapped via `cluster.bootstrap(config)`
4. Node3 joins the cluster and receives schema for `distributed_test_keyspace`
5. At `after_node_join` position, **node2** (index 1) is restarted
6. After restart, the framework's reference tracking marks `newInstance` (node3) for on-demand refresh
7. When the test uses `newInstance`, the framework triggers on-demand refresh
8. The framework replays `cluster.bootstrap()` to recreate node3
9. New node3 instance doesn't have `distributed_test_keyspace` schema
10. `BootstrapTest.count(cluster)` tries to query `distributed_test_keyspace.tbl` on node3 and fails

## Root Cause Analysis

The restart framework's stale reference refresh mechanism has a bug:

### Evidence from Logs

```
INFO  [main] <main> 2026-01-11 13:20:36,852 ReferenceRegistry.java:495 - Refreshing 1 tracked references
[REFRESH-DEBUG] Clearing cache for local org.apache.cassandra.distributed.test.ring.PendingWritesTest_RestartInjected.testPendingWrites.var14:org/apache/cassandra/distributed/api/IInvokableInstance, removed=Wrapper
DEBUG [main] <main> 2026-01-11 13:20:36,852 ReferenceRegistry.java:524 - Marked local variable org.apache.cassandra.distributed.test.ring.PendingWritesTest_RestartInjected.testPendingWrites.var14:org/apache/cassandra/distributed/api/IInvokableInstance for on-demand refresh
```

Later when the test uses `newInstance`:
```
[REFRESH-DEBUG] getRefreshedOrOriginal called: key=org.apache.cassandra.distributed.test.ring.PendingWritesTest_RestartInjected.testPendingWrites.var14:org/apache/cassandra/distributed/api/IInvokableInstance
[REFRESH-DEBUG] cached value=null
[REFRESH-DEBUG] reference lookup: found, isLocal=true
[REFRESH-DEBUG] On-demand refresh: replaying chain cluster.bootstrap({auto_bootstrap=false, broadcast_address=127.0.0.3, ...})
DEBUG [main] <main> 2026-01-11 13:20:36,855 ExpressionChain.java:197 - Replaying chain starting from cluster
```

The refresh then fails:
```
WARN  19:20:37 Failed to on-demand refresh local org.apache.cassandra.distributed.test.ring.PendingWritesTest_RestartInjected.testPendingWrites.var14:org/apache/cassandra/distributed/api/IInvokableInstance: null
```

### Problem Description

1. **Incorrect Reference Tracking**: When node2 is restarted, the framework marks node3's `IInvokableInstance` (stored in `var14`) for on-demand refresh, even though node3 was not restarted.

2. **Faulty On-Demand Refresh**: The framework attempts to recreate node3 by replaying `cluster.bootstrap()`, but:
   - This creates a fresh node3 instance without the `distributed_test_keyspace` schema
   - The new node3 is not properly integrated into the cluster
   - Schema synchronization doesn't occur during this replay

3. **No Impact on Cassandra Source**: The Cassandra source code is correct - the schema validation in `Schema.validateTable()` correctly throws an exception when the keyspace doesn't exist.

## Why This is a False Positive

1. **Framework Bug, Not Cassandra Bug**: The failure is caused by the restart framework's reference tracking system, not by any bug in Cassandra's source code or test code.

2. **Incorrect Node Recreation**: Node3 should NOT be recreated when node2 is restarted. The framework incorrectly marks node3 for refresh.

3. **Schema Loss Due to Replay**: The on-demand refresh mechanism replays `cluster.bootstrap()` which creates a new node without proper schema synchronization.

4. **Test Logic is Correct**: The test correctly expects all nodes to have the `distributed_test_keyspace` keyspace. The framework's interference breaks this expectation.

## Affected Component

**Restart Testing Framework** - specifically the stale reference refresh mechanism in `ReferenceRegistry` and `ExpressionChain`.

## Recommendation

The restart framework's reference tracking should:
1. Only mark references that are directly related to the restarted node for refresh
2. Not mark unrelated nodes' `IInvokableInstance` references for on-demand refresh
3. Properly handle schema synchronization if node recreation is necessary
