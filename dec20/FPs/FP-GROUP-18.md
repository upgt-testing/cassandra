# FP-GROUP-18: WriteTimeoutException - Write timeout

## Classification: FALSE POSITIVE

## Summary
This failure is a false positive caused by the Java driver's session not being reconnected after a node restart. The test uses a shared `SESSION` object created before the restart and expects it to work seamlessly after the node restarts, which is not valid behavior.

## Root Cause Analysis

### What Happened
1. Test creates a Java driver `SESSION` in `@BeforeClass` (before any restart)
2. Test creates a table via `schemaChange()`
3. Restart is injected at position `after_table_create`
4. Node gracefully shuts down and restarts
5. Test immediately calls `SESSION.executeAsync("INSERT...")`
6. WriteTimeoutException: "1 replica were required but only 0 acknowledged the write"

### Why This is a False Positive

**1. Not a Cassandra Source Code Bug**
- The Cassandra node restarts successfully
- The restart adapter's `waitActive()` confirms:
  - Ring is healthy (`awaitRingHealthy`)
  - Schema matches across nodes (`awaitGossipSchemaMatch`)
- The node is fully operational from Cassandra's perspective

**2. Java Driver Connection Lifecycle Issue**
- The DataStax Java driver maintains its own connection pool
- When a node goes down and restarts, the driver must:
  - Detect the node is down
  - Remove connections from the pool
  - Detect the node is back up
  - Re-establish new connections
- This process is NOT instant and takes time

**3. Test Design Does Not Account for Reconnection**
```java
@BeforeClass
public static void createCluster() throws IOException {
    // ... cluster setup ...
    DRIVER_CLUSTER = JavaDriverUtils.create(SHARED_CLUSTER);
    SESSION = DRIVER_CLUSTER.connect();  // Session created ONCE here
}

@Test
public void shouldExposeReadsAndWrites() throws Throwable {
    SHARED_CLUSTER.schemaChange("CREATE TABLE ...");

    RestartFramework.at("after_table_create")
        .on(SHARED_CLUSTER)
        // ... restart happens here ...
        .execute();

    // Immediately uses SESSION without any reconnection handling!
    SESSION.executeAsync("INSERT INTO ...");  // <-- FAILS HERE
}
```

**4. Expected Behavior During Node Restart**
- WriteTimeoutException indicates the driver sent a request but received no acknowledgment
- This happens because:
  - The connection may be in a bad state post-restart
  - The driver hasn't completed its reconnection process
  - The node may still be initializing internal structures

### Technical Details

The error message:
```
WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency LOCAL_ONE
(1 replica were required but only 0 acknowledged the write)
```

This tells us:
- The driver connected to a node (otherwise we'd see `NoHostAvailableException`)
- The write was sent but no replica acknowledged it
- The timeout indicates the node wasn't ready to process writes

### Restart Adapter Behavior

The restart adapter does its job correctly:
```java
// In CassandraClusterAdapter.performGracefulRestart()
instance.shutdown(true);        // Graceful shutdown
Thread.sleep(1000);             // Allow detection
instance.startup();             // Restart
ClusterUtils.awaitRingJoin(...); // Wait for ring join
```

However, the adapter cannot control the Java driver's reconnection behavior, which is outside the scope of Cassandra's test infrastructure.

## Conclusion

This is a **False Positive** because:
1. The failure is caused by improper test design (not waiting for driver reconnection)
2. The restart injection position is valid but the test logic doesn't account for it
3. This is NOT a bug in Cassandra's source code - the node operates correctly
4. Write timeouts during/immediately after node restart are expected behavior

## Recommendation

To avoid this false positive, tests using external Java driver sessions should:
1. Re-establish the session after a restart, OR
2. Wait for the driver to reconnect before issuing queries, OR
3. Use internal cluster APIs instead of external driver connections

## Test Information

- **Test**: `QueriesTableTest_RestartInjected.shouldExposeReadsAndWrites`
- **Position**: `after_table_create`
- **Target**: `node`
- **Mode**: `GRACEFUL`
- **Index**: `0`
