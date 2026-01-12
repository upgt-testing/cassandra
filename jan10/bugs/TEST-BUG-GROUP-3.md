# TEST-BUG-GROUP-3: NullPointerException in GCInspector due to MapMBeanWrapper.queryNames() returning null

## Summary

The test class `TableMetricTest_RestartInjected` defines a custom `MapMBeanWrapper` implementation that returns `null` from its `queryNames()` method. This violates the implicit contract expected by callers like `GCInspector`, which assume a non-null `Set` is returned.

## Exception Details

```
java.lang.NullPointerException: Cannot invoke "java.util.Set.iterator()" because the return value of "org.apache.cassandra.utils.MBeanWrapper.queryNames(javax.management.ObjectName, javax.management.QueryExp)" is null
    at org.apache.cassandra.service.GCInspector.<init>(GCInspector.java:145)
    at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.connect(InternalNodeProbe.java:79)
    at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.<init>(InternalNodeProbe.java:58)
    at org.apache.cassandra.distributed.impl.Instance$DTestNodeTool.<init>(Instance.java:1079)
    at org.apache.cassandra.distributed.impl.Instance.lambda$nodetoolResult$51(Instance.java:986)
```

## Root Cause Analysis

### Buggy Code Location

**File:** `test/distributed/org/apache/cassandra/distributed/test/metric/TableMetricTest_RestartInjected.java`

**Lines 344-347:**
```java
@Override
public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
{
    return null;  // BUG: returns null instead of empty set
}
```

### Why This Is a Bug

1. **Contract Violation:** The `MBeanWrapper.queryNames()` method has an implicit contract that it returns a valid `Set<ObjectName>`, not `null`. All standard implementations in `MBeanWrapper.java` follow this contract:

   - `NoOpMBeanWrapper.queryNames()` returns `Collections.emptySet()` (line 169)
   - `PlatformMBeanWrapper.queryNames()` returns `mbs.queryNames(name, query)` - standard MBeanServer never returns null
   - `InstanceMBeanWrapper.queryNames()` returns `mbs.queryNames(name, query)` - standard MBeanServer never returns null

2. **Caller Assumption:** `GCInspector` at line 145 uses an enhanced for-loop which implicitly calls `.iterator()` on the result:
   ```java
   for (ObjectName name : MBeanWrapper.instance.queryNames(gcName, null))
   ```

3. **When It Triggers:** The bug manifests when `InternalNodeProbe.connect()` is called (which creates a new `GCInspector`), typically during nodetool operations after restart.

### Call Chain

1. After restart, nodetool operation is invoked
2. `Instance.nodetoolResult()` creates `DTestNodeTool`
3. `DTestNodeTool` extends `InternalNodeProbe`
4. `InternalNodeProbe.connect()` creates `new GCInspector()`
5. `GCInspector.<init>()` calls `MBeanWrapper.instance.queryNames()`
6. `DelegatingMbeanWrapper` delegates to `MapMBeanWrapper`
7. `MapMBeanWrapper.queryNames()` returns `null`
8. NPE on iterator

## Proposed Fix

**File:** `test/distributed/org/apache/cassandra/distributed/test/metric/TableMetricTest_RestartInjected.java`

Change:
```java
@Override
public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
{
    return null;
}
```

To:
```java
@Override
public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
{
    return Collections.emptySet();
}
```

Or implement proper query matching if needed:
```java
@Override
public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
{
    if (name == null)
        return new HashSet<>(map.keySet());
    return map.keySet().stream()
        .filter(on -> name.apply(on))
        .collect(Collectors.toSet());
}
```

## Affected Tests

All 9 test executions in Group 3 are affected. Examples:

1. `TableMetricTest_RestartInjected.systemTables` at position `after_load_system_tables`
2. `TableMetricTest_RestartInjected.userTables` at various positions

## Classification

**TEST-BUG** - The bug is in the test code, not in the Cassandra source code. The test-specific `MapMBeanWrapper` does not properly implement the `MBeanWrapper` interface contract.

## Impact

The test fails whenever any operation triggers `GCInspector` instantiation through the test's custom `MapMBeanWrapper`. This includes nodetool operations after restart.
