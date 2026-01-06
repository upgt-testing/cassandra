# TEST-BUG Report: Group 5 - NullPointerException in GCInspector

## Summary
The `MapMBeanWrapper.queryNames()` method in `TableMetricTest` returns `null` instead of an empty `Set`, violating the `MBeanWrapper` interface contract. This causes a `NullPointerException` when `GCInspector` is instantiated during nodetool operations.

## Bug Classification
**Type:** TEST-BUG
**Priority:** HIGH
**Affected Tests:** TableMetricTest (systemTables, userTables)

## Root Cause

### Buggy Code Location
**File:** `test/distributed/org/apache/cassandra/distributed/test/metric/TableMetricTest.java`
**Lines:** 277-281

```java
@Override
public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
{
    return null;  // BUG: Should return Collections.emptySet(), not null
}
```

### Code Path to Failure

1. **Test Setup:** `TableMetricTest` sets up a custom `MapMBeanWrapper` to mock MBean registration:
   ```java
   static {
       MBEAN_REGISTRATION_CLASS.setString(MapMBeanWrapper.class.getName());
       ORG_APACHE_CASSANDRA_DISABLE_MBEAN_REGISTRATION.setBoolean(false);
   }
   ```

2. **Restart Trigger:** During graceful restart, the adapter calls:
   ```java
   // CassandraClusterAdapter.java:158
   NodeToolResult drainResult = instance.nodetoolResult("drain");
   ```

3. **NodeProbe Creation:** The `nodetoolResult` creates a new `InternalNodeProbe`:
   ```java
   // InternalNodeProbe.java:79
   gcProxy = new GCInspector();
   ```

4. **GCInspector Constructor:** The `GCInspector` constructor queries MBeans:
   ```java
   // GCInspector.java:144-146
   ObjectName gcName = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
   for (ObjectName name : MBeanWrapper.instance.queryNames(gcName, null))  // NPE HERE
   ```

5. **Null Returned:** `MapMBeanWrapper.queryNames()` returns `null`, causing the for-each loop to throw NPE.

### Why It Works Without Restart

In normal test execution, no nodetool commands are executed, so `GCInspector` is never instantiated, and `queryNames()` is never called. The bug remains latent.

## Stack Trace

```
java.lang.NullPointerException: Cannot invoke "java.util.Set.iterator()" because the return value of "org.apache.cassandra.utils.MBeanWrapper.queryNames(javax.management.ObjectName, javax.management.QueryExp)" is null
    at org.apache.cassandra.service.GCInspector.<init>(GCInspector.java:145)
    at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.connect(InternalNodeProbe.java:79)
    at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.<init>(InternalNodeProbe.java:58)
    at org.apache.cassandra.distributed.impl.Instance$DTestNodeTool.<init>(Instance.java:1079)
    at org.apache.cassandra.distributed.impl.Instance.lambda$nodetoolResult$51(Instance.java:986)
```

## Proposed Fix

Change `MapMBeanWrapper.queryNames()` to return an empty set instead of null:

```java
@Override
public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
{
    return Collections.emptySet();  // FIX: Return empty set, not null
}
```

This matches the behavior of `NoOpMBeanWrapper` which properly returns `Collections.emptySet()` (see `MBeanWrapper.java:169`).

## Patch

A patch file is available at: `dec20/patches/TEST-BUG-GROUP-5.patch`

To apply the patch:
```bash
cd /path/to/cassandra
git apply dec20/patches/TEST-BUG-GROUP-5.patch
```

## Related Files

- `src/java/org/apache/cassandra/service/GCInspector.java:145` - Where NPE is thrown
- `src/java/org/apache/cassandra/utils/MBeanWrapper.java:169` - `NoOpMBeanWrapper.queryNames()` returns `Collections.emptySet()` (correct behavior)
- `test/distributed/org/apache/cassandra/distributed/test/metric/TableMetricTest.java:277-281` - Buggy implementation
- `test/distributed/org/apache/cassandra/distributed/mock/nodetool/InternalNodeProbe.java:79` - Where `GCInspector` is created

## Test Executions (9 total)

1. `TableMetricTest_RestartInjected.systemTables` - position: "after_load_system_tables", mode: GRACEFUL
2. `TableMetricTest_RestartInjected.userTables` - position: "after_load_system_tables", mode: GRACEFUL
3. `TableMetricTest_RestartInjected.userTables` - position: "after_table_create", mode: GRACEFUL
4. And 6 more test executions...

## Impact

This bug only manifests when:
1. The test uses `MapMBeanWrapper` (via `MBEAN_REGISTRATION_CLASS` property)
2. A nodetool command is executed (which creates `InternalNodeProbe` and `GCInspector`)

The bug is isolated to test code and does not affect production Cassandra code.
