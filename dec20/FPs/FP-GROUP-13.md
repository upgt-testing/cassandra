# False Positive Report: Group 13 - TimeoutException in SEPExecutor Termination

## Summary

**Group**: 13
**Classification**: False Positive (FP)
**Root Cause**: Restart injected at inappropriate position where test's CyclicBarrier blocks executor tasks

## Error Details

```
java.util.concurrent.TimeoutException: org.apache.cassandra.concurrent.SEPExecutor@... did not terminate on time
  at org.apache.cassandra.utils.ExecutorUtils.awaitTerminationUntil(ExecutorUtils.java:111)
  at org.apache.cassandra.utils.ExecutorUtils.awaitTermination(ExecutorUtils.java:100)
  at org.apache.cassandra.concurrent.Stage.shutdownAndWait(Stage.java:193)
  at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$42(Instance.java:915)
```

## Affected Tests

1. `QueriesTableTest_RestartInjected.shouldExposeReadsAndWrites` - position: `after_async_queries`
2. `QueriesTableTest_RestartInjected.shouldExposeCAS` - position: `cas_after_async_update`

## Analysis

### Test Design

The test (`QueriesTableTest_RestartInjected`) uses ByteBuddy to intercept `Mutation.apply()` and `ReadCommand.executeLocally()` methods. These interceptors use `CyclicBarrier(2)` to deliberately block operations:

```java
public static class QueryDelayHelper
{
    private static final CyclicBarrier readBarrier = new CyclicBarrier(2);
    private static final CyclicBarrier writeBarrier = new CyclicBarrier(2);

    // In apply() for writes:
    if (keyspace.getName().contains(KEYSPACE))
        writeBarrier.await();  // Blocks until 2 threads reach this point

    // In executeLocally() for reads:
    if (executionController.metadata().keyspace.contains(KEYSPACE))
        readBarrier.await();  // Blocks until 2 threads reach this point
}
```

### Restart Injection Point

The restart is injected at `after_async_queries`:

```java
// Lines 97-98: Async queries are submitted - they get blocked by CyclicBarrier
SESSION.executeAsync("INSERT INTO " + KEYSPACE + ".tbl (k, v) VALUES (0, 0)");
SESSION.executeAsync("SELECT * FROM " + KEYSPACE + ".tbl WHERE k = 0");

// Lines 100-105: Restart injected HERE
RestartFramework.at("after_async_queries")
    .on(SHARED_CLUSTER)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Lines 125-126: These queries would normally unblock the barriers
SESSION.execute("INSERT INTO " + KEYSPACE + ".tbl (k, v) VALUES (0, 0)");
SESSION.execute("SELECT * FROM " + KEYSPACE + ".tbl WHERE k = 0");
```

### Why the Timeout Occurs

1. The async INSERT (line 97) blocks at `writeBarrier.await()` waiting for a 2nd party
2. The async SELECT (line 98) blocks at `readBarrier.await()` waiting for a 2nd party
3. Restart is triggered before lines 125-126 can provide the 2nd party
4. During graceful shutdown, `Stage.shutdownAndWait(1L, MINUTES)` tries to terminate executors
5. Tasks are indefinitely blocked on `CyclicBarrier.await()` - they cannot complete
6. After 1 minute, the timeout exception is thrown

## Why This is a False Positive

1. **Not a Cassandra Bug**: The Cassandra source code behaves correctly. The shutdown timeout mechanism is working as designed - it waits for tasks to complete and reports failure if they don't.

2. **Not a Test Bug**: The original test (`QueriesTableTest`) works correctly. The CyclicBarrier is intentionally used to delay queries so the test can observe them in `system_views.queries`.

3. **Restart Position Problem**: The restart is injected at a position where:
   - Test synchronization primitives (CyclicBarrier) are actively blocking tasks
   - The normal unblocking mechanism (subsequent queries at lines 125-126) cannot execute
   - Tasks blocked on `await()` cannot be gracefully interrupted/terminated

4. **Expected Behavior**: When threads are blocked on `CyclicBarrier.await()`, they will stay blocked until either:
   - All parties arrive at the barrier
   - The barrier is reset/broken
   - The thread is interrupted (which doesn't happen in graceful shutdown)

## Conclusion

This failure is a **False Positive** caused by injecting a restart at a position where the test's own synchronization infrastructure (CyclicBarrier) prevents clean shutdown. The restart framework does not understand that the test is using barriers to coordinate threads, and injecting a restart while tasks are blocked on these barriers will inevitably cause shutdown timeouts.

**Recommendation**: Skip restart injection at positions like `after_async_queries` where the test intentionally blocks threads using synchronization primitives.
