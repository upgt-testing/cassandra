# False Positive Report: Group 12 - TransportException Connection Closed

## Classification: FALSE POSITIVE (FP)

## Summary
The `TransportException: Connection has been closed` failure is a false positive caused by injecting a node restart while async queries are still in-flight. The DataStax Java driver correctly throws this exception when connections are forcibly closed during node shutdown.

## Failure Details

**Exception:**
```
com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
```

**Test Executions:**
1. `OverloadTest_RestartInjected.applyClientBackpressure`
   - Position: `after_queries_submitted`
   - Mode: GRACEFUL
   - Index: 0

2. `OverloadTest_RestartInjected.clientBackpressureDisabled`
   - Position: `after_queries_submitted`
   - Mode: GRACEFUL
   - Index: 0

## Root Cause Analysis

### Test Behavior

The test `testApplyClientBackpressure` is designed to test Cassandra's client backpressure mechanism:

1. **Query Submission Phase** (lines 130-138):
   ```java
   List<Future<?>> futures = new ArrayList<>();
   int count = 128;
   for (int i = 0; i < count; i++)
       futures.add(CompletableFuture.supplyAsync(() -> session.execute("select * from tbl").one(), executor));
   Thread.sleep(700);
   for (int i = 0; i < count; i++)
       futures.add(CompletableFuture.supplyAsync(() -> session.execute("select * from tbl").one(), executor));
   ```

   This submits 256 async queries to the Cassandra node.

2. **Restart Injection Point** (lines 140-145):
   ```java
   RestartFramework.at("after_queries_submitted")
       .on(control)
       .restart("node")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

   The restart is triggered BEFORE the futures are processed, while 256 queries are still in-flight.

3. **Exception Handling** (lines 157-166):
   ```java
   catch (ExecutionException e)
   {
       if (e.getCause() instanceof OperationTimedOutException)
           timedOut++;
       else if (e.getCause() instanceof ReadTimeoutException)
           timedOut++;
       else if (e.getCause() instanceof OverloadedException)
           overloaded++;
       else throw e;  // TransportException is NOT handled - it gets rethrown!
   }
   ```

   The test only handles `OperationTimedOutException`, `ReadTimeoutException`, and `OverloadedException`. Any other exception type (including `TransportException`) is rethrown, causing the test to fail.

### Why This is a False Positive

1. **Expected Driver Behavior**: When a Cassandra node is restarted, the DataStax driver detects the host going down via `Cluster$Manager.onDown()`. This triggers `SessionManager.onDown()` which forcibly closes all connections by calling `Connection$ConnectionCloseFuture.force()`. This is correct and expected driver behavior.

2. **Test Not Designed for Restarts**: The original test was designed to validate backpressure behavior under load, not to handle unexpected node restarts. The error handling only accounts for normal timeout and overload scenarios.

3. **In-Flight Query Failure**: When the restart is injected at `after_queries_submitted`:
   - 256 async queries are pending execution
   - The node shuts down, closing all connections
   - The driver throws `TransportException` for queries that were waiting for responses
   - The test fails because it doesn't handle this exception type

4. **No Cassandra Code Bug**: The `TransportException` is thrown entirely by the DataStax driver (client-side code), not by Cassandra server code. The exception correctly indicates that the connection was closed, which happened because of the injected restart.

## Stack Trace Analysis

```
java.util.concurrent.ExecutionException: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
    at java.base/java.util.concurrent.CompletableFuture.reportGet(CompletableFuture.java:396)
    at java.base/java.util.concurrent.CompletableFuture.get(CompletableFuture.java:2073)
    at OverloadTest_RestartInjected.testApplyClientBackpressure(OverloadTest_RestartInjected.java:154)
...
Caused by: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
    at com.datastax.driver.core.exceptions.TransportException.copy(TransportException.java:40)
    at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java:1460)
    at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java:1441)
    at com.datastax.driver.core.CloseFuture$Forwarding.force(CloseFuture.java:90)
    at com.datastax.driver.core.SessionManager.onDown(SessionManager.java:526)
    at com.datastax.driver.core.Cluster$Manager.onDown(Cluster.java:2179)
    ...
```

The call chain shows:
1. `Cluster$Manager.onDown()` - Driver detects host is down
2. `SessionManager.onDown()` - Session manager handles the down event
3. `Connection$ConnectionCloseFuture.force()` - Forcibly closes connections
4. `TransportException` thrown for in-flight queries

## Relationship to Group 1 (NoHostAvailableException)

This failure is closely related to Group 1 (NoHostAvailableException). Both are DataStax driver exceptions thrown when a node becomes unavailable:

| Exception | When Thrown |
|-----------|-------------|
| `NoHostAvailableException` | When trying to send a NEW query and no hosts are available |
| `TransportException` | When a query is IN-FLIGHT and the connection is forcibly closed |

Both are expected driver behaviors during node restart, not Cassandra bugs.

## Conclusion

This failure is a **False Positive** because:
- The exception is caused by the injected restart, not by a Cassandra bug
- Closing connections during node restart is expected and correct behavior
- The test's error handling was not designed to accommodate restart scenarios
- The restart position (`after_queries_submitted`) is inappropriate for this test as it has in-flight queries
- `TransportException` is correct driver behavior when connections are closed

## Recommendation

The restart position `after_queries_submitted` should be avoided for tests that have async operations pending. Valid restart positions for this test would be:
- `after_backpressure_validation` (after all queries complete and results are validated)
- `after_table_create` (before any queries start)
- `after_initial_query` (after single warmup query but before bulk queries)
