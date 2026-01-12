# FP-GROUP-17: RuntimeException in StorageService.prepareForReplacement

## Summary

**Verdict**: FALSE POSITIVE (FP)

**Exception**: `java.lang.RuntimeException: Cannot replace address with a node that is already bootstrapped`

**Root Cause**: Shared JVM system properties in in-JVM test environment cause restart of unrelated node to incorrectly inherit `REPLACE_ADDRESS` property.

## Failure Details

**Test**: `MigrationCoordinatorTest_RestartInjected.replaceNode`
**Restart Position**: `after_bootstrap`
**Restart Target**: Node 1 (index=0)
**Restart Mode**: GRACEFUL

### Stack Trace

```
org.restarttest.core.RestartException: Restart failed at position after_bootstrap
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:177)
Caused by: java.lang.RuntimeException: Cannot replace address with a node that is already bootstrapped
	at org.apache.cassandra.service.StorageService.prepareForReplacement(StorageService.java:734)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1159)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
```

## Analysis

### Test Scenario

The `replaceNode()` test does the following:
1. Creates a 2-node cluster (nodes 1 and 2)
2. Shuts down node 2
3. Sets `REPLACE_ADDRESS` system property to node 2's address
4. Bootstraps node 3 as a replacement for node 2
5. **Restart framework injects restart of node 1 at position `after_bootstrap`**

### Root Cause

The issue is in the in-JVM distributed test framework's shared process model:

**Step 1**: At line 106 of the test:
```java
withProperties.set(REPLACE_ADDRESS, replacementAddress.getHostAddress());
```
This sets a JVM-wide system property to configure node 3 to bootstrap as a replacement.

**Step 2**: The restart framework restarts node 1 at position `after_bootstrap`.

**Step 3**: When node 1 restarts via `instance.startup()`, it reads the `REPLACE_ADDRESS` system property:
```java
// StorageService.java:1159
if (DatabaseDescriptor.getReplaceAddress() != null)
    replacementId = prepareForReplacement();
```

**Step 4**: `prepareForReplacement()` checks if bootstrap has been completed:
```java
// StorageService.java:733-734
if (SystemKeyspace.bootstrapComplete())
    throw new RuntimeException("Cannot replace address with a node that is already bootstrapped");
```

Since node 1 was the original node and already completed bootstrap, this check correctly fails.

### Why This Is a False Positive

1. **Production Environment Isolation**: In a real production deployment, each Cassandra node runs in its own separate JVM process. System properties like `REPLACE_ADDRESS` are only set on the specific node that is performing the replacement, not on other nodes.

2. **In-JVM Test Framework Limitation**: The in-JVM distributed test framework (used by Cassandra for faster testing) runs all nodes in a single JVM process. This means all system properties are shared across all nodes - there is no way to set a property for only one node.

3. **Invalid Restart Scenario**: Restarting node 1 while the `REPLACE_ADDRESS` property is set creates an invalid configuration that would never occur in production. Node 1 was never configured to replace any node.

4. **Source Code Is Correct**: The check at `StorageService.java:733-734` is correct defensive behavior. It prevents an already-bootstrapped node from attempting to replace another node, which would be a serious operational error.

### Source Code Reference

```java
// StorageService.java:731-734
private synchronized UUID prepareForReplacement() throws ConfigurationException
{
    if (SystemKeyspace.bootstrapComplete())
        throw new RuntimeException("Cannot replace address with a node that is already bootstrapped");
    // ... rest of replacement logic
}
```

This code is correct. It prevents data inconsistency by ensuring a replacement node hasn't already been bootstrapped with its own data.

## Conclusion

This failure is a **False Positive** caused by:
1. The test framework's shared JVM process model where all nodes inherit the same system properties
2. The restart position `after_bootstrap` occurring when the `REPLACE_ADDRESS` property is globally set
3. The restart framework's inability to isolate per-node JVM properties in the in-JVM test environment

The Cassandra source code is behaving correctly - this is a limitation of the restart testing framework when testing node replacement scenarios in the in-JVM test environment.

## Recommendation

The restart framework should either:
1. Skip restart injection at positions where node-specific system properties (like `REPLACE_ADDRESS`) are set
2. Clear conflicting system properties before restarting nodes that are not the intended target of those properties
3. Add filtering logic to avoid restarting non-replacement nodes during replacement test scenarios
