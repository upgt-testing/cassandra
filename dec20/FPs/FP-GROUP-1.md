# False Positive Report: Group 1 - RejectedExecutionException in Netty Event Executor

## Summary
**Classification:** False Positive (FP)
**Priority:** MEDIUM
**Test Count:** Many executions

## Error Description
```
java.util.concurrent.RejectedExecutionException: event executor terminated
at io.netty.util.concurrent.SingleThreadEventExecutor.reject(SingleThreadEventExecutor.java:934)
at io.netty.util.concurrent.SingleThreadEventExecutor.offerTask(SingleThreadEventExecutor.java:353)
...
at org.apache.cassandra.net.OutboundConnection.setDisconnected(OutboundConnection.java:1406)
at org.apache.cassandra.net.OutboundConnection.<init>(OutboundConnection.java:319)
...
at org.apache.cassandra.gms.Gossiper.stop(Gossiper.java:2261)
at org.apache.cassandra.gms.Gossiper.stopShutdownAndWait(Gossiper.java:2551)
at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$20(Instance.java:883)
```

## Root Cause Analysis

### The Graceful Restart Sequence

The restart adapter implements graceful restart with the following sequence:

```java
// CassandraClusterAdapter.performGracefulRestart()
// Step 1: Drain the node
NodeToolResult drainResult = instance.nodetoolResult("drain");

// Step 2: Shutdown the node
Future<Void> shutdownFuture = instance.shutdown(true);
```

### During `nodetool drain` (StorageService.drain()):

```java
// StorageService.java:5847
Gossiper.instance.stop();  // First stop - sends GOSSIP_SHUTDOWN messages

// StorageService.java:5858
MessagingService.instance().shutdown();  // TERMINATES NETTY EVENT LOOPS
```

### During `instance.shutdown(true)` (Instance.shutdown()):

```java
// Instance.java:883
Gossiper.instance.stopShutdownAndWait(1L, MINUTES);  // Tries to stop AGAIN!
```

### The Problem

1. **First call**: During drain, `Gossiper.stop()` sends GOSSIP_SHUTDOWN messages successfully
2. **MessagingService shutdown**: Terminates all Netty event executors
3. **Second call**: During Instance.shutdown(), `Gossiper.stop()` tries to send GOSSIP_SHUTDOWN again
4. **Failure**: MessagingService tries to create new OutboundConnection, which requires an active event loop
5. **Exception**: `RejectedExecutionException: event executor terminated`

### Code Flow Leading to Exception

```java
Gossiper.stop() {
    // Line 2260-2261
    for (InetAddressAndPort ep : liveEndpoints)
        MessagingService.instance().send(message, ep);  // Tries to send GOSSIP_SHUTDOWN
}

MessagingService.doSend()
    -> getOutbound()
    -> OutboundConnections.tryRegister()
    -> new OutboundConnection() {
        this.eventLoop = template.socketFactory.defaultGroup().next();
        setDisconnected();  // Calls eventLoop.scheduleAtFixedRate() - FAILS!
    }
```

## Why This Is a False Positive

### 1. Production Behavior Difference

In production Cassandra:
- `nodetool drain` is run
- MessagingService is shut down
- **Process terminates** (exit, SIGTERM, etc.)
- No further shutdown logic runs

In the distributed test framework:
- `nodetool drain` is run
- MessagingService is shut down
- `Instance.shutdown()` continues running additional shutdown logic
- This causes the conflict

### 2. Test Framework Design Mismatch

The `Instance.shutdown()` method is designed for the case where the node has NOT been drained:
- It assumes all services are still running
- It tries to gracefully stop Gossiper, which needs MessagingService
- But after drain, MessagingService is already terminated

### 3. Not a Bug in Cassandra Production Code

- This exception can NEVER occur in production
- The failure is caused by the interaction between:
  - The restart adapter (correctly simulates production graceful shutdown)
  - The distributed test framework (doesn't expect drain to have happened first)

## Affected Tests

Example test executions:
1. `GuardrailPartitionSizeTest_RestartInjected.testPartitionSize`
   - position: `after_yaml_config_test`
   - mode: `GRACEFUL`
   - Many positions trigger this same issue

## Potential Improvements (Not Bugs)

While this is not a bug, the code could be made more defensive:

1. **In Gossiper.stop()**: Check if MessagingService is still running before trying to send:
```java
public void stop() {
    if (!MessagingService.instance().isListening()) {
        logger.info("MessagingService already shut down, skipping GOSSIP_SHUTDOWN");
        return;
    }
    // ... rest of the method
}
```

2. **In Instance.shutdown()**: Skip Gossiper shutdown if drain was already called

3. **In Restart Adapter**: Don't call drain before Instance.shutdown() since the test framework handles it differently than production

## Resolution

**Fixed in restart adapter** by removing the `nodetool drain` call before `instance.shutdown(true)`.

The distributed test framework's `shutdown(true)` method already handles graceful cleanup internally, so calling drain first was redundant and caused conflicts.

**Change made to `CassandraClusterAdapter.performGracefulRestart()`:**
```java
// Before (caused RejectedExecutionException):
instance.nodetoolResult("drain");  // Shuts down MessagingService
instance.shutdown(true);            // Tries to use MessagingService again - FAILS

// After (works correctly):
instance.shutdown(true);            // Handles all graceful cleanup internally
```

## Conclusion

This was a **False Positive** caused by the mismatch between:
- The restart adapter's production-like graceful shutdown sequence (drain + shutdown)
- The distributed test framework's assumption that services haven't been drained

The failure cannot occur in production Cassandra and was specific to the restart testing framework's interaction with the distributed test infrastructure. The issue has been resolved by updating the restart adapter to use the test framework's built-in graceful shutdown mechanism.
