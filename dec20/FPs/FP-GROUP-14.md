# FP-GROUP-14: InterruptedException during bootstrap reads

## Classification: FALSE POSITIVE

## Summary
The failure occurs because the test uses a static `CountDownLatch` to artificially block read queries, and when the node is restarted during this blocked state, the thread waiting on the latch is interrupted. This is expected behavior during node shutdown, not a bug.

## Failure Details

**Root Cause:**
```
java.lang.InterruptedException
    at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireSharedInterruptibly(AbstractQueuedSynchronizer.java:1048)
    at java.base/java.util.concurrent.CountDownLatch.await(CountDownLatch.java:230)
    at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected$BB.getCachedReplicas(ReadsDuringBootstrapTest_RestartInjected.java:170)
```

**Test:** `ReadsDuringBootstrapTest_RestartInjected.readsDuringBootstrapTest`
**Position:** `after_bootstrap_and_join`
**Target:** node index 0 (Cassandra node 1)
**Mode:** GRACEFUL

## Analysis

### Test Structure
The test (`ReadsDuringBootstrapTest_RestartInjected.java`) is designed to test read behavior during bootstrap:

1. Creates a 3-node cluster
2. Uses ByteBuddy to intercept `AbstractReplicationStrategy.getCachedReplicas()` on node 1
3. When `block.set(true)`, reads block on a static `CountDownLatch.await()` (line 170)
4. Submits a read query (line 92) that gets blocked
5. Bootstraps a 4th node
6. Eventually unblocks and completes

### Relevant Code (lines 149-173):
```java
public static class BB
{
    public static final AtomicBoolean block = new AtomicBoolean();
    public static final CountDownLatch latch = new CountDownLatch(1);

    private static void install(ClassLoader cl, Integer instanceId)
    {
        if (instanceId != 1)
            return;
        // ByteBuddy intercepts getCachedReplicas for node 1
    }

    public static EndpointsForRange getCachedReplicas(long ringVersion, Token t,
                                                      @FieldValue("keyspaceName") String keyspaceName,
                                                      @SuperCall Callable<EndpointsForRange> zuper) throws Exception
    {
        if (keyspaceName.equals(KEYSPACE) && block.get())
            latch.await();  // <-- Thread blocks here
        return zuper.call();
    }
}
```

### Why This is a False Positive

1. **Test-specific blocking mechanism**: The `CountDownLatch` is a test infrastructure component used to artificially pause read operations. This is not production code behavior.

2. **Expected shutdown behavior**: When `instance.shutdown(true)` is called (in `CassandraClusterAdapter.performGracefulRestart()`), the shutdown process interrupts all waiting threads. This is correct JVM behavior - blocking operations should be interrupted during shutdown.

3. **Static field persistence issue**: The `latch` is declared as `static`, meaning it persists across class loader boundaries. When the node restarts, the new instance cannot interact with the latch state from the test harness, causing coordination issues.

4. **Improper restart position**: The restart occurs while:
   - A read query is submitted and blocked (line 92: `es.submit(() -> cluster.coordinator(1).execute(...))`
   - The block is still active (`block.set(true)`)
   - The latch has not been counted down yet

   Restarting at this point doesn't test any meaningful production scenario.

### Execution Flow Leading to Failure

1. Line 83: `cluster.get(1).runOnInstance(() -> BB.block.set(true))` - blocking enabled
2. Line 92: `Future<?> read = es.submit(...)` - read query submitted, gets blocked on `latch.await()`
3. Line 104-109: Restart at `after_bootstrap_and_join` on node index 0 (node 1)
4. `CassandraClusterAdapter.performGracefulRestart()` calls `instance.shutdown(true)`
5. Shutdown process interrupts the thread blocked on `latch.await()`
6. `InterruptedException` propagates up to `read.get()` at line 141

## Conclusion

This is a **False Positive** because:
- The failure is caused by restarting a node during a test-specific artificial blocking state
- Thread interruption during shutdown is expected and correct behavior
- No bug exists in Cassandra source code or production logic
- The restart position is improper for this test's coordination mechanism

The restart testing framework correctly identified that injecting a restart at this position would cause problems, but the problems are in the test infrastructure (artificial blocking), not in Cassandra itself.
