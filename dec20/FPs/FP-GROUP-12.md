# False Positive Report: Group 12 - TransportException Connection Closed

## Classification: FALSE POSITIVE (FP)

## Summary
The TransportException "Connection has been closed" failure is a false positive caused by injecting a node restart while async queries are still in-flight.

## Failure Details

**Root Cause:**
```
com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
```

**Test:** `OverloadTest_RestartInjected.applyClientBackpressure`
**Restart Position:** `after_queries_submitted`
**Restart Mode:** GRACEFUL

## Analysis

### Test Behavior
The test `applyClientBackpressure` is designed to test Cassandra's client backpressure mechanism:

1. The test submits 256 async queries (128 + 128) to the Cassandra node:
   ```java
   for (int i = 0; i < count; i++)
       futures.add(CompletableFuture.supplyAsync(() -> session.execute("select * from tbl").one(), executor));
   Thread.sleep(700);
   for (int i = 0; i < count; i++)
       futures.add(CompletableFuture.supplyAsync(() -> session.execute("select * from tbl").one(), executor));
   ```

2. The restart is triggered at `after_queries_submitted` position BEFORE the futures are processed:
   ```java
   RestartFramework.at("after_queries_submitted")
       .on(control)
       .restart("node")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

3. When processing futures (lines 147-167), the test only handles specific exceptions:
   - `OperationTimedOutException`
   - `ReadTimeoutException`
   - `OverloadedException`

4. Any other exception type causes the test to re-throw:
   ```java
   else throw e;  // Line 165
   ```

### Why This is a False Positive

1. **Expected Behavior During Restart**: When a Cassandra node is gracefully restarted, all existing client connections are closed. This is normal and expected behavior - not a bug.

2. **Test Not Designed for Restarts**: The original test was designed to test backpressure behavior under load, not to handle unexpected node restarts. The error handling only accounts for timeout and overload scenarios.

3. **In-Flight Queries**: The restart is injected at a position where many async queries are still pending. When the node shuts down, these queries fail because their connections are closed.

4. **No Cassandra Code Bug**: The `TransportException` is thrown by the DataStax driver (client side), not by Cassandra server code. The exception correctly indicates that the connection was closed, which happened because of the injected restart.

### Restart Timing Issue

The restart point `after_queries_submitted` is problematic because:
- Queries have been submitted but not yet processed
- The driver-side futures are waiting for responses
- The restart closes all connections, orphaning the in-flight queries
- This is not a scenario the original test was designed to handle

## Stack Trace Analysis

```
java.util.concurrent.ExecutionException: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
    at java.base/java.util.concurrent.CompletableFuture.reportGet(CompletableFuture.java:396)
    at java.base/java.util.concurrent.CompletableFuture.get(CompletableFuture.java:2073)
    at OverloadTest_RestartInjected.testApplyClientBackpressure(OverloadTest_RestartInjected.java:154)
...
Caused by: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
    at com.datastax.driver.core.exceptions.TransportException.copy(TransportException.java:40)
    ...
    at OverloadTest_RestartInjected.lambda$testApplyClientBackpressure$1(OverloadTest_RestartInjected.java:133)
```

The exception originates from:
1. `lambda$testApplyClientBackpressure$1` - the async query submission
2. DataStax driver's connection handling code
3. The driver detecting that the connection has been closed

## Conclusion

This failure is a **False Positive** because:
- The exception is caused by the injected restart, not by a Cassandra bug
- Closing connections during node restart is expected and correct behavior
- The test's error handling was not designed to accommodate restart scenarios
- The restart position (`after_queries_submitted`) is inappropriate for this test as it has in-flight queries

## Recommendation

The restart position `after_queries_submitted` should be avoided for tests that have async operations pending. Alternative valid restart positions for this test would be:
- `after_backpressure_validation` (after all queries complete)
- `after_table_create` (before any queries start)
