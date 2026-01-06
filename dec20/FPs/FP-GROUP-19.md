# FP-GROUP-19: IllegalStateException - Instance class loader closed

## Classification: FALSE POSITIVE

## Summary
This failure occurs when the restart framework injects a restart at a position where test code holds a reference to an object (`LogAction`) that was created by the old class loader. After restart, the old class loader is closed, and attempting to use the stale object fails.

## Root Cause
```
java.lang.IllegalStateException: Can't load org.apache.cassandra.distributed.impl.FileLogAction$FileLineIterator. Instance class loader is already closed.
at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClassInternal(InstanceClassLoader.java:118)
at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClass(InstanceClassLoader.java:112)
at org.apache.cassandra.distributed.impl.FileLogAction.match(FileLogAction.java:58)
at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java:65)
at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java:137)
at org.apache.cassandra.distributed.test.JVMDTestTest_RestartInjected.instanceLogs(JVMDTestTest_RestartInjected.java:111)
```

## Affected Test
- `JVMDTestTest_RestartInjected.instanceLogs`
- Restart Position: `after_exception_trigger`
- Target: `node`
- Mode: `GRACEFUL`
- Index: `1` (node 2)

## Detailed Analysis

### Original Test Flow (without restart)
```java
LogAction logs = cluster.get(2).logs();  // Get logs object from node 2
long mark = logs.mark();                  // Get current position
cluster.get(2).runOnInstance(() -> {      // Trigger exception
    JVMStabilityInspector.uncaughtException(Thread.currentThread(),
        new RuntimeException("fail without fail"));
});
List<String> errors = logs.watchFor(mark, "^ERROR").getResult();  // Use logs object
```

The original test has NO restart between getting the `logs` object and using it.

### What Happens with Restart Injection

1. **Before Restart**:
   - Test calls `cluster.get(2).logs()` which returns a `FileLogAction` object
   - The `FileLogAction` is loaded by node 2's `InstanceClassLoader`
   - The `logs` variable holds a reference to this object

2. **Restart Injection**:
   - Restart occurs at position `after_exception_trigger`
   - Node 2 is shut down - its old `InstanceClassLoader` is closed
   - Node 2 is started - a **new** `InstanceClassLoader` is created
   - The old `logs` object still references the **closed** class loader

3. **After Restart**:
   - Test calls `logs.watchFor(mark, "^ERROR")`
   - This calls `FileLogAction.match()` at line 58
   - `match()` tries to create `new FileLineIterator(reader, fn)`
   - The JVM needs to load the `FileLineIterator` class
   - It uses the same class loader that loaded `FileLogAction` (the **old** closed one)
   - **Exception**: `IllegalStateException: Instance class loader is already closed`

### Why This is a False Positive

1. **Class Loader Isolation Design**: The JVM distributed test framework intentionally isolates each node using separate class loaders. This is core architecture, not a bug.

2. **Stale Reference Problem**: The `logs` object becomes stale after restart because:
   - It was created by code running in the old instance's class loader
   - Inner classes (`FileLineIterator`) must be loaded by the same class loader as the outer class
   - The old class loader is closed when the node restarts

3. **Not a Cassandra Bug**: This behavior is expected:
   - Objects created by an instance's class loader become invalid when that instance restarts
   - The restart framework cannot detect and update all such stale references
   - This is an inherent limitation of injecting restarts at arbitrary positions

4. **Original Test Works**: The original test (without restart injection) works correctly because it never restarts the node between getting and using the `logs` object.

## Conclusion

This failure is caused by the restart injection breaking the lifecycle assumption of the `LogAction` object. The object was designed to be used within a single instance lifecycle, not across restarts. The restart framework has no way to detect and refresh such objects.

**This is NOT a bug in Cassandra code.** It is a false positive caused by:
- Restart injection at a position where stale object references exist
- The inherent limitation of class loader isolation in the JVM distributed test framework
