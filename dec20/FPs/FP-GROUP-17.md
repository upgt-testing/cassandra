# FP-GROUP-17: RuntimeException - Cannot replace bootstrapped node

## Classification: FALSE POSITIVE (FP)

## Summary

This failure is a false positive caused by restarting a node (node 1) while a global JVM system property (`REPLACE_ADDRESS`) is still set from a different node's replacement operation. The error "Cannot replace address with a node that is already bootstrapped" is Cassandra correctly preventing an improper replacement attempt, not a bug.

## Error Details

```
java.lang.RuntimeException: Cannot replace address with a node that is already bootstrapped
    at org.apache.cassandra.service.StorageService.prepareForReplacement(StorageService.java:734)
    at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1159)
    at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
```

## Test Information

- **Test Class**: `MigrationCoordinatorTest_RestartInjected`
- **Test Method**: `replaceNode`
- **Restart Position**: `after_bootstrap`
- **Restart Target**: `node`
- **Restart Index**: `0` (node 1)
- **Restart Mode**: `GRACEFUL`

## Root Cause Analysis

### Test Scenario

The test performs the following sequence:
1. Starts a 2-node cluster (node 1 and node 2)
2. Creates a keyspace
3. Shuts down node 2
4. Creates a table
5. Sets `REPLACE_ADDRESS` to node 2's address (via `withProperties.set(REPLACE_ADDRESS, ...)`)
6. Bootstraps a new node 3 to replace node 2
7. **Restart Point**: After bootstrap completes, the restart framework tries to restart node 1

### Why the Failure Occurs

1. **Global Property Pollution**: The test sets `REPLACE_ADDRESS` as a JVM system property using `withProperties.set(REPLACE_ADDRESS, replacementAddress.getHostAddress())`. This is a **global** property that affects all nodes in the JVM.

2. **Property Persists After Bootstrap**: After node 3 successfully replaces node 2, the `REPLACE_ADDRESS` property remains set in the JVM.

3. **Wrong Node Affected**: When the restart framework restarts node 1 (index 0), this node reads the `REPLACE_ADDRESS` property via `DatabaseDescriptor.getReplaceAddress()`.

4. **Incorrect Replacement Attempt**: Node 1 sees `REPLACE_ADDRESS` is set and attempts to perform a replacement operation, even though:
   - Node 1 was never intended to be a replacement node
   - The property was set for node 3's bootstrap operation
   - Node 1 is already fully bootstrapped

5. **Correct Validation Fails**: Cassandra correctly validates that an already-bootstrapped node should not attempt replacement and throws the exception at `StorageService.java:734`:
   ```java
   if (SystemKeyspace.bootstrapComplete())
       throw new RuntimeException("Cannot replace address with a node that is already bootstrapped");
   ```

### Code Flow

```
Node 1 restart
    -> StorageService.initServer()
    -> StorageService.prepareToJoin()
    -> DatabaseDescriptor.getReplaceAddress() returns non-null (REPLACE_ADDRESS is set)
    -> StorageService.prepareForReplacement()
    -> SystemKeyspace.bootstrapComplete() returns true (node 1 is already bootstrapped)
    -> RuntimeException: "Cannot replace address with a node that is already bootstrapped"
```

## Why This is NOT a Bug

### Cassandra Source Code is Correct

The `prepareForReplacement()` method correctly validates that:
- A node cannot attempt replacement if it's already bootstrapped
- This is a safety check to prevent data corruption/loss

### Test Code is Correct (Without Restart Injection)

The original test `MigrationCoordinatorTest.replaceNode` works correctly:
- `REPLACE_ADDRESS` is set for the specific bootstrap operation
- Only the new node (node 3) bootstraps with this property
- The property is cleaned up when `withProperties` is closed

### The Restart Position is Improper

The restart point `after_bootstrap` triggers while:
1. `REPLACE_ADDRESS` is still globally set
2. No node should be restarted in this state except the replacement node itself
3. Restarting any other existing node (node 1 or the replaced node 2) would incorrectly trigger replacement logic

## Environmental State at Failure Point

| Property | Value |
|----------|-------|
| REPLACE_ADDRESS | Set to node 2's address (127.0.0.2) |
| Node 1 status | Already bootstrapped |
| Node 2 status | Shut down (was replaced) |
| Node 3 status | Successfully bootstrapped as replacement |

## Conclusion

This is a **False Positive** because:

1. **Not a Cassandra bug**: The Cassandra source code correctly prevents an already-bootstrapped node from attempting replacement.

2. **Not a test bug**: The test functions correctly without restart injection.

3. **Improper restart position**: The restart framework triggers at a point where global JVM properties are in a state specific to a different operation (node 3's replacement bootstrap).

4. **Environmental pollution**: The `REPLACE_ADDRESS` property is meant for a specific node's bootstrap, but the restart framework applies it to a different node.

The restart at this position is not meaningful for testing Cassandra's correctness - it creates an artificial scenario that would never occur in production (an already-running node suddenly trying to replace another node when it wasn't started with that intention).
