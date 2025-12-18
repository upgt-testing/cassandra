# DEBUG_TRACKER - CASSANDRA

This tracker lists failure groups ordered by likelihood of being actual bugs.
Groups are prioritized from most likely to be bugs to most likely to be false positives.

Total failure groups: 27

---

## Group 1 (Priority 2)

**Status:** [x] **TEST-BUG** - Stale coordinator reference after node restart

**Execution Count:** 38

**Priority Reason:** Application exception - java.util.concurrent.RejectedExecutionException

### Generalized Stack Trace
```
java.util.concurrent.RejectedExecutionException
	at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.addTask(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$6(IsolatedExecutor.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithResult(Coordinator.java)
	at org.apache.cassandra.distributed.api.ICoordinator.execute(ICoordinator.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.RejectedExecutionException: isolatedExecutor has shut down
	at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java:49)
	at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java:833)
	at java.base/java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java:1365)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.addTask(ThreadPoolExecutorPlus.java:50)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:81)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:33)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$6(IsolatedExecutor.java:150)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java:151)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithResult(Coordinator.java:61)
	at org.apache.cassandra.distributed.api.ICoordinator.execute(ICoordinator.java:32)
	at org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.execute(GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.java:591)
	at org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.testCompositeClusteringKey(GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.java:575)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected`
- Test Method: `testCompositeClusteringKey`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `056-586512fa`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected`
- Test Method: `testCompositeClusteringKey`
- Position: `after_first_insert`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `057-30c72039`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected`
- Test Method: `testSetSizeFrozen`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `062-b50248ed`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**
The test code stores a static reference to a `ICoordinator` object in the `@BeforeClass` setup method:
```java
// Line 58 in GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.java
coordinator = cluster.coordinator(1);
```

After a node restart, this coordinator reference becomes stale because:

1. **During setup (line 50-59):**
   - `cluster.coordinator(1)` returns a Coordinator that wraps the Instance at index 0 (node 1)
   - This Coordinator holds a reference to the Instance object

2. **During restart (after_table_create position):**
   - RestartFramework restarts node at index 0 (which is node 1 in Cassandra's 1-based indexing)
   - The restart process:
     a. Calls `instance.shutdown(true)` - shuts down the Instance including its `isolatedExecutor`
     b. Calls `instance.startup()` on the Wrapper
     c. The Wrapper detects the delegate is shut down and creates a **NEW Instance object** (AbstractCluster.java:395-396)
     d. The new Instance has a new `isolatedExecutor`

3. **After restart (line 575):**
   - Test calls `execute("INSERT INTO %s...")` which uses the static `coordinator` reference
   - This Coordinator still references the OLD Instance whose `isolatedExecutor` was shut down
   - The new Instance is a different object and cannot be accessed via the old coordinator
   - Error: `RejectedExecutionException: isolatedExecutor has shut down`

**Buggy Code Location:**
- File: `test/distributed/org/apache/cassandra/distributed/test/guardrails/GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.java`
- Lines: 58 (static coordinator initialization) and 575+ (usage after restart)

**Why This is Not a Source Code Bug:**
- The restart framework and Cassandra distributed test infrastructure are working correctly
- Creating a new Instance during restart is the expected behavior (it ensures clean state)
- The test code incorrectly assumes the coordinator reference remains valid after restarting the node it points to

**Potential Fixes:**

Option 1: Use a different node as coordinator (one that won't be restarted):
```java
@BeforeClass
public static void setupCluster() throws IOException
{
    cluster = init(Cluster.build(NUM_NODES)
                          .withConfig(c -> c.set("items_per_collection_warn_threshold", WARN_THRESHOLD)
                                            .set("items_per_collection_fail_threshold", FAIL_THRESHOLD))
                          .start());
    cluster.disableAutoCompaction(KEYSPACE);
    coordinator = cluster.coordinator(2); // Use node 2 instead of node 1
}
```
Then restart node at index 1 instead of 0:
```java
RestartFramework.at("after_table_create")
    .on(cluster)
    .restart("node")
    .withIndex(1) // Restart node 2 instead of node 1
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

Option 2: Refresh the coordinator reference after each restart:
```java
RestartFramework.at("after_table_create")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
coordinator = cluster.coordinator(1); // Refresh the reference
```

Option 3: Use instance method to get fresh coordinator each time:
```java
private ICoordinator getCoordinator() {
    return cluster.coordinator(1);
}
```
And replace all `coordinator.execute()` calls with `getCoordinator().execute()`.

**Impact:** All 38 test executions in this group are affected by the same TEST-BUG pattern - tests that restart the coordinator node without refreshing the coordinator reference.

---

## Group 2 (Priority 2)

**Status:** [x] **FP** - Restart undoes disablebinary and causes driver reconnection timing issue

**Execution Count:** 18

**Priority Reason:** Application exception - Caused by

### Generalized Stack Trace
```
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException
	at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.access$1200(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.findNextHostAndQuery(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.startNewExecution(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.sendRequest(RequestHandler.java)
	at com.datastax.driver.core.SessionManager.execute(SessionManager.java)
	at com.datastax.driver.core.SessionManager.executeAsync(SessionManager.java)
```

### Raw Stack Trace Sample
```
com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
	at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:85)
	at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:39)
	at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
	at com.datastax.driver.core.DefaultResultSetFuture.getUninterruptibly(DefaultResultSetFuture.java:295)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:60)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:41)
	at org.apache.cassandra.distributed.test.DisableBinaryTest_RestartInjected.testDisallowsNewRequests(DisableBinaryTest_RestartInjected.java:193)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
	at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java:285)
	at com.datastax.driver.core.RequestHandler.access$1200(RequestHandler.java:63)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.findNextHostAndQuery(RequestHandler.java:377)
	at com.datastax.driver.core.RequestHandler.startNewExecution(RequestHandler.java:141)
	at com.datastax.driver.core.RequestHandler.sendRequest(RequestHandler.java:123)
	at com.datastax.driver.core.SessionManager.execute(SessionManager.java:707)
	at com.datastax.driver.core.SessionManager.executeAsync(SessionManager.java:144)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.DisableBinaryTest_RestartInjected`
- Test Method: `testDisallowsNewRequests`
- Position: `after_transport_stop`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `051-a478f20a`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.OverloadTest_RestartInjected`
- Test Method: `applyClientBackpressure`
- Position: `after_initial_query`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `030-535dae58`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.OverloadTest_RestartInjected`
- Test Method: `applyClientBackpressure`
- Position: `after_enable_slow_select`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `031-14c36077`

### Debug Analysis

**Reproduced:** Yes (Test 1: DisableBinaryTest_RestartInjected.testDisallowsNewRequests with after_transport_stop restart)

**Classification:** FP (False Positive)

**Root Cause:**

This is a false positive caused by the restart injection fundamentally changing the test's behavior. The restart at position `after_transport_stop` undoes the effect of the `disablebinary` command and creates a timing issue with driver reconnection.

**Detailed Analysis:**

1. **Original Test Intent (DisableBinaryTest.java:136-158):**
   - Line 144: Calls `nodetoolResult("disablebinary")` to stop the native transport service
   - Line 146-150: Waits for the native transport to actually stop
   - Line 155: Attempts to execute a query, expecting `OverloadedException` because transport is disabled

2. **Restart-Injected Test Behavior (DisableBinaryTest_RestartInjected.java:176-196):**
   - Line 176: Calls `nodetoolResult("disablebinary")` to stop the native transport service
   - Line 178-182: Waits for the native transport to actually stop
   - **Line 183-188: RESTART INJECTED at `after_transport_stop`** (NOT in original test)
   - Line 193: Attempts to execute a query, expecting `OverloadedException`

3. **Why the Restart Causes a False Positive:**

   The `disablebinary` command is a runtime command that **does NOT persist across restarts**. When the node restarts:

   a) **Native transport restarts automatically**: Test logs confirm:
      ```
      INFO [node1_isolatedExecutor:1] node1 2025-12-17 15:09:04,192 PipelineConfigurator.java:148 -
      Starting listening for CQL clients on /127.0.0.1:9042 (unencrypted)...
      ```
      The transport comes back up after restart, undoing the effect of `disablebinary`.

   b) **DataStax driver hasn't reconnected**:
      - The driver's connection was lost when the node shut down
      - The test immediately tries to execute a query at line 193 after restart completes
      - The driver hasn't had time to detect the node is back up and reconnect
      - Result: `NoHostAvailableException: All host(s) tried for query failed (no host was tried)`

   c) **CassandraClusterAdapter.waitActive() only waits for internal health**:
      - Lines 71-89 in `/home/shuai/xlab/restart_testing/cassandra/restart-adapter/src/main/java/org/apache/cassandra/restart/CassandraClusterAdapter.java`
      - Waits for ring health (gossip protocol) and schema agreement
      - Does NOT wait for native transport to be ready or client driver to reconnect
      - So the restart framework considers the restart "complete" before client connections can be established

4. **Why This is Not a Real Bug:**

   - **No Cassandra bug**: The native transport starts correctly after restart (as designed). The transport service is functioning properly.
   - **No test bug**: The original test works correctly without the restart. The test logic is sound.
   - **Inappropriate restart position**: The restart at `after_transport_stop` fundamentally changes what the test is verifying:
     - Original test verifies: "After disabling transport, new queries are rejected"
     - With restart: "After disabling transport then restarting (which re-enables transport), driver hasn't reconnected yet"
   - **Timing artifact**: If the test waited a few seconds after restart, the driver would reconnect and queries would succeed. But then the test would still fail because it expects `OverloadedException`, which won't be thrown because the transport is now running.

**Why This is a Strong FP:**

The restart position `after_transport_stop` creates an internally inconsistent test scenario:
- The test expects the transport to remain stopped (to get `OverloadedException`)
- But the restart brings the transport back up (undoing `disablebinary`)
- The resulting `NoHostAvailableException` is a side effect of driver reconnection timing, not a bug

The failure doesn't reveal any defect in Cassandra's handling of restarts or transport management. It simply shows that:
1. `disablebinary` doesn't persist (correct behavior - it's a runtime command)
2. Native transport starts on node restart (correct default behavior)
3. Client drivers need time to reconnect after node restarts (expected behavior)

**Relevant Code References:**
- Test file: `test/distributed/org/apache/cassandra/distributed/test/DisableBinaryTest_RestartInjected.java:183-196`
- Restart adapter: `restart-adapter/src/main/java/org/apache/cassandra/restart/CassandraClusterAdapter.java:71-89`
- Restart framework: `RestartTestingFramework/restart-core/src/main/java/org/restarttest/core/RestartExecutor.java:114-116`

**Impact:** All tests in this group that inject restarts at positions after transport/connection state changes likely suffer from similar driver reconnection timing issues.

---

## Group 3 (Priority 2)

**Status:** [x] **FP** - Restart loses HintsService pause state, disrupting test timing

**Execution Count:** 12

**Priority Reason:** Test bug - java.lang.NullPointerException thrown from test code

### Generalized Stack Trace
```
java.lang.NullPointerException
	at java.base/java.util.UUID.fromString(UUID.java)
```

### Raw Stack Trace Sample
```
java.lang.NullPointerException: Cannot invoke "String.length()" because "name" is null
	at java.base/java.util.UUID.fromString(UUID.java:237)
	at org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected.doHintReappearData(HintDataReappearingTest_RestartInjected.java:279)
	at org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected.demonstrateHintCausesDataReappearanceWriteTimeout(HintDataReappearingTest_RestartInjected.java:90)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected`
- Test Method: `demonstrateHintCausesDataReappearanceWriteTimeout`
- Position: `after_pause_hints`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `003-6ea8e9de`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected`
- Test Method: `demonstrateHintCausesDataReappearanceWriteTimeout`
- Position: `after_insert_and_filter_reset`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `004-c3a515f0`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected`
- Test Method: `demonstrateHintCausesDataReappearanceWriteTimeout`
- Position: `after_first_flush`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `005-d26ee023`

### Debug Analysis

**Reproduced:** Yes (Test 1: after_pause_hints restart)

**Classification:** FP (False Positive)

**Root Cause:**

The failure is caused by the restart injection disrupting the test's timing control over hints delivery, which is critical to demonstrating the "data reappearance" behavior.

**Test Intent (HintDataReappearingTest_RestartInjected.java:88-90):**
The test `demonstrateHintCausesDataReappearanceWriteTimeout` is designed to show a known issue where hints can resurrect deleted data after tombstones are garbage collected. The test requires precise timing:

1. **Line 141:** Pause hints delivery via `pauseHintsDelivery(node1)` - calls `HintsService.instance.pauseDispatch()` (AbstractHintWindowTest.java:61-66)
2. Insert data (with writes dropped to some nodes, creating hints on node1)
3. Delete data (creating tombstones)
4. Wait for gc_grace_seconds to expire
5. Compact to purge tombstones (data is now "gone")
6. **Line 246:** Transfer hints to trigger delivery
7. **Line 279:** Verify data reappeared: `Assert.assertFalse(UUID.fromString((String) objectArrays[0][1]).toString().isEmpty())`

**Why Restart Breaks the Test:**

1. **Line 142-147:** Restart at `after_pause_hints` happens immediately after pausing hints delivery
2. The `HintsService.pauseDispatch()` sets an **in-memory state** that does NOT persist across restarts
3. The RestartAdapter's `getStateCapture()` (CassandraClusterAdapter.java:98-111) returns a **no-op implementation**:
   ```java
   public ClusterState captureState(Cluster cluster) {
       return new DefaultClusterState(Collections.emptyMap());  // NO STATE CAPTURED
   }
   ```
4. When node1 restarts, it comes back with HintsService in default state (hints dispatch **ENABLED**)
5. Hints are delivered prematurely (test logs show delivery at 15:22:47, before tombstone compaction)
6. When hints arrive while tombstones are still active, the tombstones cancel out the hint mutations
7. Later, tombstones are compacted away
8. But hints are already delivered and gone, so data cannot be resurrected
9. **Line 279:** Final verification finds `objectArrays[0][1] = null` instead of expected UUID → NPE

**Evidence from Test Logs:**
```
15:21:51:481 - "Pausing hint delivery" (test code)
15:21:51:483 - "Paused hints dispatch" (node1, before restart)
15:21:51:483 - "=== ACTIVATING RESTART POINT: after_pause_hints ==="
15:22:15:618 - "=== RESTART POINT COMPLETED ==="
15:22:47:377 - "Finished hinted handoff to /127.0.0.2:7012" (PREMATURE DELIVERY)
15:22:47:385 - "Finished hinted handoff to /127.0.0.3:7012" (PREMATURE DELIVERY)
15:22:58:912 - "DEBUG: objectArrays[0][1] = null" (expected non-null UUID)
15:22:58:913 - NPE when calling UUID.fromString(null)
```

**Why This is a False Positive:**

1. This is NOT a bug in Cassandra's source code - hints delivery works correctly
2. This is NOT a bug in the test code - the test correctly demonstrates data reappearance without restart injection
3. The failure is an **artifact of restart injection** disrupting test-specific internal state management
4. The RestartAdapter **explicitly does not preserve application state** (by design)
5. The restart position `after_pause_hints` is particularly problematic as it happens immediately after setting critical in-memory state that is then immediately lost
6. The test relies on precise timing control that cannot be maintained when in-memory state is lost across restarts

---

## Group 5 (Priority 2)

**Status:** [x] **TEST-BUG** - MapMBeanWrapper.queryNames() returns null instead of empty set

**Execution Count:** 9

**Priority Reason:** Test bug - Caused by thrown from test code

### Generalized Stack Trace
```
Caused by: java.lang.NullPointerException
	at org.apache.cassandra.service.GCInspector.<init>(GCInspector.java)
	at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.connect(InternalNodeProbe.java)
	at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.<init>(InternalNodeProbe.java)
	at org.apache.cassandra.distributed.impl.Instance$DTestNodeTool.<init>(Instance.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$nodetoolResult$51(Instance.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_load_system_tables
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.metric.TableMetricTest_RestartInjected.systemTables(TableMetricTest_RestartInjected.java:83)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.NullPointerException: Cannot invoke "java.util.Set.iterator()" because the return value of "org.apache.cassandra.utils.MBeanWrapper.queryNames(javax.management.ObjectName, javax.management.QueryExp)" is null
	at org.apache.cassandra.service.GCInspector.<init>(GCInspector.java:145)
	at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.connect(InternalNodeProbe.java:79)
	at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.<init>(InternalNodeProbe.java:58)
	at org.apache.cassandra.distributed.impl.Instance$DTestNodeTool.<init>(Instance.java:1079)
	at org.apache.cassandra.distributed.impl.Instance.lambda$nodetoolResult$51(Instance.java:986)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.metric.TableMetricTest_RestartInjected`
- Test Method: `systemTables`
- Position: `after_load_system_tables`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `067-b1422257`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.metric.TableMetricTest_RestartInjected`
- Test Method: `userTables`
- Position: `after_load_system_tables`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `012-30bec6a1`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.metric.TableMetricTest_RestartInjected`
- Test Method: `userTables`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `013-334986c3`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**
The test uses a custom `MapMBeanWrapper` implementation that has an incomplete `queryNames()` method returning `null` instead of an empty set or properly filtered results.

**Failure Sequence:**

1. **Test Setup (TableMetricTest_RestartInjected.java:60-62):**
   - The test configures a custom MBeanWrapper: `MapMBeanWrapper`
   - This is a test-only implementation that stores MBeans in a ConcurrentHashMap

2. **The Buggy Code (TableMetricTest_RestartInjected.java:344-347):**
   ```java
   @Override
   public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
   {
       return null;  // BUG: Should return empty set or filtered results
   }
   ```

3. **During Node Restart:**
   - When the node restarts at position `after_load_system_tables`, the restart framework brings the node back up
   - During restart, `InternalNodeProbe` is instantiated (InternalNodeProbe.java:58)
   - `InternalNodeProbe.connect()` creates a new `GCInspector()` (InternalNodeProbe.java:79)

4. **The NPE Trigger (GCInspector.java:145):**
   ```java
   ObjectName gcName = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
   for (ObjectName name : MBeanWrapper.instance.queryNames(gcName, null))  // NPE here!
   {
       // ... iterate over GC MBeans
   }
   ```
   - `MBeanWrapper.instance.queryNames(gcName, null)` calls the test's `MapMBeanWrapper.queryNames()`
   - This returns `null`
   - The for-each loop tries to iterate over `null`, causing NullPointerException

**Why This Only Fails During Restart:**
Without restart injection, the test doesn't trigger creation of `InternalNodeProbe` after the initial cluster setup. The restart forces re-initialization of services that depend on the MBeanWrapper, exposing the incomplete test implementation.

**Buggy Code Location:**
`test/distributed/org/apache/cassandra/distributed/test/metric/TableMetricTest_RestartInjected.java:344-347`

**Potential Fix:**
The `MapMBeanWrapper.queryNames()` method should return a properly filtered set or at minimum an empty set, not `null`:

```java
@Override
public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
{
    // Option 1: Return empty set (simple fix, matches test's minimal implementation)
    return Collections.emptySet();

    // Option 2: Actually filter the map (more correct but may not be needed for test)
    // return map.keySet().stream()
    //     .filter(on -> name == null || name.apply(on))
    //     .filter(on -> query == null || query.apply(on))
    //     .collect(Collectors.toSet());
}
```

---

## Group 9 (Priority 2)

**Status:** [x] **TEST-BUG** - Incorrect address format assumption in parseGossipInfo

**Execution Count:** 3

**Priority Reason:** Test bug - Caused by thrown from test code

### Generalized Stack Trace
```
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java)
	at org.apache.cassandra.distributed.shared.ClusterUtils.parseGossipInfo(ClusterUtils.java)
	at org.apache.cassandra.distributed.shared.ClusterUtils.gossipInfo(ClusterUtils.java)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossip(ClusterUtils.java)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossipSchemaMatch(ClusterUtils.java)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_getters_test
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.jmx.JMXGetterCheckTest_RestartInjected.testGetters(JMXGetterCheckTest_RestartInjected.java:86)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java:209)
	at org.apache.cassandra.distributed.shared.ClusterUtils.parseGossipInfo(ClusterUtils.java:691)
	at org.apache.cassandra.distributed.shared.ClusterUtils.gossipInfo(ClusterUtils.java:655)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossip(ClusterUtils.java:554)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossipSchemaMatch(ClusterUtils.java:600)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java:85)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:116)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.jmx.JMXGetterCheckTest_RestartInjected`
- Test Method: `testGetters`
- Position: `after_getters_test`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `059-acaf07f8`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.jmx.JMXFeatureTest_RestartInjected`
- Test Method: `testShutDownAndRestartInstances`
- Position: `after_all_getters_test`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `010-901071ff`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.jmx.JMXFeatureTest_RestartInjected`
- Test Method: `testMultipleNetworkInterfacesProvisioning`
- Position: `after_all_getters_test`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `056-48962736`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**
The `parseGossipInfo` method in ClusterUtils.java (line 678-699) has an incorrect assumption about the format of broadcast addresses in gossipinfo output.

**Buggy Code Location:** `test/distributed/org/apache/cassandra/distributed/shared/ClusterUtils.java:685`

**Problem:**
The method only checks if a line starts with "/" to identify instance addresses:
```java
// Line 685
if (line.startsWith("/"))
{
    // start of new instance
    currentInstance = line;
    continue;
}
// Line 691 - throws NPE if currentInstance is still null
Objects.requireNonNull(currentInstance);
```

However, as documented in the method `getBroadcastAddressString()` at line 897-902, broadcast addresses can have two formats:
1. `localhost/127.0.0.1` (hostname/IP format)
2. `/127.0.0.1` (IP-only format)

The actual gossipinfo output uses format #1:
```
localhost/127.0.0.1
  generation:1766008708
  heartbeat:33
  STATUS:23:NORMAL,9223372036854775807
  ...
```

When the first line `localhost/127.0.0.1` doesn't start with "/", it's not recognized as an instance address. The code then tries to parse it as a key-value pair with `currentInstance` still being null, triggering the NPE at line 691.

**Potential Fix:**
The condition at line 685 should be updated to handle both address formats:
```java
// Current buggy code:
if (line.startsWith("/"))

// Should be fixed to:
if (line.startsWith("/") || (line.contains("/") && !line.contains(":")))
```

Or more robustly:
```java
// Check if line looks like an address (has "/" but not ":" which indicates key-value pairs)
if (line.trim().matches("^[^:]+/[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$"))
{
    currentInstance = line.trim();
    continue;
}
```

This would correctly identify both `localhost/127.0.0.1` and `/127.0.0.1` as instance addresses, while properly skipping them from key-value pair parsing.

---

## Group 14 (Priority 2)

**Status:** [x] **FP** - Datastax driver reconnection timing issue after node restart

**Execution Count:** 2

**Priority Reason:** Application exception - Caused by

### Generalized Stack Trace
```
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException
	at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.access$1200(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.findNextHostAndQuery(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.startNewExecution(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.sendRequest(RequestHandler.java)
	at com.datastax.driver.core.SessionManager.execute(SessionManager.java)
	at com.datastax.driver.core.SessionManager.prepareAsync(SessionManager.java)
	at com.datastax.driver.core.AbstractSession.prepareAsync(AbstractSession.java)
	at com.datastax.driver.core.AbstractSession.prepare(AbstractSession.java)
```

### Raw Stack Trace Sample
```
com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
	at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:85)
	at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:39)
	at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
	at com.datastax.driver.core.AbstractSession.prepare(AbstractSession.java:88)
	at org.apache.cassandra.distributed.test.ReprepareNewBehaviourTest_RestartInjected.testUseWithMultipleKeyspaces(ReprepareNewBehaviourTest_RestartInjected.java:106)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
	at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java:285)
	at com.datastax.driver.core.RequestHandler.access$1200(RequestHandler.java:63)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.findNextHostAndQuery(RequestHandler.java:377)
	at com.datastax.driver.core.RequestHandler.startNewExecution(RequestHandler.java:141)
	at com.datastax.driver.core.RequestHandler.sendRequest(RequestHandler.java:123)
	at com.datastax.driver.core.SessionManager.execute(SessionManager.java:707)
	at com.datastax.driver.core.SessionManager.prepareAsync(SessionManager.java:179)
	at com.datastax.driver.core.AbstractSession.prepareAsync(AbstractSession.java:106)
	at com.datastax.driver.core.AbstractSession.prepare(AbstractSession.java:86)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.ReprepareNewBehaviourTest_RestartInjected`
- Test Method: `testUseWithMultipleKeyspaces`
- Position: `after_switch_ks2`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `018-f85cd3b5`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.PrepareBatchStatementsTest_RestartInjected`
- Test Method: `testPreparedBatch`
- Position: `after_schema_creation`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `050-27008f8e`

### Debug Analysis

**Reproduced:** Yes (Both Test 1 and Test 2 configurations)

**Classification:** FALSE POSITIVE (FP)

**Root Cause:**
This failure is caused by a **timing mismatch between Cassandra cluster health and external driver reconnection**. The restart framework correctly restarts the node and ensures internal cluster health, but the external Datastax Java driver needs additional time to detect and reconnect to the restarted node.

**Detailed Analysis:**

1. **Test Flow:**
   - Test creates a Datastax driver session before any restarts (e.g., ReprepareNewBehaviourTest_RestartInjected.java:45-48)
   - Node restart occurs at a restart injection point
   - Test immediately attempts to use the driver session (e.g., session.prepare() at line 106)
   - Driver throws `NoHostAvailableException: All host(s) tried for query failed (no host was tried)`

2. **Restart Framework Behavior (CassandraClusterAdapter.java:135-158):**
   ```java
   private void performGracefulRestart(Cluster cluster, IInvokableInstance instance) {
       // Graceful shutdown
       Future<Void> shutdownFuture = instance.shutdown(true);
       FBUtilities.waitOnFuture(shutdownFuture);
       Thread.sleep(1000);

       // Restart the node
       instance.startup();

       // Wait for node to rejoin ring (INTERNAL cluster health only)
       IInvokableInstance referenceNode = findRunningNode(cluster, nodeNum);
       if (referenceNode != null) {
           ClusterUtils.awaitRingJoin(referenceNode, instance);
       }
   }
   ```

   The adapter waits for the node to rejoin the ring using `ClusterUtils.awaitRingJoin()`, which ensures **internal Cassandra cluster health** (gossip state, ring membership, schema agreement). However, it does NOT wait for **external client drivers** to reconnect.

3. **Why This is an FP:**

   a. **Restart framework is working correctly:**
      - It properly restarts the node and verifies cluster health
      - The `waitActive()` method (lines 71-89) ensures ring health and schema agreement
      - This is the appropriate scope for a cluster restart adapter

   b. **Cassandra source code is not buggy:**
      - The node correctly shuts down, restarts, and rejoins the ring
      - No Cassandra code is malfunctioning

   c. **Test code logic is not buggy:**
      - The original test without restart injection works perfectly (ReprepareNewBehaviourTest.java)
      - The test logic for preparing statements and switching keyspaces is sound

   d. **This is an artifact of restart injection:**
      - The failure only occurs because restart injection creates an artificial scenario where:
        * A long-lived driver connection is established
        * A node restart breaks the connection
        * The test immediately tries to use the connection before driver reconnection completes
      - In normal operation without restart injection, driver connections aren't suddenly interrupted mid-test

4. **Evidence from Test Comparison:**

   Original test (ReprepareNewBehaviourTest.java:37-78):
   - No restarts → No connection disruption → Works fine

   Restart-injected test (ReprepareNewBehaviourTest_RestartInjected.java:39-130):
   - Multiple restarts → Driver connection broken → Immediate reuse fails

5. **Driver Reconnection Timing:**
   - The Datastax driver has built-in reconnection policies (default: exponential backoff)
   - After a connection is lost, the driver needs time to:
     * Detect the connection loss
     * Attempt reconnection
     * Establish a new connection pool
   - This process is asynchronous and takes more than the 1-second sleep in the restart adapter

**Why This is NOT a Bug:**

This failure does not represent a real-world bug because:

1. **External clients are out of scope:** The restart framework correctly focuses on cluster-internal health. Waiting for all possible external client reconnections would be impractical and outside the framework's responsibility.

2. **Normal client behavior:** Real-world applications using Datastax driver typically have:
   - Retry logic with backoff
   - Connection pool health checks
   - Graceful handling of temporary connection loss
   - Natural delays between operations (not immediate reuse after a restart)

3. **Test artifact:** The immediate reuse of the driver connection after a restart is specific to the restart injection scenario and doesn't reflect realistic client usage patterns.

**Conclusion:**
This is a FALSE POSITIVE caused by the timing requirements of external client reconnection, which is beyond the scope of the restart framework. The Cassandra cluster, the restart adapter, and the test logic are all functioning correctly.

---

## Group 16 (Priority 2)

**Status:** [x] **TEST-BUG** - Stale LogAction reference after node restart

**Execution Count:** 2

**Priority Reason:** Application exception - java.lang.IllegalStateException

### Generalized Stack Trace
```
java.lang.IllegalStateException
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClassInternal(InstanceClassLoader.java)
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClass(InstanceClassLoader.java)
	at org.apache.cassandra.distributed.impl.FileLogAction.match(FileLogAction.java)
	at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java)
	at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java)
```

### Raw Stack Trace Sample
```
java.lang.IllegalStateException: Can't load org.apache.cassandra.distributed.impl.FileLogAction$FileLineIterator. Instance class loader is already closed.
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClassInternal(InstanceClassLoader.java:118)
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClass(InstanceClassLoader.java:112)
	at org.apache.cassandra.distributed.impl.FileLogAction.match(FileLogAction.java:58)
	at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java:65)
	at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java:137)
	at org.apache.cassandra.distributed.test.JVMDTestTest_RestartInjected.instanceLogs(JVMDTestTest_RestartInjected.java:111)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.JVMDTestTest_RestartInjected`
- Test Method: `instanceLogs`
- Position: `after_exception_trigger`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `062-7aca286b`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected`
- Test Method: `compactionDoesNotCancelUpgradeSSTables`
- Position: `after_upgradesstables_test2`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `056-7e61b015`

### Debug Analysis

**Reproduced:** Yes (Test 1: JVMDTestTest_RestartInjected.instanceLogs with after_exception_trigger restart)

**Classification:** TEST-BUG

**Root Cause:**

The test holds a stale reference to a `LogAction` object obtained before a node restart. When the node is restarted, its `InstanceClassLoader` is closed, rendering the old `LogAction` reference unusable. Attempting to use this stale reference results in an `IllegalStateException` when trying to load classes through the closed classloader.

**Detailed Analysis:**

1. **Test Sequence (JVMDTestTest_RestartInjected.java:86-121):**
   - Line 88-89: Creates a cluster with 2 nodes
   - Line 90-95: First restart at "after_cluster_start" on node 1 (index 0)
   - Line 99: **`LogAction logs = cluster.get(2).logs();`** - Gets logs from node 2 (index 1)
   - Line 100: `long mark = logs.mark();` - Marks position in log file
   - Line 101-104: Triggers an exception on node 2 to generate error logs
   - Line 105-110: **Second restart at "after_exception_trigger" on node 2 (index 1)** - This is the critical restart
   - Line 111: **`List<String> errors = logs.watchFor(mark, "^ERROR").getResult();`** - FAILS HERE

2. **Why the Failure Occurs:**

   When a distributed test node is restarted:
   - The old `Instance` object is shut down and its `InstanceClassLoader` is closed
   - A new `Instance` object is created with a new `InstanceClassLoader`
   - Any references to objects from the old Instance (like `LogAction`) become stale

   The `LogAction` object obtained at line 99 holds references to:
   - The old Instance's `InstanceClassLoader`
   - The log file path associated with the old Instance

   At line 111, when `logs.watchFor()` is called:
   - It calls `LogAction.watchFor()` (LogAction.java:137)
   - Which calls `LogAction.watchFor()` (LogAction.java:65)
   - Which calls `FileLogAction.match()` (FileLogAction.java:58)
   - At FileLogAction.java:58, it tries to instantiate `new FileLineIterator(reader, fn)`
   - `FileLineIterator` is an inner class that needs to be loaded by the classloader
   - The classloader tries to load the class but finds it's already closed
   - **Result:** `IllegalStateException: Can't load org.apache.cassandra.distributed.impl.FileLogAction$FileLineIterator. Instance class loader is already closed.`

3. **Why This Only Fails With Restart Injection:**

   Without restart injection, the test never restarts node 2 after obtaining the `logs` reference. The `logs` object remains valid throughout the test execution because the Instance's classloader is never closed.

   With restart injection at position `after_exception_trigger`, the restart happens on the same node (node 2) whose logs the test is trying to watch. This closes the classloader associated with the `logs` object, causing it to become unusable.

**Buggy Code Location:**

`test/distributed/org/apache/cassandra/distributed/test/JVMDTestTest_RestartInjected.java:99-111`

The test obtains a `LogAction logs` reference at line 99 but continues to use it at line 111 after restarting the same node at lines 105-110.

**Potential Fix:**

After the restart at line 105-110, the test should re-obtain the logs reference from the restarted node:

```java
// Line 99: Original code
LogAction logs = cluster.get(2).logs();
long mark = logs.mark(); // get the current position so watching doesn't see any previous exceptions
cluster.get(2).runOnInstance(() -> {
    // pretend that an uncaught exception was thrown
    JVMStabilityInspector.uncaughtException(Thread.currentThread(), new RuntimeException("fail without fail"));
});
RestartFramework.at("after_exception_trigger")
    .on(cluster)
    .restart("node")
    .withIndex(1)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// FIX: Re-obtain logs reference after restart since the Instance has been recreated
logs = cluster.get(2).logs();
// Note: The mark position is lost because the new Instance has a new log file
// The test logic would need to be adjusted to handle this appropriately

List<String> errors = logs.watchFor(mark, "^ERROR").getResult();
```

**Alternative Fix:**

The test could be restructured to avoid restarting the node whose logs are being monitored. For example:
- Monitor logs on node 1 instead of node 2
- Or perform all log operations before any restart on that node
- Or restart a different node (node 1) at the `after_exception_trigger` position

**Note on Test 2:**

Test 2 (`UpgradeSSTablesTest_RestartInjected.compactionDoesNotCancelUpgradeSSTables`) likely has a similar pattern where a reference to an Instance object (possibly logs or another object) is held before a restart and used after the restart, resulting in the same classloader issue. The same fix pattern applies: re-obtain any Instance-related references after a restart.

**Fix Verification:**

The fix has been applied and verified. After adding the line `logs = cluster.get(2).logs();` after the restart at line 110, the test now passes successfully:
- **Tests run:** 1
- **Failures:** 0
- **Errors:** 0
- **Result:** BUILD SUCCESSFUL

The `IllegalStateException: Can't load org.apache.cassandra.distributed.impl.FileLogAction$FileLineIterator. Instance class loader is already closed.` exception is completely resolved.

---

## Group 17 (Priority 2)

**Status:** [x] **FP** - Restart adapter doesn't wait for RPC readiness before returning control

**Execution Count:** 2

**Priority Reason:** Application exception - org.apache.cassandra.exceptions.UnavailableException

### Generalized Stack Trace
```
org.apache.cassandra.exceptions.UnavailableException
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java)
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java)
	at org.apache.cassandra.service.StorageProxy.findSuitableReplica(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutateCounter(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutate(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutateWithTriggers(StorageProxy.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithoutCondition(ModificationStatement.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java)
	at java.base/java.lang.Thread.run(Thread.java)
```

### Raw Stack Trace Sample
```
org.apache.cassandra.exceptions.UnavailableException: Cannot achieve consistency level ONE
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:37)
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:31)
	at org.apache.cassandra.service.StorageProxy.findSuitableReplica(StorageProxy.java:1782)
	at org.apache.cassandra.service.StorageProxy.mutateCounter(StorageProxy.java:1725)
	at org.apache.cassandra.service.StorageProxy.mutate(StorageProxy.java:892)
	at org.apache.cassandra.service.StorageProxy.mutateWithTriggers(StorageProxy.java:1158)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithoutCondition(ModificationStatement.java:529)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java:502)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:67)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:47)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java:94)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java:61)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.CountersTest_RestartInjected`
- Test Method: `testUpdateCounter`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `029-71a48fba`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.CountersTest_RestartInjected`
- Test Method: `testUpdateCounterWithDroppedCompactStorage`
- Position: `after_counter_decrement`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `032-d7e603f0`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** FP (False Positive)

**Root Cause:**

The failure is caused by a race condition in gossip propagation of the RPC readiness state after a node restart. This is a limitation of the restart adapter, not a bug in Cassandra's source code or test code.

**Detailed Analysis:**

1. **Test Setup and Flow:**
   - Cluster with 2 nodes, keyspace with `replication_factor: 1`
   - After table creation, node 1 (index 0) is restarted at position `after_table_create`
   - Test immediately loops through coordinators (i=1 to cluster.size()) to execute counter operations

2. **The Race Condition:**

   The failure occurs because of a distributed systems race condition with gossip propagation:

   - **Node Restart Completes:** The restart adapter's `performGracefulRestart()` method (CassandraClusterAdapter.java:135-158) waits for the restarted node to rejoin the ring using `ClusterUtils.awaitRingJoin()` (line 152)

   - **What awaitRingJoin Waits For:** This function (ClusterUtils.java:411-422) only waits for the node to appear in the gossip ring with status "Up" and state "Normal"

   - **What's Missing:** It does NOT wait for the node to be RPC ready (able to accept client requests)

   - **RPC Readiness is Also Gossiped:** When a node starts, it sets `rpcReady=true` in its local gossip state (CassandraDaemon.java:824), but this gossip update takes time to propagate to other nodes

3. **Why Operations Succeed from Node1 but Fail from Node2:**

   Debug logging revealed:
   ```
   [node1_isolatedExecutor:1] - DEBUG findSuitableReplica: ... After RpcReady filter: [node1] ✓
   [node2_isolatedExecutor:1] - DEBUG findSuitableReplica: ... After RpcReady filter: [] ✗
   ```

   - When test executes through **node1's coordinator** (i=1): Node1 can check its own RPC readiness locally - **SUCCESS**
   - When test executes through **node2's coordinator** (i=2): Node2 checks node1's RPC readiness via gossip, but hasn't received the gossip update yet - **FAILURE**

4. **Why This Throws UnavailableException:**

   In `StorageProxy.findSuitableReplica()` (StorageProxy.java:1767-1790):
   ```java
   // Line 1772: Get natural replicas for the counter mutation
   EndpointsForToken replicas = replicationStrategy.getNaturalReplicasForToken(key);

   // Line 1778: Filter out endpoints not RPC ready (CASSANDRA-13043)
   replicas = replicas.filter(replica -> StorageService.instance.isRpcReady(replica.endpoint()));

   // Line 1784: Filter out endpoints not alive (CASSANDRA-17411)
   replicas = replicas.filter(replica -> FailureDetector.instance.isAlive(replica.endpoint()));

   // Line 1789-1790: If no replicas remain after filtering, throw UnavailableException
   if (replicas.isEmpty())
       throw UnavailableException.create(cl, cl.blockFor(replicationStrategy), 0);
   ```

   With `replication_factor: 1`, there's only one replica (node1). After node1 restarts:
   - Node1 sees itself as RPC ready → replica list not empty → SUCCESS
   - Node2 sees node1 as not RPC ready (stale gossip) → replica list becomes empty → **UnavailableException**

**Why This is a False Positive:**

This is classified as **FP** because:

1. **Not a Source Code Bug:** Cassandra's filtering of non-ready replicas is intentional and correct (CASSANDRA-13043, CASSANDRA-17411)

2. **Not a Test Bug:** The test code is reasonable - it's testing counter operations after table creation and restart

3. **Restart Adapter Limitation:** The issue is in the restart adapter's `performGracefulRestart()` method, which only waits for ring membership (Up/Normal) but not for RPC readiness propagation

4. **Unrealistic Test Timing:** In production, there would naturally be time between a node restart completing and client operations resuming, allowing gossip to propagate. The restart framework creates an artificially tight timing window.

**Proper Fix:**

The restart adapter should wait for the restarted node to be globally seen as RPC ready, not just locally. This requires either:
- Waiting for `isRpcReady()` to return true from other nodes' perspectives
- Adding an additional delay after `awaitRingJoin()` to allow gossip propagation
- Implementing a proper check that all cluster nodes have received the RPC ready gossip update

**Verification:**

The failure was reliably reproduced with the exact stack trace showing the UnavailableException at StorageProxy.java:1790 (now 1782 after debug logging additions). The debug output clearly shows the race condition between node1's local RPC readiness and node2's stale gossip view.

---

## Group 20 (Priority 2)

**Status:** [x] **TEST-BUG** - Stale coordinator reference after node restart

**Execution Count:** 1

**Priority Reason:** Application exception - java.util.concurrent.RejectedExecutionException

### Generalized Stack Trace
```
java.util.concurrent.RejectedExecutionException
	at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.addTask(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$6(IsolatedExecutor.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithPagingWithResult(Coordinator.java)
	at org.apache.cassandra.distributed.api.ICoordinator.executeWithPaging(ICoordinator.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.RejectedExecutionException: isolatedExecutor has shut down
	at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java:49)
	at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java:833)
	at java.base/java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java:1365)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.addTask(ThreadPoolExecutorPlus.java:50)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:81)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:33)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$6(IsolatedExecutor.java:150)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java:151)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithPagingWithResult(Coordinator.java:180)
	at org.apache.cassandra.distributed.api.ICoordinator.executeWithPaging(ICoordinator.java:49)
	at org.apache.cassandra.distributed.test.GroupByTest_RestartInjected.testGroupWithDeletesAndPaging(GroupByTest_RestartInjected.java:204)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.GroupByTest_RestartInjected`
- Test Method: `testGroupWithDeletesAndPaging`
- Position: `after_inserts`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `051-899d8831`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**
The test code stores a local `ICoordinator` reference before a node restart and then attempts to use it after the restart, when the underlying Instance has been shut down and replaced.

**Detailed Analysis:**

1. **Coordinator stored before restart (line 186 in GroupByTest_RestartInjected.java):**
   ```java
   ICoordinator coordinator = cluster.coordinator(1);
   ```
   This creates a Coordinator object that wraps the Instance at node 1.

2. **The Coordinator class structure (Coordinator.java:52-56):**
   ```java
   final Instance instance;
   public Coordinator(Instance instance)
   {
       this.instance = instance;
   }
   ```
   The Coordinator holds a final reference to the Instance object.

3. **Node restart occurs (lines 189-194):**
   ```java
   RestartFramework.at("after_inserts")
       .on(cluster)
       .restart("node")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```
   During this restart:
   - The Instance at index 0 (node 1) is shut down
   - A new Instance is created to replace it
   - The old Instance's `isolatedExecutor` is shut down

4. **Coordinator used after restart (line 204):**
   ```java
   Iterator<Object[]> rows = coordinator.executeWithPaging(query, ConsistencyLevel.ALL, 1);
   ```
   The `executeWithPaging` method calls `instance.sync()` (Coordinator.java:120), which tries to submit a task to the old Instance's executor that has been shut down.

5. **Result:**
   ```
   RejectedExecutionException: isolatedExecutor has shut down
   ```

**Buggy Code Location:**
`test/distributed/org/apache/cassandra/distributed/test/GroupByTest_RestartInjected.java:186-204`

**Potential Fix:**
After the restart at line 194, obtain a fresh coordinator reference:
```java
RestartFramework.at("after_inserts")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
// Add this line to refresh the coordinator reference:
coordinator = cluster.coordinator(1);

cluster.get(1).executeInternal(withKeyspace("DELETE FROM %s.tbl WHERE pk=0 AND ck=0"));
// ... rest of the test
```

This ensures the coordinator reference points to the new Instance created after the restart.

---

## Group 21 (Priority 2)

**Status:** [x] **BUG** - Stale token metadata after node restart

**Execution Count:** 1

**Priority Reason:** Application exception - org.apache.cassandra.exceptions.UnavailableException

### Generalized Stack Trace
```
org.apache.cassandra.exceptions.UnavailableException
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java)
	at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicas(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicasForWrite(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java)
	at org.apache.cassandra.service.StorageProxy.performWrite(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutate(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutateWithTriggers(StorageProxy.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithoutCondition(ModificationStatement.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java)
	at java.base/java.lang.Thread.run(Thread.java)
```

### Raw Stack Trace Sample
```
org.apache.cassandra.exceptions.UnavailableException: Cannot achieve consistency level ALL
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:37)
	at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicas(ReplicaPlans.java:193)
	at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicasForWrite(ReplicaPlans.java:136)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java:475)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java:466)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java:460)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java:449)
	at org.apache.cassandra.service.StorageProxy.performWrite(StorageProxy.java:1383)
	at org.apache.cassandra.service.StorageProxy.mutate(StorageProxy.java:894)
	at org.apache.cassandra.service.StorageProxy.mutateWithTriggers(StorageProxy.java:1158)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithoutCondition(ModificationStatement.java:529)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java:502)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:67)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:47)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java:94)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java:61)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.ring.NodeNotInRingTest_RestartInjected`
- Test Method: `nodeNotInRingTest`
- Position: `after_first_populate`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `032-9a74b21a`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** BUG

**Root Cause:**

After a node restarts, it accepts coordinator requests with stale token metadata (ring information), leading to UnavailableException for CL ALL writes.

**Detailed Investigation:**

The test scenario:
1. Creates a 3-node cluster with RF=2
2. Blocks gossip from node 3 and removes node 3 from the ring (via `GossipHelper.removeFromRing()` on nodes 1 and 2)
3. At position `after_first_populate`, node 2 (index 1) is restarted
4. Immediately after restart, the test uses node 2 as coordinator for a write with CL ALL
5. The write fails with `UnavailableException: Cannot achieve consistency level ALL`

**Debug output from instrumented test (NodeNotInRingTest_RestartInjected.java:102-118):**

```
=== DEBUG: About to populate with coordinator 2 after restart ===
Node 1 - effectiveOwnership: {127.0.0.1:7012=1.0, 127.0.0.2:7012=1.0}
Node 1 - liveMembers: [/127.0.0.1:7012, /127.0.0.2:7012]
Node 1 - unreachableMembers: []

Node 2 - effectiveOwnership: {127.0.0.1:7012=0.666666, 127.0.0.2:7012=0.666666, 127.0.0.3:7012=0.666666}
Node 2 - liveMembers: [/127.0.0.1:7012, /127.0.0.2:7012]
Node 2 - unreachableMembers: [/127.0.0.3:7012]

Node 3 - effectiveOwnership: {127.0.0.1:7012=0.666666, 127.0.0.2:7012=0.666666, 127.0.0.3:7012=0.666666}
Node 3 - liveMembers: [/127.0.0.3:7012]
Node 3 - unreachableMembers: [/127.0.0.1:7012, /127.0.0.2:7012]
```

**Key Observation:**

- **Node 1** (non-restarted): `effectiveOwnership` correctly shows only 2 nodes (0.1 + 1.0 = 100% split between 2 nodes)
- **Node 2** (just restarted): `effectiveOwnership` incorrectly shows 3 nodes (0.666666 × 3 = 100% split between 3 nodes)

Node 2's gossip state shows it knows about live/unreachable members correctly:
- `liveMembers`: nodes 1 and 2
- `unreachableMembers`: node 3

However, its **token metadata** (`effectiveOwnership`) is stale and still includes node 3 in the ring.

**Why this causes the failure:**

When node 2 (acting as coordinator) receives a write request with CL ALL:
1. It consults its token metadata which indicates 3 nodes are in the ring
2. With RF=2, it tries to determine the replicas for the write
3. The stale metadata causes incorrect replica planning
4. `ReplicaPlans.assureSufficientLiveReplicasForWrite()` at line 136 checks if enough live replicas are available
5. Due to the inconsistency between token metadata (3 nodes) and gossip state (node 3 unreachable), it cannot satisfy CL ALL
6. Throws `UnavailableException` at `ReplicaPlans.java:193`

**Buggy Component:**

The bug is in Cassandra's node startup sequence. Specifically:

**File:** `src/java/org/apache/cassandra/service/StorageService.java` (startup logic)

**Issue:** When a node restarts, it:
1. Loads persisted token metadata from disk (which may be stale if ring changes occurred while the node was down)
2. Begins accepting coordinator requests before token metadata is synchronized with current gossip state
3. The node knows from gossip that node 3 is unreachable, but hasn't updated `effectiveOwnership` to reflect that node 3 was removed from the ring

**Expected Behavior:**

A restarted node should NOT accept coordinator requests until:
1. Its token metadata is synchronized with the current ring state from gossip, OR
2. It has a mechanism to detect stale metadata and either update it on-the-fly or reject requests

**Potential Fix:**

The fix should be in the node startup sequence to ensure token metadata consistency:

```java
// In StorageService.java startup sequence
// After gossip is initialized and before marking the node as ready to accept coordinator requests:

private void waitForTokenMetadataSync()
{
    // Wait for pending range calculations to complete
    PendingRangeCalculatorService.instance.blockUntilFinished();

    // Ensure all "left" nodes are removed from token metadata
    for (InetAddressAndPort endpoint : Gossiper.instance.getLiveMembers())
    {
        EndpointState state = Gossiper.instance.getEndpointStateForEndpoint(endpoint);
        if (state != null)
        {
            VersionedValue vv = state.getApplicationState(ApplicationState.STATUS);
            if (vv != null)
            {
                String status = vv.value;
                // Process any LEFT states before accepting coordinator requests
                if (status.startsWith(VersionedValue.STATUS_LEFT))
                {
                    // Ensure onChange handler has processed this
                    onChange(endpoint, ApplicationState.STATUS, vv);
                }
            }
        }
    }
}
```

Alternatively, the fix could be in `ReplicaPlans.java` to validate token metadata consistency before creating replica plans, but this would be treating the symptom rather than the root cause.

**Production Impact:**

This bug can occur in production when:
1. An administrator removes a node from the cluster (via `nodetool removenode` or decommission)
2. Another node in the cluster crashes and restarts shortly after (before the removal is fully persisted)
3. The restarted node may incorrectly reject writes with high consistency levels due to stale token metadata
4. This could cause temporary write unavailability until gossip fully synchronizes

---

## Group 23 (Priority 2)

**Status:** [x] **TEST-BUG** - Restarting the same node that is executing an async operation

**Execution Count:** 1

**Priority Reason:** Application exception - Caused by

### Generalized Stack Trace
```
Caused by: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.checkInterrupted(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.await(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.await(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.await(Awaitable.java)
	at org.apache.cassandra.utils.concurrent.AsyncFuture.await(AsyncFuture.java)
	at org.apache.cassandra.utils.concurrent.AsyncFuture.await(AsyncFuture.java)
	at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.waitOn(IsolatedExecutor.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.ExecutionException: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.AbstractFuture.getWhenDone(AbstractFuture.java:239)
	at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java:246)
	at org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected.testDynamicContentionTracing(CASContentionTest_RestartInjected.java:139)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
	at org.apache.cassandra.utils.Throwables.unchecked(Throwables.java:307)
	at org.apache.cassandra.utils.Throwables.throwAsUncheckedException(Throwables.java:318)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.waitOn(IsolatedExecutor.java:281)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java:151)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithResult(Coordinator.java:61)
	at org.apache.cassandra.distributed.api.ICoordinator.execute(ICoordinator.java:32)
	at org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected.lambda$testDynamicContentionTracing$2(CASContentionTest_RestartInjected.java:109)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.checkInterrupted(WaitQueue.java:361)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.await(WaitQueue.java:320)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.await(WaitQueue.java:299)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.await(Awaitable.java:306)
	at org.apache.cassandra.utils.concurrent.AsyncFuture.await(AsyncFuture.java:154)
	at org.apache.cassandra.utils.concurrent.AsyncFuture.await(AsyncFuture.java:46)
	at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java:245)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.waitOn(IsolatedExecutor.java:276)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected`
- Test Method: `testDynamicContentionTracing`
- Position: `after_filter_reset`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `007-e3ac67f6`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**
The test restarts the same node (node 1) that is executing an asynchronous CAS operation, causing the async task to be interrupted during shutdown. When the test later waits for the operation to complete with `Future.get()`, it receives an `InterruptedException` wrapped in `ExecutionException`.

**Failure Sequence:**

1. **Line 108-110 (CASContentionTest_RestartInjected.java):** Test starts an async CAS INSERT operation on node 1:
   ```java
   Future<?> insert = THREE_NODES.get(1).async(() -> {
       THREE_NODES.coordinator(1).execute("INSERT INTO ...", QUORUM);
   }).call();
   ```
   - `THREE_NODES.get(1)` - node 1 (1-based API indexing, internally at index 0)
   - The async task runs on node1's `isolatedExecutor`
   - The operation is initially blocked by a message filter

2. **Line 111:** Test waits for the async operation to start

3. **Line 120:** Test executes a second INSERT to create contention

4. **Line 129-130:** Test unblocks the filter and resets filters, allowing the first async INSERT to proceed

5. **Line 132-137:** Restart point `after_filter_reset` executes:
   ```java
   RestartFramework.at("after_filter_reset")
       .on(THREE_NODES)
       .restart("node")
       .withIndex(0)  // This is node 1 in 1-based indexing!
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```
   - **PROBLEM:** `withIndex(0)` restarts the node at index 0, which is node 1
   - Node 1 is the SAME node running the async CAS operation from line 108

6. **During node 1 shutdown:**
   ```
   INFO  [main] node1 2025-12-17 16:55:50,205 Instance.java:842 - Shutting down instance 1 / 127.0.0.1
   ERROR 22:55:52 Exception in thread Thread[node1_isolatedExecutor:2,5,isolatedExecutor]
   org.apache.cassandra.exceptions.WriteFailureException: Operation failed - received 0 responses and 0 failures
   ```
   - When node 1 shuts down, it terminates all executor threads
   - The async task running on `node1_isolatedExecutor:2` receives an interrupt
   - The CAS operation fails with `WriteFailureException` then `InterruptedException`

7. **Line 139:** Test calls `insert.get()` to wait for the async operation:
   ```
   DEBUG: After restart at after_filter_reset. Future isDone: true, isCancelled: false
   DEBUG: About to call insert.get()
   DEBUG: insert.get() threw exception: java.util.concurrent.ExecutionException:
          org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
   ```
   - The Future is already done (completed exceptionally due to the interrupt)
   - Calling `get()` throws `ExecutionException` wrapping the `InterruptedException`

**Why This is a TEST-BUG:**

The test's restart injection is fundamentally flawed:
- It submits an async operation to node 1's executor
- Then it restarts node 1 before the operation completes
- Restarting a node while it's executing an async task will always interrupt that task
- This is not testing Cassandra's behavior under node restarts; it's testing what happens when you kill the executor running your test code

**Buggy Code Location:**
- File: `test/distributed/org/apache/cassandra/distributed/test/CASContentionTest_RestartInjected.java`
- Lines: 108-110 (starts async on node 1), 132-137 (restarts node 1), 139 (waits for completed async task)

**Why This is Not a Source Code Bug:**
- The restart infrastructure is working correctly - shutting down a node interrupts its executor threads
- Cassandra's async execution and Future handling is correct
- The InterruptedException is the expected behavior when an executor is shut down during task execution
- In a real deployment, you wouldn't restart a node in the middle of executing application code on that node's executor

**Potential Fixes:**

Option 1: Restart a different node (not the one running the async operation):
```java
RestartFramework.at("after_filter_reset")
    .on(THREE_NODES)
    .restart("node")
    .withIndex(1)  // Restart node 2 instead of node 1
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

Option 2: Wait for the async operation to complete BEFORE restarting:
```java
haveInvalidated.countDown();
THREE_NODES.filters().reset();
insert.get();  // Wait for completion

RestartFramework.at("after_filter_reset")
    .on(THREE_NODES)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

Option 3: Remove the problematic restart point entirely, as it doesn't test meaningful Cassandra behavior.

**Impact:** This is the only test execution in this failure group, and it's caused by improper test structure rather than a Cassandra bug.

---

## Group 4 (Priority 3)

**Status:** [x] **FP** - Restart injection at point where test has artificially stopped gossip on all nodes

**Execution Count:** 10

**Priority Reason:** Exception with restart framework involvement

### Generalized Stack Trace
```
Caused by: java.lang.RuntimeException
	at org.apache.cassandra.gms.Gossiper.doShadowRound(Gossiper.java)
	at org.apache.cassandra.service.StorageService.checkForEndpointCollision(StorageService.java)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_verification
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.GossipSettlesTest_RestartInjected.testGossipSettles(GossipSettlesTest_RestartInjected.java:135)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 2
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.RuntimeException: Unable to gossip with any peers
	at org.apache.cassandra.gms.Gossiper.doShadowRound(Gossiper.java:2075)
	at org.apache.cassandra.service.StorageService.checkForEndpointCollision(StorageService.java:866)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1181)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:947)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java:708)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.GossipSettlesTest_RestartInjected`
- Test Method: `testGossipSettles`
- Position: `after_verification`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `052-132c22ff`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.gossip.GossipShutdownTest_RestartInjected`
- Test Method: `emptyStateAndShutdownEvent`
- Position: `after_filter_setup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `046-b98f1031`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.HintedHandoffAddRemoveNodesTest_RestartInjected`
- Test Method: `shouldStreamHintsDuringDecommission`
- Position: `after_final_decommissions`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `019-868ef345`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** FP (False Positive)

**Root Cause:**
The restart framework is injecting a restart at a point where the test has artificially stopped gossip on all nodes, creating an invalid state for node restart.

**Test Execution Flow (GossipSettlesTest):**
1. **Line 54-57**: Cluster starts with 3 nodes (node1=127.0.1.1 is SEED, node2=127.0.1.2 and node3=127.0.1.3 are non-seeds)
2. **Line 70**: Test calls `cluster.forEach(i -> i.runOnInstance(() -> { Gossiper.instance.stop(); }))` - **STOPS GOSSIP ON ALL NODES**
3. **Line 79-128**: Test runs verification logic on node1
4. **Line 130-135**: Test reaches restart point "after_verification" which triggers restart of node at index 1 (node2)

**Timeline from test logs:**
```
17:03:28.270 - node1 announces shutdown (Gossiper.stop() called by test line 70)
17:03:30.275 - node2 announces shutdown (Gossiper.stop() called by test line 70)
17:03:32.278 - node3 announces shutdown (Gossiper.stop() called by test line 70)
17:03:34.293 - RestartFramework activates restart point "after_verification" for node2
17:03:34.299 - node2 begins shutdown for restart
17:03:41.124 - node2 tries shadow round during startup and FAILS
```

**Why It Fails:**
When node2 (a non-seed node) tries to restart:
1. Node2 must complete a "shadow round" to gossip with peers before fully joining the ring (Gossiper.java:2023)
2. Non-seed nodes MUST successfully gossip with at least one peer or they throw "Unable to gossip with any peers" (Gossiper.java:2074-2075)
3. Node1 (the seed) has gossip STOPPED from the test's Gossiper.stop() call, so it doesn't respond to node2's gossip messages
4. Node3 also has gossip STOPPED, so it doesn't respond either
5. After timeout (shadowRoundDelay), node2 fails with the exception

**Code Reference (Gossiper.java:2071-2075):**
```java
if (slept > shadowRoundDelay)
{
    // if we got here no peers could be gossiped to. If we're a seed that's OK, but otherwise we stop.
    if (!isSeed)
        throw new RuntimeException("Unable to gossip with any peers");
```

**Why This is a False Positive:**
1. **Artificial Test State**: The test deliberately stops gossip on ALL nodes for verification purposes (to prevent heartbeat changes). This is not a production scenario.
2. **Restart Injection Timing**: In the original test without restart injection, there would be intermediate restarts that would re-enable gossip on some nodes. The restart framework bypasses those intermediate steps.
3. **Expected Cassandra Behavior**: Cassandra correctly refuses to start a non-seed node when no seeds are available for gossip. This is proper safety behavior.
4. **Not a Real Bug**: This failure only occurs because restart is injected at a point where the system is in an artificial state (all gossip stopped) that wouldn't exist during normal node restarts in production.

**Similar Pattern in Other Test Executions:**
All 10 test executions in this group likely involve scenarios where tests manipulate gossip state (stopping/restarting gossip) or where nodes are being decommissioned/removed (making seeds unavailable), and the restart injection happens at an incompatible point.

---

## Group 6 (Priority 3)

**Status:** [x] **FP** - Restarting decommissioned nodes is semantically incorrect

**Execution Count:** 4

**Priority Reason:** Exception with restart framework involvement

### Generalized Stack Trace
```
Caused by: org.apache.cassandra.exceptions.ConfigurationException
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_successful_decommission
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart(DecommissionTest_RestartInjected.java:208)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: org.apache.cassandra.exceptions.ConfigurationException: This node was decommissioned and will not rejoin the ring unless -Dcassandra.override_decommission=true has been set, or all existing data is removed and the node is bootstrapped again
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1144)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:947)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java:708)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected`
- Test Method: `testDecommissionAfterNodeRestart`
- Position: `after_successful_decommission`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `045-76bbfef7`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.HintedHandoffAddRemoveNodesTest_RestartInjected`
- Test Method: `shouldAvoidHintTransferOnDecommission`
- Position: `after_decommission`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `043-da6c8e70`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.streaming.LCSStreamingKeepLevelTest_RestartInjected`
- Test Method: `testDecom`
- Position: `after_decommission`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `065-99f590ae`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** FP (False Positive)

**Root Cause:**
The restart framework is attempting to restart nodes at positions `after_successful_decommission` and `after_decommission`, which is semantically incorrect and violates Cassandra's intentional design.

**Why This Is a False Positive:**

1. **Cassandra's Intentional Safety Mechanism:**
   The error is thrown by Cassandra's deliberate protection in `StorageService.prepareToJoin()` (src/java/org/apache/cassandra/service/StorageService.java:1135-1147):
   ```java
   if (SystemKeyspace.wasDecommissioned())
   {
       if (OVERRIDE_DECOMMISSION.getBoolean())
       {
           logger.warn("This node was decommissioned, but overriding by operator request.");
           SystemKeyspace.setBootstrapState(SystemKeyspace.BootstrapState.COMPLETED);
       }
       else
       {
           throw new ConfigurationException("This node was decommissioned and will not rejoin the ring unless -D" + OVERRIDE_DECOMMISSION.getKey() +
                                            "=true has been set, or all existing data is removed and the node is bootstrapped again");
       }
   }
   ```
   This prevents decommissioned nodes from accidentally rejoining the cluster, which is correct behavior.

2. **Original Tests Never Restart Decommissioned Nodes:**

   **DecommissionTest.testDecommissionAfterNodeRestart** (line 125-186):
   - The original test decommissions a node and then **ends** - it never attempts to restart after decommission
   - The restart-injected version added a restart point at line 203-208 (`after_successful_decommission`) that doesn't exist in the original test

   **HintedHandoffAddRemoveNodesTest.shouldAvoidHintTransferOnDecommission** (line 55-84):
   - The original test decommissions node 1 at line 78
   - The test then checks results and **ends** at line 83 - NO restart after decommission
   - The restart-injected version added a restart at `after_decommission` which is not part of the original test logic

3. **Decommission Semantics:**
   - Decommissioning is a permanent operation that removes a node from the cluster
   - A decommissioned node is not meant to rejoin without explicit operator override (`-Dcassandra.override_decommission=true`) or data removal
   - Attempting to restart a decommissioned node is not a valid test scenario in the context of these tests

4. **Restart Position Is Invalid:**
   - The restart positions `after_successful_decommission` and `after_decommission` are inherently problematic
   - They attempt to restart a node that has been intentionally and permanently removed from the cluster
   - This is not testing Cassandra's behavior - it's testing the restart framework's ability to override Cassandra's safety mechanisms

**Conclusion:**
This failure is caused by the restart framework injecting restart points at semantically incorrect positions. The original tests do not expect or test the scenario of restarting decommissioned nodes. Cassandra's behavior is correct and intentional.

---

## Group 10 (Priority 3)

**Status:** [x] **FP** - Restart injection attempts to restart already-stopped nodes

**Execution Count:** 3

**Priority Reason:** Exception with restart framework involvement

### Generalized Stack Trace
```
Caused by: java.lang.IllegalStateException
	at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.shutdown(AbstractCluster.java)
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_stop_all_2
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.hostreplacement.HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNodeAndOtherNodeStartsAfter(HostReplacementOfDownedClusterTest_RestartInjected.java:229)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.IllegalStateException: Instance is not running, so can not be shutdown
	at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.shutdown(AbstractCluster.java:449)
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:140)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.hostreplacement.HostReplacementOfDownedClusterTest_RestartInjected`
- Test Method: `hostReplacementOfDeadNodeAndOtherNodeStartsAfter`
- Position: `after_stop_all_2`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `004-211b7df2`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.RestartTest_RestartInjected`
- Test Method: `test`
- Position: `after_both_nodes_shutdown`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `031-6043a934`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.hostreplacement.HostReplacementOfDownedClusterTest_RestartInjected`
- Test Method: `hostReplacementOfDeadNode`
- Position: `after_stop_all`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `004-b9fd3ca8`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration: `hostReplacementOfDeadNodeAndOtherNodeStartsAfter` at position `after_stop_all_2`)

**Classification:** FP (False Positive)

**Root Cause:**

The restart framework is attempting to inject restarts at positions immediately after the test has explicitly stopped all nodes in the cluster. Since the nodes are already stopped, the graceful restart operation fails when trying to shut down an already-shutdown instance.

**Detailed Analysis:**

1. **Test Execution Flow (HostReplacementOfDownedClusterTest_RestartInjected.java):**

   For `hostReplacementOfDeadNodeAndOtherNodeStartsAfter`:
   - Line 191-194: Creates a 3-node cluster and starts it
   - Line 222: **`stopAll(cluster)`** - Explicitly stops ALL nodes in the cluster
   - Line 224-229: **Restart injection at `after_stop_all_2`** - Attempts to restart node at index 0
   - Line 232: `seed.startup()` - Test expects to manually start the seed node

2. **Restart Adapter Behavior (CassandraClusterAdapter.java:135-158):**

   The `performGracefulRestart` method expects to restart a **running** node:
   ```java
   // Line 140: Attempts to gracefully shutdown the instance first
   Future<Void> shutdownFuture = instance.shutdown(true);
   ```

   However, the node has already been stopped by `stopAll(cluster)` at line 222 of the test.

3. **AbstractCluster Safety Check (AbstractCluster.java:446-449):**

   ```java
   public synchronized Future<Void> shutdown(boolean graceful)
   {
       if (isShutdown())
           throw new IllegalStateException("Instance is not running, so can not be shutdown");
       // ...
   }
   ```

   This is a **valid safety mechanism** to prevent double shutdown. When the restart adapter tries to shut down an already-stopped instance, this check correctly throws `IllegalStateException`.

4. **Similar Pattern in Other Test Executions:**
   - Test 2: Position `after_both_nodes_shutdown` - both nodes are explicitly shut down
   - Test 3: Position `after_stop_all` - all nodes are stopped via `stopAll(cluster)`

**Why This is a False Positive:**

1. **Restart positions are semantically incorrect:** The positions `after_stop_all_2`, `after_both_nodes_shutdown`, and `after_stop_all` all occur after the test has intentionally stopped all nodes. At these points, attempting to "restart" a node that's already stopped is not a meaningful operation.

2. **Not testing realistic scenarios:** The original tests are designed to validate host replacement after complete cluster crashes and manual recovery. They never intended to test "restarting an already-stopped node" - that scenario doesn't make sense in the context of these tests.

3. **Test has explicit control flow:** The tests explicitly control when nodes are stopped (`stopAll()`) and when they are started (`seed.startup()`). The restart injection interferes with this explicit control flow by trying to restart nodes that the test has intentionally left stopped.

4. **Restart framework working as designed:** The restart adapter correctly expects nodes to be running when performing a graceful restart. The safety check in AbstractCluster is functioning properly to prevent invalid operations.

5. **Not a bug in Cassandra or tests:**
   - Cassandra's distributed test infrastructure correctly validates that instances must be running before shutdown
   - The test code correctly manages node lifecycle for host replacement scenarios
   - The restart adapter correctly implements graceful restart for running nodes

**Why Not a Framework Issue:**

The restart adapter is designed to restart **running** nodes to test resilience under node restarts. It is not designed to start already-stopped nodes (that would be a different operation: "start stopped node" rather than "restart running node"). The positions where restart injection occurs should be at points where nodes are running, not after explicit `stopAll()` calls.

**Conclusion:**

This failure is a false positive caused by restart injection at semantically incorrect positions - immediately after the test has explicitly stopped all nodes. The original tests don't expect or require node restarts at these positions, and attempting to restart already-stopped nodes is not testing any meaningful Cassandra behavior.

**Relevant Code References:**
- Test file: `test/distributed/org/apache/cassandra/distributed/test/hostreplacement/HostReplacementOfDownedClusterTest_RestartInjected.java:222-229`
- Restart adapter: `restart-adapter/src/main/java/org/apache/cassandra/restart/CassandraClusterAdapter.java:140`
- Safety check: `test/distributed/org/apache/cassandra/distributed/impl/AbstractCluster.java:446-449`

---

## Group 24 (Priority 3)

**Status:** [x] **TEST-BUG** - JVM-wide REPLACE_ADDRESS property affects unintended nodes during restart

**Execution Count:** 1

**Priority Reason:** Exception with restart framework involvement

### Generalized Stack Trace
```
Caused by: java.lang.RuntimeException
	at org.apache.cassandra.service.StorageService.prepareForReplacement(StorageService.java)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_bootstrap
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.MigrationCoordinatorTest_RestartInjected.replaceNode(MigrationCoordinatorTest_RestartInjected.java:114)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.RuntimeException: Cannot replace address with a node that is already bootstrapped
	at org.apache.cassandra.service.StorageService.prepareForReplacement(StorageService.java:734)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1159)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:947)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java:708)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.MigrationCoordinatorTest_RestartInjected`
- Test Method: `replaceNode`
- Position: `after_bootstrap`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `043-3d9de488`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**
The test incorrectly uses a JVM-wide system property (REPLACE_ADDRESS) that affects all nodes in the cluster, not just the node being bootstrapped. When the restart framework restarts an existing node after the bootstrap, that node also sees the REPLACE_ADDRESS property and incorrectly attempts to enter replacement mode.

**Detailed Flow:**

1. **Test Setup (line 106 in MigrationCoordinatorTest_RestartInjected.java):**
   ```java
   withProperties.set(REPLACE_ADDRESS, replacementAddress.getHostAddress());
   ```
   - `WithProperties.set()` calls `System.setProperty()` which sets a **JVM-wide system property**
   - This property is visible to ALL nodes in the cluster, not just the new node being bootstrapped
   - Source: `test/distributed/org/apache/cassandra/distributed/shared/WithProperties.java:77`

2. **Bootstrap new node (line 107):**
   ```java
   cluster.bootstrap(config).startup();
   ```
   - The new node (node 3) starts with REPLACE_ADDRESS set and successfully replaces node 2
   - REPLACE_ADDRESS property remains set in the JVM

3. **Restart existing node (lines 109-114):**
   ```java
   RestartFramework.at("after_bootstrap")
       .on(cluster)
       .restart("node")
       .withIndex(0)  // Restart node 1
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```
   - Restart framework attempts to restart node 1 (at index 0)
   - Node 1 goes through normal startup: `Instance.startup()` → `StorageService.initServer()` (line 708)
   - `StorageService.initServer()` calls `DatabaseDescriptor.getReplaceAddress()` (line 1105)
   - `DatabaseDescriptor.getReplaceAddress()` reads the REPLACE_ADDRESS system property (line 2125)
   - Since REPLACE_ADDRESS is still set, `prepareForReplacement()` is called
   - `prepareForReplacement()` checks if `SystemKeyspace.bootstrapComplete()` is true (line 733)
   - Since node 1 is already bootstrapped, it throws:
     ```
     "Cannot replace address with a node that is already bootstrapped"
     ```

**Buggy Code Location:**
- Test: `test/distributed/org/apache/cassandra/distributed/test/MigrationCoordinatorTest_RestartInjected.java:106-114`

**Buggy Code:**
```java
// Line 106: Sets JVM-wide property that affects ALL nodes
withProperties.set(REPLACE_ADDRESS, replacementAddress.getHostAddress());
cluster.bootstrap(config).startup();

// Lines 109-114: Tries to restart node 1, but REPLACE_ADDRESS is still set
RestartFramework.at("after_bootstrap")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

**Potential Fix:**
Clear the REPLACE_ADDRESS property after the bootstrap completes and before restarting any other nodes:

```java
// Option 1: Clear all properties
withProperties.set(REPLACE_ADDRESS, replacementAddress.getHostAddress());
cluster.bootstrap(config).startup();
withProperties.close(); // Clear all properties including REPLACE_ADDRESS

RestartFramework.at("after_bootstrap")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Option 2: Clear only REPLACE_ADDRESS
withProperties.set(REPLACE_ADDRESS, replacementAddress.getHostAddress());
cluster.bootstrap(config).startup();
REPLACE_ADDRESS.clearValue(); // Clear only REPLACE_ADDRESS

RestartFramework.at("after_bootstrap")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

**Impact:**
This same issue affects all three test methods in MigrationCoordinatorTest_RestartInjected:
- `replaceNode()` (line 68)
- `explicitEndpointIgnore()` (line 119)
- `explicitVersionIgnore()` (line 170)

All three tests bootstrap a new node with REPLACE_ADDRESS set and then attempt to restart other nodes without clearing the property first.

---

## Group 7 (Priority 4)

**Status:** [x] **FP** - Restart causes loss of manually manipulated bootstrapping state and mid-CAS ring view change

**Execution Count:** 4

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
org.apache.cassandra.exceptions.CasWriteTimeoutException
	at org.apache.cassandra.service.paxos.Paxos$MaybeFailure.markAndThrowAsTimeoutOrFailure(Paxos.java)
	at org.apache.cassandra.service.paxos.Paxos.begin(Paxos.java)
	at org.apache.cassandra.service.paxos.Paxos.cas(Paxos.java)
	at org.apache.cassandra.service.paxos.Paxos.cas(Paxos.java)
	at org.apache.cassandra.service.StorageProxy.cas(StorageProxy.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithCondition(ModificationStatement.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java)
	at java.base/java.lang.Thread.run(Thread.java)
```

### Raw Stack Trace Sample
```
org.apache.cassandra.exceptions.CasWriteTimeoutException: CAS operation timed out: received 1 of 2 required responses after 0 contention retries
	at org.apache.cassandra.service.paxos.Paxos$MaybeFailure.markAndThrowAsTimeoutOrFailure(Paxos.java:565)
	at org.apache.cassandra.service.paxos.Paxos.begin(Paxos.java:1072)
	at org.apache.cassandra.service.paxos.Paxos.cas(Paxos.java:677)
	at org.apache.cassandra.service.paxos.Paxos.cas(Paxos.java:636)
	at org.apache.cassandra.service.StorageProxy.cas(StorageProxy.java:327)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithCondition(ModificationStatement.java:542)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java:501)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:67)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:47)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java:94)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java:61)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.CASTest_RestartInjected`
- Test Method: `testSucccessfulWriteDuringRangeMovementFollowedByRead`
- Position: `after_bootstrap_setup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `037-18a19bb5`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.CASTest_RestartInjected`
- Test Method: `testSuccessfulWriteDuringRangeMovementFollowedByConflicting`
- Position: `after_bootstrap_setup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `037-f70c4759`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.CASTest_RestartInjected`
- Test Method: `testIncompleteWriteFollowedBySuccessfulWriteWithStaleRingDuringRangeMovementFollowedByRead`
- Position: `after_bootstrap_setup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `029-ff964b52`

### Debug Analysis

**Reproduced:** Yes (Test 1: testSucccessfulWriteDuringRangeMovementFollowedByRead with after_bootstrap_setup restart)

**Classification:** FP (False Positive)

**Root Cause:**

The restart at position `after_bootstrap_setup` causes node 2 to lose manually manipulated (test-specific) ring state, which then triggers a ring view change on node 1 during the CAS operation. This causes the CAS to timeout because the coordinator's view of which nodes to contact changes mid-operation.

**Detailed Analysis:**

1. **Test Setup (CASTest_RestartInjected.java:554-563):**
   - The test manually manipulates the ring state to simulate a bootstrap scenario
   - Lines 554-558: Removes node 4 from ALL nodes' ring views
   - Lines 559-563: Adds node 4 back as **BOOTSTRAPPING** to nodes 2, 3, and 4 (but NOT to node 1)
   - This creates an intentional inconsistency: node 1 doesn't know node 4 is bootstrapping

2. **Before Restart - Ring State (from debug output):**
   ```
   Node 1: Bootstrapping tokens: []           (node 1 doesn't see node 4 as bootstrapping)
   Node 2: Bootstrapping tokens: [9223372036854775801]  (node 2 sees node 4 as bootstrapping)
   Node 3: Bootstrapping tokens: [9223372036854775801]
   Node 4: Bootstrapping tokens: [9223372036854775801]
   ```

3. **Restart Event (CASTest_RestartInjected.java:581-586):**
   - Node 2 (index 1) is restarted at position `after_bootstrap_setup`
   - **CRITICAL ISSUE:** The manually manipulated bootstrapping state is stored in-memory only
   - When node 2 restarts, it loses the knowledge that node 4 was in bootstrapping state

4. **After Restart - Ring State (from debug output):**
   ```
   Node 1: Bootstrapping tokens: []           (still doesn't see node 4 as bootstrapping)
   Node 2: Bootstrapping tokens: []           (**LOST the bootstrapping state!**)
   Node 3: Bootstrapping tokens: [9223372036854775801]  (still has it)
   Node 4: Bootstrapping tokens: [9223372036854775801]  (still has it)
   ```

5. **The Fatal Gossip Message:**
   After restart, node 2 gossips its view of the ring to other nodes. From the logs:
   ```
   [node1_GossipStage:1] node1 - Node /127.0.0.4:7012 is now part of the cluster
   [node1_GossipStage:1] node1 - Node /127.0.0.4:7012 state NORMAL, token [9223372036854775801]
   [node1_GossipStage:1] node1 - New node /127.0.0.4:7012 at token 9223372036854775801
   ```

   **This happens DURING the CAS operation execution!** Node 1 receives gossip from node 2 saying that node 4 is now NORMAL (not bootstrapping), which causes node 1's token metadata to update mid-operation.

6. **CAS Operation Failure (CASTest_RestartInjected.java:613):**
   - Node 1 coordinator starts a CAS operation
   - Message filters are set up (line 612): `drop(FOUR_NODES, 1, to(3), to(3), to(2, 3))`
   - This blocks communication from node 1 to node 3 for prepare/propose phases
   - The test expects: communicate with nodes 1, 2, and 4 (excluding 3)

   **But the ring view changes during operation:**
   - Initially, node 1 thinks the replicas are {1, 2, 3} (based on original 3-node ring without node 4)
   - Mid-operation, gossip updates node 1's view to include node 4 in the ring
   - Now the coordinator recalculates replicas and might get a different set
   - With message filters blocking node 3, the coordinator only gets 1 response when it needs 2
   - Result: `CasWriteTimeoutException: received 1 of 2 required responses`

**Why This is a False Positive:**

1. **No Cassandra Bug:**
   - Cassandra's bootstrap protocol works correctly - bootstrapping state is gossiped and managed properly through normal operations
   - The gossip protocol correctly updates node views when ring topology changes
   - CAS operations correctly timeout when quorum cannot be achieved

2. **No Test Bug:**
   - The original test (without restart injection) correctly verifies CAS behavior during range movements
   - The test's use of manual ring manipulation is a valid testing technique to create specific scenarios

3. **Restart Position Incompatibility:**
   - The restart at `after_bootstrap_setup` fundamentally undermines the test's preconditions
   - The test requires maintaining an intentional inconsistency (node 1 not knowing about node 4's bootstrap)
   - Restarting node 2 causes it to lose this test-specific state
   - In real Cassandra deployments, bootstrap state is managed through proper protocols and persisted/gossiped correctly
   - The manual ring manipulation done by the test via `CASTestBase::removeFromRing` and `CASTestBase::addToRingBootstrapping` is not meant to survive restarts

4. **Timing Artifact:**
   - The failure only occurs because the gossip message arrives during CAS operation execution
   - If gossip had completed before the CAS operation, the test would still fail but for different reasons (wrong replica set)
   - This creates a race condition between gossip propagation and CAS execution

**Why This is a Strong FP:**

The restart injection creates an impossible scenario:
- The test requires an asymmetric ring view where some nodes see node 4 as bootstrapping and others don't
- This asymmetry is maintained through manual in-memory manipulation
- Restarting any node that has this manipulated state causes it to be lost
- The restart causes the carefully constructed test scenario to collapse mid-execution

In real Cassandra:
- Bootstrap state is managed through proper protocols and gossiped correctly
- All nodes would have consistent views of bootstrapping nodes (or be in the process of converging to consistency)
- Manual ring manipulation APIs used in tests are not how production systems manage topology changes

**Relevant Code References:**
- Test setup: `test/distributed/org/apache/cassandra/distributed/test/CASTest_RestartInjected.java:554-563`
- Restart point: `test/distributed/org/apache/cassandra/distributed/test/CASTest_RestartInjected.java:581-586`
- Failing CAS: `test/distributed/org/apache/cassandra/distributed/test/CASTest_RestartInjected.java:612-614`
- Ring manipulation: `test/distributed/org/apache/cassandra/distributed/test/CASTestBase.java` (removeFromRing, addToRingBootstrapping methods)

**Impact:** All 4 test executions in this group fail with the same pattern - restart at `after_bootstrap_setup` causes loss of manually manipulated bootstrapping state, leading to CAS timeouts.

---

## Group 8 (Priority 4)

**Status:** [x] **TEST-BUG** - Restarting node with blocked async operation waiting on test-controlled latch

**Execution Count:** 4

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
Caused by: java.util.concurrent.TimeoutException
	at org.apache.cassandra.utils.ExecutorUtils.awaitTerminationUntil(ExecutorUtils.java)
	at org.apache.cassandra.utils.ExecutorUtils.awaitTermination(ExecutorUtils.java)
	at org.apache.cassandra.concurrent.Stage.shutdownAndWait(Stage.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$42(Instance.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$parallelRun$52(Instance.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_first_insert_started
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected.testDynamicContentionTracing(CASContentionTest_RestartInjected.java:118)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.RuntimeException: java.util.concurrent.TimeoutException: org.apache.cassandra.concurrent.SEPExecutor@6ca42584 did not terminate on time
	at org.apache.cassandra.utils.Throwables.maybeFail(Throwables.java:79)
	at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$48(Instance.java:945)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$10(IsolatedExecutor.java:156)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.util.concurrent.TimeoutException: org.apache.cassandra.concurrent.SEPExecutor@6ca42584 did not terminate on time
	at org.apache.cassandra.utils.ExecutorUtils.awaitTerminationUntil(ExecutorUtils.java:111)
	at org.apache.cassandra.utils.ExecutorUtils.awaitTermination(ExecutorUtils.java:100)
	at org.apache.cassandra.concurrent.Stage.shutdownAndWait(Stage.java:193)
	at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$42(Instance.java:915)
	at org.apache.cassandra.distributed.impl.Instance.lambda$parallelRun$52(Instance.java:1162)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected`
- Test Method: `testDynamicContentionTracing`
- Position: `after_first_insert_started`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `005-62d6417a`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected`
- Test Method: `testDynamicContentionTracing`
- Position: `after_second_insert`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `006-e95f24a7`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.QueriesTableTest_RestartInjected`
- Test Method: `shouldExposeReadsAndWrites`
- Position: `after_async_queries`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `051-9491c198`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**
The test creates a circular dependency by attempting to gracefully restart a node while that node has an active async operation blocked on a test-controlled latch that won't be released until after the restart completes.

**Detailed Analysis for CASContentionTest_RestartInjected.testDynamicContentionTracing:**

1. **Message filter setup (lines 86-90):**
   ```java
   THREE_NODES.verbs(PAXOS2_PREPARE_REQ).from(1).messagesMatching((from, to, verb) -> {
       haveStarted.countDown();
       Uninterruptibles.awaitUninterruptibly(haveInvalidated);  // BLOCKS HERE
       return false;
   }).drop();
   ```
   Sets up a message filter that blocks indefinitely waiting for `haveInvalidated` latch when a PAXOS2_PREPARE_REQ message from node 1 is intercepted.

2. **Async operation start (lines 108-111):**
   ```java
   Future<?> insert = THREE_NODES.get(1).async(() -> {
       THREE_NODES.coordinator(1).execute("INSERT INTO " + KEYSPACE + '.' + tableName + " (pk, v) VALUES (1, 1) IF NOT EXISTS", QUORUM);
   }).call();
   haveStarted.await();
   ```
   Starts an async INSERT operation on node 1. This operation triggers the message filter, which counts down `haveStarted` then blocks waiting for `haveInvalidated`.

3. **Restart attempt (lines 113-118):**
   ```java
   RestartFramework.at("after_first_insert_started")
       .on(THREE_NODES)
       .restart("node")
       .withIndex(0)  // Restarts node 1
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```
   Attempts to gracefully restart node 1 while the async operation is still blocked in the message filter callback.

4. **The Deadlock:**
   - Graceful shutdown calls `Stage.shutdownAndWait(1L, MINUTES)` (Instance.java:915)
   - This waits for all executors to terminate with a 1-minute timeout (ExecutorUtils.java:111)
   - But there's a thread blocked in the message filter callback waiting for `haveInvalidated`
   - The `haveInvalidated.countDown()` only occurs at **line 129** - AFTER the restart should have completed
   - Result: Circular dependency causes TimeoutException

**Problematic Test Code:**
- Test file: `test/distributed/org/apache/cassandra/distributed/test/CASContentionTest_RestartInjected.java`
- Lines 86-90: Message filter with blocking latch wait
- Lines 108-111: Async operation that triggers the blocking filter
- Lines 113-118: Restart while operation is blocked
- Line 129: Latch release (too late)

**Why This Is a Test Bug:**
The test design is fundamentally flawed. You cannot gracefully restart a node that has threads blocked on test-controlled synchronization primitives (latches, semaphores, etc.) that require the test to continue executing before they can be released. This creates an impossible circular dependency where:
- The restart cannot complete until the blocked thread finishes
- The blocked thread cannot finish until the latch is released
- The latch cannot be released until the test continues past the restart
- The test cannot continue until the restart completes

**Similar Pattern in Test 3:**
The same pattern likely occurs in `QueriesTableTest_RestartInjected.shouldExposeReadsAndWrites` at position `after_async_queries`, where async queries are started but the node is restarted before they complete, and some may be blocked on test-controlled resources.

**Impact:** All test executions in this group fail with the same pattern - attempting to restart a node that has active operations blocked on resources controlled by the test's main thread.

---

## Group 13 (Priority 4)

**Status:** [x] **TEST-BUG** - StorageMetrics.totalHints counter resets on restart, breaking test assumption

**Execution Count:** 2

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
org.awaitility.core.ConditionTimeoutException
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
	at org.apache.cassandra.distributed.test.AbstractHintWindowTest.insertData(AbstractHintWindowTest.java)
```

### Raw Stack Trace Sample
```
org.awaitility.core.ConditionTimeoutException: Condition with org.apache.cassandra.distributed.test.AbstractHintWindowTest was not fulfilled within 10 seconds.
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:165)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:78)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:26)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:895)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:864)
	at org.apache.cassandra.distributed.test.AbstractHintWindowTest.insertData(AbstractHintWindowTest.java:122)
	at org.apache.cassandra.distributed.test.HintsPersistentWindowTest_RestartInjected.testPersistentHintWindow(HintsPersistentWindowTest_RestartInjected.java:165)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.HintsPersistentWindowTest_RestartInjected`
- Test Method: `testPersistentHintWindow`
- Position: `after_node_startup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `009-b5edb33b`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.HintsPersistentWindowTest_RestartInjected`
- Test Method: `testPersistentHintWindow`
- Position: `after_hints_size_assertion`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `010-11b54bd5`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**

The test has a faulty assumption about the persistence of the `StorageMetrics.totalHints` metric across node restarts. The test flow:

1. **First insertData call (line 90):** Creates hints when node2 is down, `StorageMetrics.totalHints` increases to ~50001
2. **pauseHintsDelivery (line 110):** Pauses hints dispatch (but not creation)
3. **Wait 60 seconds (line 120):** Exceeds max_hint_window (30s)
4. **Node2 starts up (line 132):** Node2 comes back online
5. **RESTART at "after_node_startup" (line 135-140):** Node1 (index 0) is restarted
6. **Node2 shuts down again (line 156):** Node2 goes down second time
7. **Second insertData call (line 165):** Expects to create hints, but times out

**Why the test fails with restart:**

After the restart, three things happen:
1. **Old hints persist on disk:** 6.5MB of hints from the first insertData remain on disk
2. **Persistent hint window prevents new hints:** Cassandra's persistent hint window feature detects the old hints (>60s old, exceeding the 30s max_hint_window) and refuses to create NEW hints
3. **StorageMetrics.totalHints resets to 0:** The metric tracks hints created since node startup, not total hints on disk, so it resets to 0 after restart

The `insertData` method at line 122 of AbstractHintWindowTest.java waits for:
```java
await().until(() -> cluster.get(1).callOnInstance(() -> StorageMetrics.totalHints.getCount()) > 0);
```

Since no new hints are created (persistent window prevents it) and the counter was reset, this await times out.

**Why the test would pass without restart:**

Without the restart:
- `StorageMetrics.totalHints` would still be ~50001 from the first insertData
- The await would succeed immediately (50001 > 0)
- Even though no NEW hints are created in the second insertData, the test would pass
- Line 175 would compare 50001 == 50001 and pass

**Buggy Test Code:**

AbstractHintWindowTest.java:122
```java
await().until(() -> cluster.get(1).callOnInstance(() -> StorageMetrics.totalHints.getCount()) > 0);
```

This incorrectly assumes `totalHints` persists across restarts or that new hints will always be created.

**Potential Fix:**

The `insertData` method should either:
1. Check for existing hints on disk instead of relying solely on the `totalHints` counter
2. Accept a parameter indicating whether new hints are expected to be created
3. Check both `totalHints > 0 OR getTotalHintsSize() > 0` to account for both new hints and existing hints on disk

Example fix for AbstractHintWindowTest.java:122:
```java
// Instead of just checking totalHints counter:
await().until(() -> cluster.get(1).callOnInstance(() -> StorageMetrics.totalHints.getCount()) > 0);

// Check if hints exist (either new or on disk):
await().until(() -> {
    long totalHints = cluster.get(1).callOnInstance(() -> StorageMetrics.totalHints.getCount());
    if (totalHints > 0) return true;

    // If no new hints created, check for existing hints on disk
    UUID node2UUID = cluster.get(2).callOnInstance(() ->
        org.apache.cassandra.service.StorageService.instance.getLocalHostUUID());
    long hintsOnDisk = cluster.get(1).appliesOnInstance(
        (IIsolatedExecutor.SerializableFunction<UUID, Long>) secondNode ->
            HintsService.instance.getTotalHintsSize(secondNode)
    ).apply(node2UUID);
    return hintsOnDisk > 0;
});
```

---

## Group 15 (Priority 4)

**Status:** [x] **FP** - Snapshot TTL expires during restart delay

**Execution Count:** 2

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
org.awaitility.core.ConditionTimeoutException
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
```

### Raw Stack Trace Sample
```
org.awaitility.core.ConditionTimeoutException: Condition with org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected was not fulfilled within 20 seconds.
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:165)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:78)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:26)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:895)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:864)
	at org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected.waitForSnapshot(SnapshotsTest_RestartInjected.java:358)
	at org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected.waitForSnapshotPresent(SnapshotsTest_RestartInjected.java:345)
	at org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected.testSecondaryIndexCleanup(SnapshotsTest_RestartInjected.java:206)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected`
- Test Method: `testSecondaryIndexCleanup`
- Position: `after_snapshot`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `048-0453f216`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected`
- Test Method: `testSnapshotsCleanupByTTL`
- Position: `after_snapshot`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `061-cb80856f`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** FP (False Positive)

**Root Cause:**
The test creates a snapshot with a very short TTL (5 seconds) and immediately restarts the node at the "after_snapshot" position. The restart operation takes approximately the same amount of time as the TTL, causing the snapshot to expire during the restart. When the node completes its restart, it correctly cleans up the expired snapshot, but the test expects the snapshot to still be present.

**Detailed Timeline from Debug Logs:**
```
19:59:02.371 - Snapshot "first" created with TTL=5s, expiresAt=19:59:07.371
19:59:02.477 - Restart begins (position: after_snapshot)
19:59:07.406 - Node startup: Snapshot reloaded from disk with same expiresAt=19:59:07.371
19:59:07.408 - SnapshotCleanup thread: "Removing expired snapshot" (current time > expiresAt)
19:59:07.518 - Restart completes (took 5.042 seconds)
19:59:07.541 - Test checks for snapshot: "There are no snapshots" (snapshot already cleaned up)
```

**Why This Is Not a Bug:**

1. **Correct TTL cleanup behavior**: Cassandra's snapshot cleanup mechanism is working exactly as designed. When a node restarts, it loads snapshot metadata from disk (including expiration times) and the SnapshotCleanup background thread immediately removes any snapshots that have already passed their expiration time.

2. **Restart timing conflict**: The restart injection adds ~5 seconds of delay, which happens to match the snapshot TTL of 5 seconds. This creates a race condition where the snapshot expires during the restart operation.

3. **Test timing assumption violated**: The original test (without restart injection) assumes the snapshot check happens immediately (within milliseconds) after snapshot creation. The restart injection violates this assumption by adding significant delay.

4. **Semantically correct in production**: In a real deployment scenario, if a node with an expiring snapshot restarts and comes back up after the snapshot's TTL has expired, it is absolutely correct for that snapshot to be cleaned up immediately upon startup. The snapshot was meant to expire at a specific time, and the cleanup mechanism honored that contract.

**Relevant Source Code:**
- Line 201-204 in SnapshotsTest_RestartInjected.java: Creates snapshot with 5-second TTL
- Line 205: Restart injected at "after_snapshot" position
- Line 206: Test expects snapshot to be present (fails because it expired during restart)

**SnapshotCleanup mechanism (observed in logs):**
```
DEBUG [node1_SnapshotCleanup:1] node1 2025-12-17 19:59:07,408 SnapshotManager.java:151 -
Removing expired snapshot TableSnapshot{keyspaceName='distributed_test_keyspace',
tableName='tbl', tag='first', createdAt=2025-12-18T01:59:02.371Z,
expiresAt=2025-12-18T01:59:07.371Z, ...}
```

**Conclusion:**
This is a false positive caused by the restart injection creating a timing conflict with the snapshot's short TTL. The Cassandra code is behaving correctly - the snapshot expired and was properly cleaned up. The restart position "after_snapshot" is semantically problematic when the snapshot has a TTL comparable to the restart duration.

---

## Group 19 (Priority 4)

**Status:** [x] **FP** - Restart disrupts asynchronous streaming operation before it can be established

**Execution Count:** 1

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
Caused by: java.util.concurrent.TimeoutException
	at java.base/java.util.concurrent.FutureTask.get(FutureTask.java)
	at org.awaitility.core.Uninterruptibles.getUninterruptibly(Uninterruptibles.java)
	at org.awaitility.core.Uninterruptibles.getUninterruptibly(Uninterruptibles.java)
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
```

### Raw Stack Trace Sample
```
org.awaitility.core.ConditionTimeoutException: Condition with alias 'Did not see stream running or timed out' didn't complete within 3 minutes because condition with org.apache.cassandra.distributed.test.streaming.StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected was not fulfilled.
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:165)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:78)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:26)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:895)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:864)
	at org.apache.cassandra.distributed.test.streaming.StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected.failureDueToSessionTimeout(StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected.java:87)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.util.concurrent.TimeoutException
	at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:204)
	at org.awaitility.core.Uninterruptibles.getUninterruptibly(Uninterruptibles.java:101)
	at org.awaitility.core.Uninterruptibles.getUninterruptibly(Uninterruptibles.java:81)
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:101)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.streaming.StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected`
- Test Method: `failureDueToSessionTimeout`
- Position: `after_trigger_streaming`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `036-63709395`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** FP (False Positive)

**Root Cause:**

The restart position `after_trigger_streaming` creates a race condition that prevents the test from reaching its intended scenario. The restart injection happens at a semantically improper point that disrupts the asynchronous streaming operation before it can be established.

**Test Flow Analysis:**

```java
// Line 74: Start streaming asynchronously in background thread
ForkJoinPool.commonPool().execute(() -> triggerStreaming(cluster));

// Line 76-81: Immediately restart node 1 (index 0) - THE SOURCE NODE
RestartFramework.at("after_trigger_streaming")
    .on(cluster)
    .restart("node")
    .withIndex(0)  // Node 1 is the streaming SOURCE
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Line 85-87: Wait for either stream to start OR session timeout
Awaitility.await("Did not see stream running or timed out")
          .atMost(3, TimeUnit.MINUTES)
          .until(() => State.STREAM_IS_RUNNING.await(false) ||
                      searchForLog(cluster.get(1), false, "Session timed out"));
```

**The Problem:**

1. **Background Thread**: `triggerStreaming(cluster)` is executed asynchronously via `ForkJoinPool.commonPool()`. This method calls:
   ```java
   // From AbstractStreamFailureLogs.java:91
   node2.nodetoolResult("repair", "-full", KEYSPACE, "tbl").asserts().failure();
   ```
   This is a **blocking** call that initiates repair on node 2, which needs to stream data FROM node 1.

2. **Immediate Restart**: Right after starting the background thread (before the repair can establish a streaming session), the test restarts **node 1 at index 0** - the very node that node 2 needs to stream data from.

3. **Timing Issue**: The sequence is:
   - Background thread starts and begins initiating repair on node 2
   - Before the streaming session can be negotiated/established between nodes
   - Node 1 (the source) is restarted
   - Node 2's repair operation is now waiting for a node that is down/restarting
   - The streaming never reaches the point where ByteBuddy hooks would signal `STREAM_IS_RUNNING`
   - No "Session timed out" message is logged in the expected format
   - Test times out waiting for conditions that will never be met

4. **Why This is a False Positive:**
   - The test is designed to verify that streaming failures due to session timeouts are properly logged
   - The test uses ByteBuddy to intercept streaming operations and artificially block them to trigger a timeout
   - However, the restart injection prevents the streaming from ever reaching the interception point
   - The failure is not due to Cassandra code behaving incorrectly, but due to the restart position disrupting the test's preconditions

**Conclusion:**

This is a false positive caused by the restart injection at position `after_trigger_streaming`. The restart happens immediately after an asynchronous streaming operation is initiated but before the streaming session can be established. Restarting the source node (node 1) at this critical point prevents the streaming from progressing to the state where the test's intended scenario (session timeout during data transfer) can occur. The Cassandra code is behaving correctly - it simply cannot establish a streaming session when the source node is unavailable.

---

## Group 22 (Priority 4)

**Status:** [x] **TEST-BUG** - Stale LogAction reference after node restart

**Execution Count:** 1

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
Caused by: java.util.concurrent.TimeoutException
		at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java)
		at org.apache.cassandra.utils.FBUtilities.waitOnFutures(FBUtilities.java)
```

### Raw Stack Trace Sample
```
java.lang.IllegalStateException: Can't load org.apache.cassandra.distributed.impl.FileLogAction$FileLineIterator. Instance class loader is already closed.
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClassInternal(InstanceClassLoader.java:118)
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClass(InstanceClassLoader.java:112)
	at org.apache.cassandra.distributed.impl.FileLogAction.match(FileLogAction.java:58)
	at org.apache.cassandra.distributed.api.LogAction.grep(LogAction.java:170)
	at org.apache.cassandra.distributed.api.LogAction.grep(LogAction.java:208)
	at org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected.upgradeSSTablesInterruptsOngoingCompaction(UpgradeSSTablesTest_RestartInjected.java:119)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	Suppressed: java.lang.RuntimeException: java.util.concurrent.TimeoutException
		at org.apache.cassandra.utils.Throwables.maybeFail(Throwables.java:79)
		at org.apache.cassandra.utils.FBUtilities.waitOnFutures(FBUtilities.java:543)
		at org.apache.cassandra.distributed.impl.AbstractCluster.close(AbstractCluster.java:1089)
		at org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected.upgradeSSTablesInterruptsOngoingCompaction(UpgradeSSTablesTest_RestartInjected.java:59)
	Caused by: java.util.concurrent.TimeoutException
		at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java:253)
		at org.apache.cassandra.utils.FBUtilities.waitOnFutures(FBUtilities.java:535)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected`
- Test Method: `upgradeSSTablesInterruptsOngoingCompaction`
- Position: `after_upgradesstables`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `056-e90052ff`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**
The test code stores a reference to a `LogAction` object before a node restart and tries to use it after the restart, but the LogAction becomes invalid because it references a closed InstanceClassLoader.

**Buggy Code Location:**
`test/distributed/org/apache/cassandra/distributed/test/UpgradeSSTablesTest_RestartInjected.java`

1. **Line 98:** Obtains LogAction reference BEFORE restart
   ```java
   LogAction logAction = cluster.get(1).logs();
   ```

2. **Lines 111-116:** Node restart occurs
   ```java
   RestartFramework.at("after_upgradesstables")
       .on(cluster)
       .restart("node")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

3. **Line 119:** Attempts to use stale LogAction AFTER restart
   ```java
   Assert.assertFalse(logAction.grep("Compaction interrupted").getResult().isEmpty());
   ```

**Why It Fails:**
When a node is restarted in the distributed test framework:
- The Instance object is recreated with a new InstanceClassLoader
- The old Instance and its InstanceClassLoader are closed/discarded
- The `LogAction` object obtained before restart still references the old (now closed) InstanceClassLoader
- Attempting to use this stale LogAction triggers: `IllegalStateException: Can't load org.apache.cassandra.distributed.impl.FileLogAction$FileLineIterator. Instance class loader is already closed.`

**Potential Fix:**
Obtain a fresh LogAction reference after the restart:
```java
// Line 111-116: Restart happens
RestartFramework.at("after_upgradesstables")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Line 118: Complete the async compaction
future.get();

// FIX: Get fresh LogAction after restart instead of using stale reference
LogAction logActionAfterRestart = cluster.get(1).logs();
Assert.assertFalse(logActionAfterRestart.grep("Compaction interrupted").getResult().isEmpty());
```

**Note on Suppressed TimeoutException:**
The suppressed `TimeoutException` during cluster shutdown is a side effect of the main error. When the test fails with IllegalStateException, the cluster cleanup also fails because compactions cannot complete properly.

---

## Group 25 (Priority 4)

**Status:** [x] **FP** - Datastax driver reconnection delay after node restart

**Execution Count:** 1

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
Caused by: com.datastax.driver.core.exceptions.WriteTimeoutException
	at com.datastax.driver.core.Responses$Error$1.decode(Responses.java)
	at com.datastax.driver.core.Responses$Error$1.decode(Responses.java)
	at com.datastax.driver.core.Message$ProtocolDecoder.decode(Message.java)
	at com.datastax.driver.core.Message$ProtocolDecoder.decode(Message.java)
	at com.datastax.shaded.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java)
```

### Raw Stack Trace Sample
```
com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency LOCAL_ONE (1 replica were required but only 0 acknowledged the write)
	at com.datastax.driver.core.exceptions.WriteTimeoutException.copy(WriteTimeoutException.java:87)
	at com.datastax.driver.core.exceptions.WriteTimeoutException.copy(WriteTimeoutException.java:25)
	at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
	at com.datastax.driver.core.DefaultResultSetFuture.getUninterruptibly(DefaultResultSetFuture.java:295)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:60)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:41)
	at org.apache.cassandra.distributed.test.QueriesTableTest_RestartInjected.shouldExposeReadsAndWrites(QueriesTableTest_RestartInjected.java:125)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency LOCAL_ONE (1 replica were required but only 0 acknowledged the write)
	at com.datastax.driver.core.exceptions.WriteTimeoutException.copy(WriteTimeoutException.java:115)
	at com.datastax.driver.core.Responses$Error.asException(Responses.java:177)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.onSet(RequestHandler.java:653)
	at com.datastax.driver.core.Connection$Dispatcher.channelRead0(Connection.java:1292)
	at com.datastax.driver.core.Connection$Dispatcher.channelRead0(Connection.java:1210)
	at com.datastax.shaded.netty.channel.SimpleChannelInboundHandler.channelRead(SimpleChannelInboundHandler.java:99)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.handler.timeout.IdleStateHandler.channelRead(IdleStateHandler.java:286)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:442)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.handler.codec.ByteToMessageDecoder.fireChannelRead(ByteToMessageDecoder.java:346)
	at com.datastax.shaded.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:318)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.channel.ChannelInboundHandlerAdapter.channelRead(ChannelInboundHandlerAdapter.java:93)
	at com.datastax.driver.core.InboundTrafficMeter.channelRead(InboundTrafficMeter.java:40)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1410)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:440)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:919)
	at com.datastax.shaded.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:166)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:788)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:724)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:650)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562)
	at com.datastax.shaded.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
	at com.datastax.shaded.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
	at com.datastax.shaded.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency LOCAL_ONE (1 replica were required but only 0 acknowledged the write)
	at com.datastax.driver.core.Responses$Error$1.decode(Responses.java:91)
	at com.datastax.driver.core.Responses$Error$1.decode(Responses.java:69)
	at com.datastax.driver.core.Message$ProtocolDecoder.decode(Message.java:299)
	at com.datastax.driver.core.Message$ProtocolDecoder.decode(Message.java:270)
	at com.datastax.shaded.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:88)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.QueriesTableTest_RestartInjected`
- Test Method: `shouldExposeReadsAndWrites`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `050-86bc4cdf`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** FP (False Positive)

**Root Cause:**

The failure is caused by a Datastax driver reconnection delay after node restart. When the restart adapter restarts the node, all external client connections are terminated. The Datastax driver takes ~13 seconds to automatically detect the disconnection and reconnect. The test tries to execute queries immediately after restart returns (before driver reconnection completes), causing WriteTimeoutException.

**Detailed Analysis:**

1. **Test Setup and Flow:**
   - Single-node cluster with NATIVE_PROTOCOL and GOSSIP features enabled (QueriesTableTest_RestartInjected.java:61-62)
   - Uses Datastax driver to connect to the cluster (line 63-64)
   - Creates table, then restarts node at position `after_table_create` (line 83-90)
   - Immediately tries to execute async INSERT and SELECT queries (line 97-98)
   - Fails with: `WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency LOCAL_ONE (1 replica were required but only 0 acknowledged the write)`

2. **Debug Evidence:**

   Added debug logging to check driver connection state after restart:

   ```
   DEBUG: After restart, checking driver connection...
   DEBUG: Driver hosts count: 1
   DEBUG:   Host /127.0.0.1 - IsUp: false
   DEBUG: Waiting for driver to reconnect (max 15 seconds)...
   DEBUG: All hosts UP after 13000ms
   ```

   Key findings:
   - Immediately after restart: Driver reports host as `IsUp: false`
   - Driver takes **13 seconds** to automatically reconnect
   - After reconnection completes, test passes successfully

3. **Why WriteTimeoutException Occurs:**

   In a single-node cluster with consistency LOCAL_ONE, the write should be acknowledged by the only node. However:

   - **Line 85-90:** Restart adapter restarts the node, terminating all client connections
   - **Line 97:** Test immediately tries `SESSION.executeAsync("INSERT ...")`
   - **Driver state:** Connection is down (`IsUp: false`), driver hasn't reconnected yet
   - **Result:** Query cannot reach the server → timeout → WriteTimeoutException with "0 acknowledged the write"

   From the logs:
   ```
   ERROR [Control connection] Cannot connect to any host, scheduling retry in 32000 milliseconds
   DEBUG [cluster1-reconnection-1] Successful reconnection to /127.0.0.1:9042, setting host UP
   DEBUG [cluster1-reconnection-1] [/127.0.0.1:9042] marking host UP
   ```

4. **Comparison to Group 7:**

   This is similar to Group 7 (RPC readiness issue) but manifests differently:
   - **Group 7:** Multi-node cluster where internal coordinators fail because other nodes don't see the restarted node as RPC ready via gossip (race condition in gossip propagation)
   - **Group 25:** Single-node cluster where external Datastax driver fails because the driver connection was terminated during restart and hasn't reconnected yet (driver reconnection delay)

   Both share the same underlying issue: **restart adapter returns control before the system is fully ready to accept requests**

**Why This is a False Positive:**

This is classified as **FP** because:

1. **Not a Source Code Bug:** Cassandra is working correctly. The node successfully restarted and is ready to accept connections. The native transport is listening and functional.

2. **Not a Test Bug:** The test code is reasonable for normal operations. It's testing the system_views.queries table after a restart, which is a valid test scenario.

3. **Restart Adapter Limitation:** The issue is in how the restart adapter interacts with external client drivers:
   - The restart adapter's `performGracefulRestart()` waits for the node to rejoin the ring using `ClusterUtils.awaitRingJoin()`
   - This ensures the node is "Up" and "Normal" in the ring
   - However, it does NOT wait for external client driver connections to be re-established
   - When restart returns, the driver is still disconnected and hasn't started reconnection

4. **Unrealistic Test Timing:** In production scenarios:
   - There would naturally be time between a node restart completing and client operations resuming
   - Client applications typically implement retry logic and connection pooling
   - The restart framework creates an artificially tight timing window where the test tries to use the driver immediately after restart, before the driver's automatic reconnection logic has completed

**Proper Fix:**

The restart adapter could be enhanced to wait for external client connections to be re-established, but this is complex because:
- The adapter would need to track which clients were connected before restart
- Different drivers have different reconnection strategies and timing
- In distributed tests, this is an artificial constraint that doesn't reflect real-world scenarios

A simpler approach for tests using external drivers would be to add a small delay or connection check after restart before executing queries. The test passes when we add a wait loop:

```java
// Wait for driver to reconnect after restart
int attempts = 0;
while (attempts < 30) {
    boolean allUp = true;
    for (Host host : DRIVER_CLUSTER.getMetadata().getAllHosts()) {
        if (!host.isUp()) {
            allUp = false;
            break;
        }
    }
    if (allUp) break;
    Thread.sleep(500);
    attempts++;
}
```

With this change, the test passes reliably because it waits for the driver to complete its reconnection (which takes ~13 seconds) before executing queries.

---

## Group 26 (Priority 4)

**Status:** [x] **BUG** - Race condition in asynchronous snapshot creation during graceful shutdown

**Execution Count:** 1

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
org.awaitility.core.ConditionTimeoutException
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
	at org.awaitility.core.AbstractHamcrestCondition.await(AbstractHamcrestCondition.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
```

### Raw Stack Trace Sample
```
org.awaitility.core.ConditionTimeoutException: org.apache.cassandra.distributed.test.PreviewRepairSnapshotTest_RestartInjected expected not an empty string but was "" within 1 minutes.
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:165)
	at org.awaitility.core.AbstractHamcrestCondition.await(AbstractHamcrestCondition.java:86)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:895)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:601)
	at org.apache.cassandra.distributed.test.PreviewRepairSnapshotTest_RestartInjected.lambda$checkSnapshot$d36cbfdb$1(PreviewRepairSnapshotTest_RestartInjected.java:189)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.PreviewRepairSnapshotTest_RestartInjected`
- Test Method: `testSnapshotOfSStablesContainingMismatchingTokens`
- Position: `after_final_repair`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `009-9c8284de`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** BUG

**Root Cause:**

This is a race condition bug in Cassandra's asynchronous diagnostic snapshot creation that manifests during graceful node shutdown.

**Execution Flow:**

1. **Repair triggers snapshot** (line 137-139 in PreviewRepairTask.java):
   - `DiagnosticSnapshotService.repairedDataMismatch(...)` is called
   - This is a fire-and-forget asynchronous operation

2. **Snapshot request handling** (SnapshotVerbHandler.java):
   - Line 54: `DiagnosticSnapshotService.snapshot(...)` enqueues snapshot task
   - Line 160 (DiagnosticSnapshotService.java): `executor.execute(new DiagnosticSnapshotTask(...))` - asynchronous
   - Line 62 (SnapshotVerbHandler.java): Response sent **immediately before snapshot completes** ❌

3. **Race with restart**:
   - Repair command returns to test (snapshot still pending in background)
   - Restart injection triggers immediately (only ~2ms later based on timestamps)
   - Graceful shutdown begins while DiagnosticSnapshotTask is running

4. **Snapshot corruption**:
   - Exception thrown during snapshot creation: `ERROR uncaught exception in thread Thread[node1_DiagnosticSnapshot:1,5,DiagnosticSnapshot] at DiagnosticSnapshotService$DiagnosticSnapshotTask.run:211`
   - Snapshot directory created but manifest.json is empty/corrupted
   - After restart, `listSnapshots()` returns the snapshot tag but reading fails
   - Test times out waiting for valid snapshot with schema file

**Buggy Code Locations:**

1. **src/java/org/apache/cassandra/service/SnapshotVerbHandler.java:54-62**
```java
// Line 54: Enqueue snapshot task (asynchronous)
DiagnosticSnapshotService.snapshot(command, ranges, message.from());

// ... more code ...

// Line 62: Send response BEFORE snapshot completes - WRONG!
logger.debug("Enqueuing response to snapshot request {} to {}", command.snapshot_name, message.from());
MessagingService.instance().send(message.emptyResponse(), message.from());
```
The handler sends a response before the snapshot is created, violating request-response semantics.

2. **src/java/org/apache/cassandra/utils/DiagnosticSnapshotService.java:160**
```java
private void maybeSnapshot(SnapshotCommand command, List<Range<Token>> ranges, InetAddressAndPort initiator)
{
    executor.execute(new DiagnosticSnapshotTask(command, ranges, initiator));  // Asynchronous execution
}
```
Snapshot creation is asynchronous with no mechanism to await completion.

3. **src/java/org/apache/cassandra/repair/PreviewRepairTask.java:137-139**
```java
DiagnosticSnapshotService.repairedDataMismatch(Keyspace.open(keyspace).getColumnFamilyStore(table).metadata(),
                                               nodes,
                                               normalizedRanges);
// Repair continues without waiting for snapshot completion
```
The repair doesn't wait for snapshot creation to complete.

**Impact:**

- **Production Impact:** If a node is gracefully restarted shortly after a repair that detects data mismatches, diagnostic snapshots will be corrupted
- **Data Loss Risk:** Corrupted snapshots cannot be used for forensic analysis of data mismatches
- **Operations Impact:** Silent corruption - the snapshot appears to exist but is unusable

**Potential Fixes:**

1. **Make snapshot creation synchronous** (preferred for diagnostic snapshots):
   - Execute DiagnosticSnapshotTask synchronously in SnapshotVerbHandler
   - Send response only after snapshot completes

2. **Add proper coordination**:
   - Make DiagnosticSnapshotService.snapshot() return a Future
   - Wait for completion before sending response

3. **Improve shutdown handling**:
   - Clean up incomplete snapshots during shutdown (detect empty manifest.json)
   - Or ensure DiagnosticSnapshot executor completes before data directories are accessed during shutdown

4. **Make checkSnapshot more robust**:
   - Detect and delete corrupted snapshots (empty manifest.json)
   - Retry snapshot creation if corruption detected

---

## Group 11 (Priority 5)

**Status:** [x] **FP** - Restart injection disrupts generation-based ByteBuddy interceptor logic

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
java.lang.RuntimeException
	at org.apache.cassandra.distributed.test.DecommissionTest$BB.prepareUnbootstrapStreaming(DecommissionTest.java)
	at org.apache.cassandra.service.StorageService.prepareUnbootstrapStreaming(StorageService.java)
	at org.apache.cassandra.service.StorageService.unbootstrap(StorageService.java)
	at org.apache.cassandra.service.StorageService.decommission(StorageService.java)
```

### Raw Stack Trace Sample
```
java.lang.RuntimeException: simulated error in prepareUnbootstrapStreaming
	at org.apache.cassandra.distributed.test.DecommissionTest$BB.prepareUnbootstrapStreaming(DecommissionTest.java:208)
	at org.apache.cassandra.service.StorageService.prepareUnbootstrapStreaming(StorageService.java)
	at org.apache.cassandra.service.StorageService.unbootstrap(StorageService.java:5427)
	at org.apache.cassandra.service.StorageService.decommission(StorageService.java:5351)
	at org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected.lambda$testDecommissionAfterNodeRestart$81c80a4a$2(DecommissionTest_RestartInjected.java:192)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected`
- Test Method: `testDecommissionAfterNodeRestart`
- Position: `after_failed_decommission`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `043-a7a8df70`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected`
- Test Method: `testDecommissionAfterNodeRestart`
- Position: `after_manual_restart`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `044-5be8ad0d`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** FP (False Positive)

**Root Cause:**
The restart injection disrupts the test's generation-based logic for controlling when the ByteBuddy (BB) interceptor is installed. This test uses a carefully orchestrated scenario where:

1. **Test Design:** The test simulates a decommission failure using a ByteBuddy interceptor that throws an exception on the first invocation. The instance initializer conditionally installs the BB interceptor based on generation:
   ```java
   // Lines 139-145 in DecommissionTest_RestartInjected.java
   .withInstanceInitializer((classLoader, threadGroup, num, generation) -> {
       // we do not want to install BB after restart of a node which
       // failed to decommission which is the second generation, here
       // as "1" as it is counted from 0.
       if (num == 1 && generation != 1)
           BB.install(classLoader, num);
   })
   ```

2. **Expected Flow (without restart injection):**
   - Generation 0: BB installed (generation 0 != 1), first decommission fails with "simulated error"
   - Manual restart (line 176-177): Generation 1, BB NOT installed (generation 1 == 1)
   - Second decommission (line 192): Succeeds because BB is not installed

3. **Actual Flow (with restart injection at `after_failed_decommission`):**
   - Generation 0: BB installed (generation 0 != 1), first decommission fails with "simulated error"
   - **Restart framework restart (line 169-174): Generation 1, BB NOT installed (generation 1 == 1)**
   - Manual restart (line 176-177): **Generation 2**, BB **IS installed** (generation 2 != 1)
     - New classloader means BB.invocations static counter resets to 0
   - Second decommission (line 192): **BB intercepts again, invocations=1, throws "simulated error" (unexpected!)**

4. **Why This Is a False Positive:**
   The restart injection causes an extra generation increment, shifting the generation count from the expected value. The test's logic explicitly depends on generation numbers to control test behavior (when to install BB), but the restart framework's injection consumes generation 1, forcing the manual restart to become generation 2. This causes the BB interceptor to be installed when the test expects it to be absent, leading to an unexpected failure of the second decommission attempt.

**Relevant Code:**
- Test file: `test/distributed/org/apache/cassandra/distributed/test/DecommissionTest_RestartInjected.java`
- Instance initializer (lines 139-145): Conditionally installs BB based on generation
- BB interceptor (lines 213-243): Static invocation counter, throws on first call
- Failure point (line 192): Second decommission attempt that should succeed

---

## Group 12 (Priority 5)

**Status:** [x] **FP** - Restart closes active connections, causing in-flight async queries to fail

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
Caused by: com.datastax.driver.core.exceptions.TransportException
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java)
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java)
	at com.datastax.driver.core.Connection.defunct(Connection.java)
	at com.datastax.driver.core.Connection$Dispatcher.exceptionCaught(Connection.java)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireExceptionCaught(AbstractChannelHandlerContext.java)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline$HeadContext.exceptionCaught(DefaultChannelPipeline.java)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.ExecutionException: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
	at java.base/java.util.concurrent.CompletableFuture.reportGet(CompletableFuture.java:396)
	at java.base/java.util.concurrent.CompletableFuture.get(CompletableFuture.java:2073)
	at org.apache.cassandra.distributed.test.OverloadTest_RestartInjected.testApplyClientBackpressure(OverloadTest_RestartInjected.java:154)
	at org.apache.cassandra.distributed.test.OverloadTest_RestartInjected.applyClientBackpressure(OverloadTest_RestartInjected.java:72)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
	at com.datastax.driver.core.exceptions.TransportException.copy(TransportException.java:40)
	at com.datastax.driver.core.exceptions.TransportException.copy(TransportException.java:26)
	at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
	at com.datastax.driver.core.DefaultResultSetFuture.getUninterruptibly(DefaultResultSetFuture.java:295)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:60)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:41)
	at org.apache.cassandra.distributed.test.OverloadTest_RestartInjected.lambda$testApplyClientBackpressure$1(OverloadTest_RestartInjected.java:133)
	at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(CompletableFuture.java:1768)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java:1460)
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java:1441)
	at com.datastax.driver.core.Connection.defunct(Connection.java:624)
	at com.datastax.driver.core.Connection$Dispatcher.exceptionCaught(Connection.java:1363)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java:346)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java:325)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireExceptionCaught(AbstractChannelHandlerContext.java:317)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline$HeadContext.exceptionCaught(DefaultChannelPipeline.java:1377)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java:346)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java:325)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline.fireExceptionCaught(DefaultChannelPipeline.java:907)
	at com.datastax.shaded.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.handleReadException(AbstractNioByteChannel.java:125)
	at com.datastax.shaded.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:177)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:788)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:724)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:650)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562)
	at com.datastax.shaded.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
	at com.datastax.shaded.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
	at com.datastax.shaded.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.OverloadTest_RestartInjected`
- Test Method: `applyClientBackpressure`
- Position: `after_queries_submitted`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `032-4a5e2787`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.OverloadTest_RestartInjected`
- Test Method: `clientBackpressureDisabled`
- Position: `after_queries_submitted`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `032-263631b1`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** FP (False Positive)

**Root Cause:**

The failure occurs because the restart injection point `after_queries_submitted` closes active DataStax driver connections while async queries are in-flight, causing those queries to fail with TransportException. This is an incompatible restart position for tests that rely on async operations completing over persistent connections.

**Detailed Analysis:**

1. **Test Purpose and Flow:**

   The test validates client backpressure behavior under server overload conditions (OverloadTest_RestartInjected.java:75-199):

   - **Lines 77-89:** Creates a 1-node cluster with ByteBuddy instrumentation (SlowSelect) that can slow down SELECT queries to 1100ms
   - **Lines 100-104:** Creates DataStax driver cluster and session (loops through ProtocolV4 and V5)
   - **Lines 110-121:** Executes initial query, then enables SlowSelect to make subsequent queries slow
   - **Lines 130-138:** Submits **256 async queries** (128 + sleep 700ms + 128 more) via CompletableFuture
   - **Lines 140-145:** **RESTART INJECTION at `after_queries_submitted`** - THIS IS WHERE THE PROBLEM OCCURS
   - **Lines 150-166:** Calls `future.get()` on all 256 futures to retrieve results
   - **Lines 158-165:** Exception handling - catches OperationTimedOutException, ReadTimeoutException, and OverloadedException, but **re-throws any other exception** (including TransportException)

2. **Why TransportException Occurs:**

   When the restart happens at `after_queries_submitted`:

   - The test has already submitted 256 async queries that are being executed (or queued for execution)
   - Each query is tied to a CompletableFuture that expects to receive results over the active DataStax driver connection
   - The GRACEFUL restart closes all client connections (native transport port 9042)
   - The DataStax driver detects the connection closure and marks in-flight requests as failed
   - The CompletableFutures receive TransportException: "[/127.0.0.1:9042] Connection has been closed"
   - When the test calls `future.get()` at line 154, it receives ExecutionException wrapping TransportException
   - Since TransportException is not in the expected exception types, the test re-throws it (line 165)

3. **Key Code Locations:**

   **OverloadTest_RestartInjected.java:133:**
   ```java
   futures.add(CompletableFuture.supplyAsync(() -> session.execute("select * from tbl").one(), executor));
   ```
   Creates async queries that depend on active session connections.

   **OverloadTest_RestartInjected.java:140-145:**
   ```java
   RestartFramework.at("after_queries_submitted")
       .on(control)
       .restart("node")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```
   Restart injection closes all active connections.

   **OverloadTest_RestartInjected.java:154-165:**
   ```java
   future.get();  // Fails with ExecutionException(TransportException)
   success++;
   } catch (ExecutionException e) {
       if (e.getCause() instanceof OperationTimedOutException)
           timedOut++;
       else if (e.getCause() instanceof ReadTimeoutException)
           timedOut++;
       else if (e.getCause() instanceof OverloadedException)
           overloaded++;
       else throw e;  // TransportException is NOT handled, so it's re-thrown
   }
   ```

4. **Why This is FP:**

   - **Incompatible restart position:** The restart injection occurs after async queries are submitted but before they complete. The test's design requires those queries to complete over persistent connections.

   - **DataStax driver behavior is correct:** When the server closes connections during restart, the driver correctly reports TransportException for in-flight requests. This is standard connection lifecycle behavior.

   - **Not a Cassandra bug:** Cassandra correctly closes connections during a graceful restart. This is expected behavior.

   - **Not a test bug:** The original test (without restart injection) is correctly designed to test client backpressure. It doesn't need to handle TransportException because connections don't suddenly close during normal operation.

   - **Similar to Groups 2, 6, 25:** All were classified as FP due to DataStax driver connection closure/reconnection issues after restart.

5. **Why GRACEFUL Restart Doesn't Help:**

   Even though the restart mode is GRACEFUL, the queries still fail because:
   - There are 256 slow queries (each takes 1100ms when SlowSelect is enabled)
   - A GRACEFUL shutdown may not wait for all in-flight queries to complete, especially with large numbers of slow queries
   - The restart adapter returns control to the test before the DataStax driver has reconnected
   - The futures that were created with the old connection cannot automatically migrate to a new connection

**Conclusion:**

This is a false positive caused by the restart framework injecting a restart at an inappropriate point. The test is designed to validate client backpressure behavior with a stable connection, not to handle mid-operation node restarts that close active connections and disrupt async operations.

---

## Group 18 (Priority 5)

**Status:** [x] **FP** - Restart interrupts artificially blocked async operation

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
Caused by: java.lang.InterruptedException
	at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireSharedInterruptibly(AbstractQueuedSynchronizer.java)
	at java.base/java.util.concurrent.CountDownLatch.await(CountDownLatch.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.ExecutionException: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
	at java.base/java.util.concurrent.FutureTask.report(FutureTask.java:122)
	at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:191)
	at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected.readsDuringBootstrapTest(ReadsDuringBootstrapTest_RestartInjected.java:141)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
	at org.apache.cassandra.utils.Throwables.unchecked(Throwables.java:307)
	at org.apache.cassandra.utils.Throwables.throwAsUncheckedException(Throwables.java:318)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.waitOn(IsolatedExecutor.java:285)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java:151)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithResult(Coordinator.java:61)
	at org.apache.cassandra.distributed.api.ICoordinator.execute(ICoordinator.java:32)
	at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected.lambda$readsDuringBootstrapTest$1(ReadsDuringBootstrapTest_RestartInjected.java:92)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.InterruptedException
	at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireSharedInterruptibly(AbstractQueuedSynchronizer.java:1048)
	at java.base/java.util.concurrent.CountDownLatch.await(CountDownLatch.java:230)
	at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected$BB.getCachedReplicas(ReadsDuringBootstrapTest_RestartInjected.java:170)
	at org.apache.cassandra.locator.AbstractReplicationStrategy.getCachedReplicas(AbstractReplicationStrategy.java)
	at org.apache.cassandra.locator.AbstractReplicationStrategy.getNaturalReplicas(AbstractReplicationStrategy.java:98)
	at org.apache.cassandra.locator.AbstractReplicationStrategy.getNaturalReplicasForToken(AbstractReplicationStrategy.java:90)
	at org.apache.cassandra.locator.ReplicaLayout.forTokenReadLiveSorted(ReplicaLayout.java:331)
	at org.apache.cassandra.locator.ReplicaPlans.forRead(ReplicaPlans.java:723)
	at org.apache.cassandra.service.reads.AbstractReadExecutor.getReadExecutor(AbstractReadExecutor.java:196)
	at org.apache.cassandra.service.StorageProxy.fetchRows(StorageProxy.java:2119)
	at org.apache.cassandra.service.StorageProxy.readRegular(StorageProxy.java:2011)
	at org.apache.cassandra.service.StorageProxy.read(StorageProxy.java:1885)
	at org.apache.cassandra.db.SinglePartitionReadCommand$Group.execute(SinglePartitionReadCommand.java:1285)
	at org.apache.cassandra.cql3.statements.SelectStatement.execute(SelectStatement.java:427)
	at org.apache.cassandra.cql3.statements.SelectStatement.execute(SelectStatement.java:340)
	at org.apache.cassandra.cql3.statements.SelectStatement.execute(SelectStatement.java:108)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:67)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:47)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java:94)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java:61)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected`
- Test Method: `readsDuringBootstrapTest`
- Position: `after_bootstrap_and_join`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `023-25a41f1b`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected`
- Test Method: `readsDuringBootstrapTest`
- Position: `after_cache_population`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `026-af60fba8`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** FP (False Positive)

**Root Cause:**
The restart injection interrupts an artificially blocked async operation, causing an `InterruptedException`. This is a false positive because the failure is a direct consequence of restarting a node while it has an in-flight operation that has been intentionally blocked by the test's ByteBuddy instrumentation.

**Detailed Analysis:**

1. **Test Design and ByteBuddy Instrumentation (lines 149-173):**
   The test uses ByteBuddy to intercept the `AbstractReplicationStrategy.getCachedReplicas()` method on Node 1:
   ```java
   // Line 165-172
   public static EndpointsForRange getCachedReplicas(long ringVersion, Token t,
                                                     @FieldValue("keyspaceName") String keyspaceName,
                                                     @SuperCall Callable<EndpointsForRange> zuper) throws Exception
   {
       if (keyspaceName.equals(KEYSPACE) && block.get())
           latch.await();  // Blocks here waiting on CountDownLatch
       return zuper.call();
   }
   ```

2. **Test Execution Flow:**
   - **Line 83:** `cluster.get(1).runOnInstance(() -> BB.block.set(true))` - Enables blocking on Node 1
   - **Line 92:** `Future<?> read = es.submit(() -> cluster.coordinator(1).execute(query, ...))` - Submits async read query on Node 1
     - This read query executes and hits the ByteBuddy interceptor
     - The interceptor calls `latch.await()` at line 170, blocking the thread
   - **Line 102:** `bootstrapAndJoinNode(cluster)` - Bootstraps new node 4
   - **Line 104-109:** **RESTART INJECTION at position "after_bootstrap_and_join"**
     - Restarts node at index 0 (which is Node 1 in 0-based indexing)
     - Node 1 is the SAME node executing the blocked read operation!
   - **Line 120:** `cluster.get(1).runOnInstance(() -> BB.block.set(false))` - (After restart) Disables blocking
   - **Line 140:** `cluster.get(1).runOnInstance(() -> BB.latch.countDown())` - Counts down latch
   - **Line 141:** `read.get()` - Attempts to get result, but fails with InterruptedException

3. **Why the InterruptedException Occurs:**
   When the restart framework restarts Node 1 at line 104-109:
   - The node's instance is shut down
   - All thread pools and executors are terminated
   - Any threads waiting on operations (like the `latch.await()` at line 170) are interrupted
   - This causes an `InterruptedException` to be thrown in the read execution thread
   - The exception propagates through the call stack and is wrapped in `ExecutionException` when `read.get()` is called

4. **Node Indexing Verification:**
   - Cassandra distributed tests use 1-based indexing: `cluster.get(1)` = Node 1, `cluster.coordinator(1)` = Node 1
   - Restart framework uses 0-based indexing: `withIndex(0)` = Node 1 (first node)
   - Therefore, the restart at line 104-109 restarts the same node (Node 1) that's executing the blocked read

5. **Why This is a False Positive:**
   - The test artificially blocks read operations using ByteBuddy instrumentation
   - The restart happens on the node that has an in-flight, intentionally blocked operation
   - Restarting a node naturally terminates all its operations and interrupts waiting threads
   - This is expected behavior, not a bug in Cassandra's source code or test logic
   - The restart position "after_bootstrap_and_join" is semantically inappropriate because it restarts the coordinator node while it has an ongoing async operation
   - The test design assumes no restarts occur between line 92 (read submission) and line 140 (latch countdown)

**Relevant Code Locations:**
- Test file: `test/distributed/org/apache/cassandra/distributed/test/ring/ReadsDuringBootstrapTest_RestartInjected.java`
- Blocking interceptor: Lines 165-172
- Async read submission: Line 92
- Problematic restart injection: Lines 104-109
- Latch countdown (expected to unblock): Line 140
- Failure point: Line 141 (`read.get()`)

**Conclusion:**
This is not a bug in Cassandra. The failure occurs because restart injection disrupts the test's carefully orchestrated timing by restarting the node that is executing an artificially blocked operation. The restart causes thread interruption, which is the correct and expected behavior when shutting down a node with in-flight operations.

---

## Group 27 (Priority 5)

**Status:** [x] **TEST-BUG** - Restarting the same node that is executing an async repair operation

**Execution Count:** 1

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
Caused by: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.checkInterrupted(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.awaitUntil(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.awaitUntil(Awaitable.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.awaitUntil(Awaitable.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$Defaults.await(Awaitable.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$AbstractAwaitable.await(Awaitable.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.ExecutionException: java.lang.RuntimeException: java.lang.InterruptedException
	at java.base/java.util.concurrent.FutureTask.report(FutureTask.java:122)
	at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:191)
	at org.apache.cassandra.distributed.test.PreviewRepairTest_RestartInjected.testFinishingNonIntersectingIncRepairDuringPreview(PreviewRepairTest_RestartInjected.java:486)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: java.lang.InterruptedException
	at org.apache.cassandra.distributed.test.PreviewRepairTest_RestartInjected.lambda$repair$6323037e$1(PreviewRepairTest_RestartInjected.java:819)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.checkInterrupted(WaitQueue.java:361)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.awaitUntil(WaitQueue.java:349)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.awaitUntil(Awaitable.java:314)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.awaitUntil(Awaitable.java:346)
	at org.apache.cassandra.utils.concurrent.Awaitable$Defaults.await(Awaitable.java:114)
	at org.apache.cassandra.utils.concurrent.Awaitable$AbstractAwaitable.await(Awaitable.java:210)
	at org.apache.cassandra.distributed.test.PreviewRepairTest_RestartInjected.lambda$repair$6323037e$1(PreviewRepairTest_RestartInjected.java:815)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.PreviewRepairTest_RestartInjected`
- Test Method: `testFinishingNonIntersectingIncRepairDuringPreview`
- Position: `testNonIntersect_after_range_repair`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `042-54060778`

### Debug Analysis

**Reproduced:** Yes (Test 1 configuration)

**Classification:** TEST-BUG

**Root Cause:**

The test restarts node 1 (index 0) while an asynchronous repair operation is actively running **inside** that same node's instance, causing the repair's waiting threads to be interrupted during the node's graceful shutdown. This is identical to Group 23.

**Detailed Analysis:**

1. **Test Flow (PreviewRepairTest_RestartInjected.java:426-497):**
   - Line 472: Submits an async repair operation to run on node 1 (index 0):
     ```java
     Future<RepairResult> repairStatusFuture = es.submit(() ->
         cluster.get(1).callOnInstance(repair(options(true, false, localRanges.get(0)))));
     ```
   - The `callOnInstance()` method runs the repair callable **inside node 1's JVM/instance**
   - Line 473: Test waits for the preview repair to start
   - Line 475: Another non-preview repair completes successfully on node 1
   - **Line 478-483: RESTART is injected at position `testNonIntersect_after_range_repair`**
     - Restarts node at `withIndex(0)` (which is node 1)
     - This is the **SAME NODE** that's running the async repair from line 472
   - Line 485: Signals to continue the preview repair (unblocks message filter)
   - Line 486: Tries to get the result from `repairStatusFuture` → **FAILS with InterruptedException**

2. **Why the Restart Causes the Failure:**

   The `repair()` function (PreviewRepairTest_RestartInjected.java:794-823) runs inside node 1's instance and waits for repair completion:
   ```java
   // Line 815
   await.await(1, MINUTES);
   ```

   When node 1 is shut down during restart (line 478-483):
   - All threads in node 1's instance are interrupted as part of the graceful shutdown process
   - The thread waiting at `await.await()` receives an `InterruptedException`
   - The exception is caught at line 817 and wrapped in a `RuntimeException` (line 819)
   - This bubbles up through the future chain to line 486, where `.get()` throws `ExecutionException`

3. **Why This is a TEST-BUG:**

   - **Not a Cassandra bug**: Thread interruption during graceful shutdown is correct and expected behavior
   - **Test design flaw**: The test creates an impossible scenario:
     1. Starts an async operation on node 1 (line 472)
     2. Restarts node 1 while the operation is still running (line 478-483)
     3. Expects to retrieve the operation's result after restart (line 486)
   - **Fundamental issue**: `callOnInstance()` runs code **inside the node's JVM**. When that node is restarted, its threads are terminated
   - **Inappropriate restart position**: The restart at `testNonIntersect_after_range_repair` happens while the async repair from line 472 is still active and waiting

4. **Similarity to Other Groups:**

   This is identical in nature to **Group 23**, which was classified as TEST-BUG with the description "Restarting the same node that is executing an async operation". The pattern is the same:
   - Submit async operation to run on node X
   - Restart node X while operation is in progress
   - Operation fails with InterruptedException when node shuts down
   - This is expected behavior, not a bug

**Relevant Code References:**
- Test method: `test/distributed/org/apache/cassandra/distributed/test/PreviewRepairTest_RestartInjected.java:426-497`
- Async repair submission: `PreviewRepairTest_RestartInjected.java:472`
- Restart injection: `PreviewRepairTest_RestartInjected.java:478-483`
- Repair function with await: `PreviewRepairTest_RestartInjected.java:815`
- Exception handling: `PreviewRepairTest_RestartInjected.java:817-819`

**Conclusion:**

This is not a Cassandra bug. The failure is caused by the test restarting the exact same node that is executing an asynchronous repair operation. When the node shuts down, its threads are interrupted, which is the correct behavior. The test should not restart a node while it's executing an async operation that the test expects to complete.

---
