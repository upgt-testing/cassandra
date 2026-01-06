# FP-GROUP-2: NoHostAvailableException - Transport unavailable

## Classification: FALSE POSITIVE

## Summary
This failure group is a False Positive because the restart is injected at a semantically inappropriate position where the test is specifically verifying transport-disabled behavior, and the restart invalidates the test's preconditions.

## Test Information
- **Test**: `DisableBinaryTest_RestartInjected.testDisallowsNewRequests`
- **Restart Position**: `after_transport_stop`
- **Restart Mode**: GRACEFUL
- **Target**: node (index 0)

## Error Details
```
com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
    at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:85)
    ...
    at org.apache.cassandra.distributed.test.DisableBinaryTest_RestartInjected.testDisallowsNewRequests(DisableBinaryTest_RestartInjected.java:193)
```

## Test Purpose Analysis

The `testDisallowsNewRequests` test is specifically designed to verify that:
1. When binary transport is disabled via `nodetool disablebinary`
2. New queries should be rejected with `OverloadedException`

The test flow is:
1. Start 100 blocking queries using ByteBuddy-injected blocking code
2. Call `nodetool disablebinary` to disable the transport
3. Wait until transport is stopped
4. **Restart point injected here: `after_transport_stop`**
5. Try to execute queries, expecting `OverloadedException` because transport is disabled

## Why This Is a False Positive

### 1. Restart Invalidates Test Preconditions

The test's assertion at line 193-196 expects an `OverloadedException`:
```java
try
{
    session.execute("select * from tbl").one();
    fail("Should have thrown OverloadedException");
}
catch (OverloadedException e) {}
```

This assertion is based on the precondition that the transport is disabled. However, after restart:
- The node shuts down
- The node restarts with transport **re-enabled** (normal startup behavior)
- The transport-disabled state is lost

### 2. Client Session State Issue (Not a Cassandra Bug)

The `NoHostAvailableException` occurs because:
1. During restart, the datastax driver session loses connection
2. The driver's reconnection logic schedules a retry in 16000ms
3. The test immediately tries to execute a query before reconnection completes

Log evidence:
```
ERROR [cluster1-reconnection-0] 2026-01-06 01:58:25,199 ControlConnection.java:186 - [Control connection] Cannot connect to any host, scheduling retry in 16000 milliseconds
```

This is expected client-side behavior, not a Cassandra server bug.

### 3. Semantically Inappropriate Restart Position

The position `after_transport_stop` is right at a critical point where:
- The test is verifying specific behavior of the disabled transport
- The test expects transport to remain disabled
- A restart changes this fundamental assumption

A restart at this position doesn't test any meaningful restart scenario because:
- The transport-disabled state is a temporary operational state, not persistent configuration
- Restarting a node after disabling transport will always re-enable the transport
- The test's assertions become invalid after restart

## Evidence from Test Code

From `DisableBinaryTest_RestartInjected.java:178-196`:
```java
control.get(1).runOnInstance(() -> {
    while(CassandraDaemon.getInstanceForTesting().nativeTransportService().isRunning()) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
    }
});
RestartFramework.at("after_transport_stop")  // <-- Restart here
    .on(control)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

Future<?> afterShutdown = CompletableFuture.supplyAsync(() -> session.execute("select * from tbl").one());
try
{
    session.execute("select * from tbl").one();
    fail("Should have thrown OverloadedException");  // Test expects transport to be disabled
}
catch (OverloadedException e) {}
```

## Conclusion

This failure is a **False Positive** because:
1. The restart position is at a point where the test is verifying specific transport-disabled behavior
2. Restarting the node re-enables the transport, invalidating the test's preconditions
3. The `NoHostAvailableException` is due to client-side session state during reconnection, not a Cassandra bug
4. This is not a meaningful restart scenario - it's testing an operational state that is intentionally non-persistent

## All Test Executions in This Group (20 total)

All 20 test executions in this group share the same root cause - they all involve tests where:
- The test disables binary transport or puts the client in a specific connection state
- A restart at that position invalidates the test's expected state
- The client session fails to reconnect in time

This is inherent to the test design and the restart position, not a bug in Cassandra.
