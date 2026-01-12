# FP-GROUP-13: TimeoutException in ExecutorUtils - CyclicBarrier Blocking

## Classification: FALSE POSITIVE

## Summary

The test uses ByteBuddy to inject a `CyclicBarrier` that intentionally blocks queries. When the restart framework triggers at the "after_async_queries" position, the queries are still blocked waiting for a second party to join the barrier. The SEPExecutor cannot terminate within the timeout because worker threads are blocked on the barrier, not because of any bug in Cassandra.

## Root Cause

### Test Design

The `QueriesTableTest` uses ByteBuddy to intercept `Mutation.apply()` and `ReadCommand.executeLocally()` methods:

```java
public static class QueryDelayHelper
{
    private static final CyclicBarrier readBarrier = new CyclicBarrier(2);
    private static final CyclicBarrier writeBarrier = new CyclicBarrier(2);

    @SuppressWarnings("unused")
    public static void apply(Keyspace keyspace, boolean durableWrites, boolean isDroppable, @SuperCall Callable<Void> zuper)
    {
        try
        {
            if (keyspace.getName().contains(KEYSPACE))
                writeBarrier.await();  // <-- Blocks here waiting for second thread

            zuper.call();
        }
        catch (Exception e)
        {
            throw Throwables.unchecked(e);
        }
    }

    // Similar for executeLocally with readBarrier
}
```

Each barrier expects 2 threads to call `await()`. The test issues async queries that block on these barriers, then issues synchronous queries to unblock them. This allows the test to verify that in-progress queries are visible in `system_views.queries`.

### Restart Position Problem

The restart position "after_async_queries" is placed between the async and sync queries:

```java
SESSION.executeAsync("INSERT INTO " + KEYSPACE + ".tbl (k, v) VALUES (0, 0)");
SESSION.executeAsync("SELECT * FROM " + KEYSPACE + ".tbl WHERE k = 0");

RestartFramework.at("after_async_queries")  // <-- Restart triggered here!
    .on(SHARED_CLUSTER)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// ... later ...
// Issue another read and write to unblock the original queries in progress:
SESSION.execute("INSERT INTO ...");  // <-- Would unblock writeBarrier
SESSION.execute("SELECT ...");       // <-- Would unblock readBarrier
```

When restart is triggered:
1. Async INSERT is blocked on `writeBarrier.await()` in the Mutation stage
2. Async SELECT is blocked on `readBarrier.await()` in the Read stage
3. Neither barrier will be released because the unblocking queries haven't been issued yet

### Shutdown Flow

When the restart adapter attempts graceful shutdown:

1. `Instance.shutdown()` calls `Stage.shutdownAndWait(1L, MINUTES)`
2. `Stage.shutdownAndWait()` calls `ExecutorUtils.shutdownNow(executors)` followed by `ExecutorUtils.awaitTermination()`
3. `SEPExecutor.shutdownNow()` drains the pending task queue but does NOT interrupt running tasks
4. Worker threads are blocked in `task.run()` on the CyclicBarrier
5. The 1-minute timeout expires before workers complete, causing `TimeoutException`

### Why SEPExecutor Doesn't Interrupt Tasks

The `SEPExecutor.shutdownNow()` implementation:

```java
public synchronized List<Runnable> shutdownNow()
{
    shutdown();
    List<Runnable> aborted = new ArrayList<>();
    while (takeTaskPermit(false) == TOOK_PERMIT)
        aborted.add(tasks.poll());
    return aborted;
}
```

This only drains the queue, it doesn't interrupt worker threads. This is likely intentional in Cassandra's design to prevent interrupting critical operations like writes. The `SharedExecutorPool.terminateWorkers()` only unparks sleeping/spinning workers:

```java
void terminateWorkers()
{
    // Only unparks workers, doesn't interrupt running tasks
    Map.Entry<Long, SEPWorker> e;
    while (null != (e = descheduled.pollFirstEntry()))
        e.getValue().assign(Work.SPINNING, false);

    while (null != (e = spinning.pollFirstEntry()))
        LockSupport.unpark(e.getValue().thread);
}
```

## Why This Is A False Positive

1. **Test-Specific Blocking**: The `CyclicBarrier` blocking is injected by the test harness using ByteBuddy. Real Cassandra queries don't block indefinitely waiting for another query to arrive.

2. **Invalid Restart Position**: The restart position "after_async_queries" is triggered when queries are intentionally blocked. This is not a valid point to restart a node.

3. **Normal Operation Works**: In normal Cassandra operation, queries complete and executors can terminate. The test explicitly designs a scenario where queries never complete.

4. **Design Decision**: The SEPExecutor not interrupting running tasks is a design choice to prevent corruption of in-flight operations. This is not a bug.

## Affected Tests

1. `QueriesTableTest_RestartInjected.shouldExposeReadsAndWrites` at position "after_async_queries"
2. `QueriesTableTest_RestartInjected.shouldExposeCAS` at position "cas_after_async_update"

## Recommendation

The restart framework should not inject restarts at positions where queries are known to be intentionally blocked by test harnesses. The test's use of CyclicBarrier creates an artificial deadlock scenario that doesn't represent real-world Cassandra behavior.
