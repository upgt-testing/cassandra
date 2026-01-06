# FP-GROUP-9: RejectedExecutionException - isolatedExecutor has shut down

## Classification: FALSE POSITIVE

## Summary
This failure group represents a false positive caused by stale coordinator reference usage after a node restart. The test code stores a coordinator reference before the restart and attempts to use it after the restart, resulting in a `RejectedExecutionException` because the underlying executor has been shut down during the restart.

## Error Details
```
java.util.concurrent.RejectedExecutionException: isolatedExecutor has shut down
    at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java:49)
    at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java:833)
    at java.base/java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java:1365)
    at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.addTask(ThreadPoolExecutorPlus.java:50)
    at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:81)
    at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:33)
    at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$6(IsolatedExecutor.java:150)
    at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java:151)
    at org.apache.cassandra.distributed.impl.Coordinator.executeWithPagingWithResult(Coordinator.java:180)
    at org.apache.cassandra.distributed.api.ICoordinator.executeWithPaging(ICoordinator.java:49)
    at org.apache.cassandra.distributed.test.GroupByTest_RestartInjected.testGroupWithDeletesAndPaging(GroupByTest_RestartInjected.java:204)
```

## Root Cause Analysis

The test `testGroupWithDeletesAndPaging` has the following code pattern:

```java
// Line 186: Get coordinator BEFORE restart
ICoordinator coordinator = cluster.coordinator(1);

coordinator.execute(withKeyspace("INSERT INTO %s.tbl (pk, ck) VALUES (0, 0)"), ConsistencyLevel.ALL);
coordinator.execute(withKeyspace("INSERT INTO %s.tbl (pk, ck) VALUES (1, 1)"), ConsistencyLevel.ALL);

// Lines 189-194: Restart node 1 (index 0)
RestartFramework.at("after_inserts")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// ... some operations ...

// Line 204: Use the OLD coordinator reference AFTER restart
String query = withKeyspace("SELECT * FROM %s.tbl GROUP BY pk");
Iterator<Object[]> rows = coordinator.executeWithPaging(query, ConsistencyLevel.ALL, 1);
```

**The Problem:**
1. The `coordinator` object is obtained at line 186 (before the restart)
2. Node 1 (index 0) is restarted at lines 189-194
3. During the restart, the old `isolatedExecutor` for node 1 is shut down
4. At line 204, the test tries to use the OLD coordinator reference
5. The coordinator still references the OLD, now-shut-down executor
6. Result: `RejectedExecutionException` because the executor has been terminated

## Why This is a False Positive

1. **Stale Reference Issue**: The test was not designed with restart injection in mind. It stores a coordinator reference and expects to reuse it throughout the test. When a restart occurs, this reference becomes stale.

2. **Expected Behavior**: When a node is restarted:
   - The old instance's executor is shut down
   - A new instance is started with a new executor
   - Old references to the previous executor are no longer valid

3. **Not a Bug in Cassandra**: This is expected behavior in both Cassandra source code and the testing framework. If a node restarts, clients holding old references must refresh their connections.

4. **Test Code Issue**: If the test wanted to survive restarts, it would need to refresh the coordinator reference after the restart:
   ```java
   // After restart, get a fresh coordinator reference
   coordinator = cluster.coordinator(1);
   ```

## Affected Tests
- `GroupByTest_RestartInjected.testGroupWithDeletesAndPaging` (position: after_inserts)
- `HintsServiceMetricsTest_RestartInjected.testHintsServiceMetrics` (position: after_first_half_writes)

## Conclusion
This failure is a false positive because it results from the restart testing framework injecting a restart at a position where the test code holds stale references. The test was not designed with restarts in mind, and the failure represents expected behavior when using stale executor references after a node restart.
