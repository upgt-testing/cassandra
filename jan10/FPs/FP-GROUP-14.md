# FP-GROUP-14: InterruptedException in CountDownLatch.await

## Classification: FALSE POSITIVE

## Summary

The test uses ByteBuddy to deliberately block queries on a `CountDownLatch`. When the restart adapter restarts the node while queries are blocked, the waiting threads are interrupted, causing `InterruptedException`. This is expected JVM behavior, not a Cassandra bug.

## Test Information

- **Test Class**: `org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected`
- **Test Method**: `readsDuringBootstrapTest`
- **Restart Positions**: `after_bootstrap_and_join`, `after_cache_population`
- **Target**: `node`, Index: `0`
- **Mode**: `GRACEFUL`
- **Test Executions**: 2 failures

## Stack Trace

```
java.util.concurrent.ExecutionException: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
    at java.base/java.util.concurrent.FutureTask.report(FutureTask.java:122)
    at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:191)
    at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected.readsDuringBootstrapTest(ReadsDuringBootstrapTest_RestartInjected.java:141)
Caused by: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
    at org.apache.cassandra.distributed.impl.IsolatedExecutor.waitOn(IsolatedExecutor.java:285)
    ...
Caused by: java.lang.InterruptedException
    at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireSharedInterruptibly(AbstractQueuedSynchronizer.java:1048)
    at java.base/java.util.concurrent.CountDownLatch.await(CountDownLatch.java:230)
    at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected$BB.getCachedReplicas(ReadsDuringBootstrapTest_RestartInjected.java:170)
    at org.apache.cassandra.locator.AbstractReplicationStrategy.getCachedReplicas(AbstractReplicationStrategy.java)
    ...
```

## Root Cause Analysis

### Test Design

The test uses ByteBuddy to intercept `AbstractReplicationStrategy.getCachedReplicas()` on node 1:

```java
// ReadsDuringBootstrapTest_RestartInjected.java:149-172
public static class BB
{
    public static final AtomicBoolean block = new AtomicBoolean();
    public static final CountDownLatch latch = new CountDownLatch(1);

    private static void install(ClassLoader cl, Integer instanceId)
    {
        if (instanceId != 1)
            return;
        new ByteBuddy().rebase(AbstractReplicationStrategy.class)
                       .method(named("getCachedReplicas"))
                       .intercept(MethodDelegation.to(BB.class))
                       .make()
                       .load(cl, ClassLoadingStrategy.Default.INJECTION);
    }

    public static EndpointsForRange getCachedReplicas(long ringVersion, Token t,
                                                      @FieldValue("keyspaceName") String keyspaceName,
                                                      @SuperCall Callable<EndpointsForRange> zuper) throws Exception
    {
        if (keyspaceName.equals(KEYSPACE) && block.get())
            latch.await();  // <-- Blocks here until latch.countDown() is called
        return zuper.call();
    }
}
```

### Test Flow at Failure Point

1. **Line 83**: `cluster.get(1).runOnInstance(() -> BB.block.set(true));` - Enables blocking
2. **Line 92**: `Future<?> read = es.submit(() -> cluster.coordinator(1).execute(query, ...));` - Submits query that will block
3. **Line 102**: `bootstrapAndJoinNode(cluster);` - Bootstraps node 4
4. **Lines 104-109**: **RESTART INJECTION** at `after_bootstrap_and_join` restarts node 1 (index 0)
5. The query from step 2 is still blocked on `latch.await()` when restart occurs

### Why This Happens

1. The query submitted at line 92 executes on node 1 as coordinator
2. Due to the ByteBuddy intercept, when `getCachedReplicas()` is called, the query blocks on `latch.await()`
3. The restart adapter triggers at position `after_bootstrap_and_join` and attempts to restart node 1
4. Graceful shutdown interrupts all worker threads on node 1
5. The thread blocked on `latch.await()` receives an interrupt, throwing `InterruptedException`
6. This propagates up as `UncheckedInterruptedException`

### Why This is NOT a Bug

1. **Test-Specific Blocking**: The blocking is deliberately introduced by the test using ByteBuddy and CountDownLatch - this is not normal Cassandra behavior
2. **Expected Interrupt Behavior**: When a JVM thread is blocked on `CountDownLatch.await()` and receives an interrupt, it throws `InterruptedException` - this is standard Java behavior
3. **Invalid Restart Position**: The restart occurs while a query is intentionally blocked. In production, queries would not be artificially blocked like this
4. **Graceful Shutdown**: The restart adapter correctly performs graceful shutdown, which includes interrupting blocked threads

## Comparison with Similar FPs

This is similar to **FP-GROUP-13** where the test uses a `CyclicBarrier` to block queries, and restarting during the block causes `TimeoutException` because the executor cannot terminate while threads are blocked.

Both cases share the pattern:
- Test uses synchronization primitives (CountDownLatch/CyclicBarrier) to deliberately block operations
- Restart injection triggers while operations are blocked
- Shutdown/restart naturally interrupts the blocked operations
- This is not a Cassandra bug but an invalid restart position for tests with deliberate blocking

## Verdict

**FALSE POSITIVE** - The test deliberately blocks queries using a CountDownLatch synchronization mechanism. Restarting a node while threads are blocked on synchronization primitives will naturally cause those threads to be interrupted. This is expected JVM/Cassandra behavior, not a bug in Cassandra source code. The restart position is invalid for this test scenario.

## Recommendation

The restart framework should avoid restart positions that trigger while test-specific blocking mechanisms (CountDownLatch, CyclicBarrier, Semaphore, etc.) are active. Alternatively, these test positions should be filtered out as invalid restart candidates for tests that use such synchronization primitives.
