# Cassandra Debug Tracker - Jan10

## Priority Order Rationale

Groups are ordered by likelihood of being actual bugs:
1. **Highest Priority**: NPE/ClassCircularityError from non-test, non-restarttest code
2. **High Priority**: Configuration/State exceptions from Cassandra core code
3. **Medium Priority**: Connection issues and timeouts
4. **Lowest Priority**: Expected unavailability during restart

---

## HIGH PRIORITY - Likely Bugs

### Group 2: NullPointerException in DatabaseDescriptor.getAllDataFileLocations

[x] FP - Framework interaction bug (see FPs/FP-GROUP-2.md)

**Test Executions**: 13 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at org.apache.cassandra.config.DatabaseDescriptor.getAllDataFileLocations(DatabaseDescriptor.java)
```

**Raw Stack Trace Sample**:
```
java.lang.ExceptionInInitializerError
	at org.apache.cassandra.distributed.action.GossipHelper.lambda$changeGossipState$a1b28875$1(GossipHelper.java:440)
	...
Caused by: java.lang.NullPointerException: Cannot read field "local_system_data_file_directory" because "org.apache.cassandra.config.DatabaseDescriptor.conf" is null
	at org.apache.cassandra.config.DatabaseDescriptor.getAllDataFileLocations(DatabaseDescriptor.java:2772)
	at org.apache.cassandra.service.snapshot.SnapshotManager.<init>(SnapshotManager.java:78)
	at org.apache.cassandra.service.StorageService.<init>(StorageService.java:345)
	at org.apache.cassandra.service.StorageService.<clinit>(StorageService.java:347)
```

**Analysis**: NPE because DatabaseDescriptor.conf is null during StorageService initialization. This indicates the configuration is not properly loaded after restart - potential initialization order bug.

**Test Executions (Examples)**:

1. Test: `BootstrapTest_RestartInjected.bootstrapUnspecifiedFailsOnResumeTest`
   - "position": "after_bootstrap_config"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "015-38102f86"

2. Test: `BootstrapTest_RestartInjected.bootstrapWithoutResumeTest`
   - "position": "after_new_node_startup"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "015-26338469"

3. Test: `PendingWritesTest_RestartInjected.testPendingWrites`
   - "position": "after_bootstrap_startup"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "1"
   - "executionDir": "003-b342bedd"

---

### Group 3: NullPointerException in GCInspector

[x] TEST-BUG - MapMBeanWrapper.queryNames() returns null (see bugs/TEST-BUG-GROUP-3.md)

**Test Executions**: 9 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at org.apache.cassandra.service.GCInspector.<init>(GCInspector.java)
```

**Analysis**: NPE in GCInspector initialization - another initialization order issue after restart.

---

### Group 11: NullPointerException in Objects.requireNonNull

[x] TEST-BUG - ClusterUtils.parseGossipInfo incorrect address parsing (see bugs/TEST-BUG-GROUP-11.md)

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java)
```

**Analysis**: Null value passed where non-null expected - potential uninitialized state.

---

### Group 19: NullPointerException in CompactionManager

[x] BUG - Missing null check in submitMaximal (see bugs/BUG-GROUP-19.md)

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at org.apache.cassandra.db.compaction.CompactionManager.submitMaximal(CompactionManager.java)
```

**Analysis**: `submitMaximal()` doesn't check if `getMaximalTasks()` returns null. The `getMaximalTasks()` method can return null when `runWithCompactionsDisabled()` encounters uninterruptible higher-priority compactions. This is a latent bug exposed by restart testing.

---

### Group 16: ClassCircularityError

[x] FP - JVM classloader race condition (see FPs/FP-GROUP-16.md)

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
java.lang.ClassCircularityError
	at java.base/java.security.AccessController.getInnocuousAcc(AccessController.java)
```

**Analysis**: JVM classloader race condition in `AccessController$AccHolder`. This is a timing-dependent issue that occurs in JVM internal classes during concurrent class loading. Not reproducible after 4 attempts.

---

### Group 15: IllegalStateException in HintsService

[x] FP - Shutdown race condition (see FPs/FP-GROUP-15.md)

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.IllegalStateException
	at org.apache.cassandra.hints.HintsService.write(HintsService.java)
```

**Analysis**: Benign race condition during shutdown. The `HintRunnable` is submitted to `Stage.MUTATION` executor, but when `HintsService.shutdownBlocking()` sets `isShutDown = true`, pending hint tasks are NOT cancelled. When they run and call `HintsService.write()`, it throws `IllegalStateException`. This only occurs during test teardown, has no data integrity impact, and could not be reproduced after 4 attempts.

---

## HIGH-MEDIUM PRIORITY - Configuration/State Issues

### Group 5: RuntimeException in Gossiper.doShadowRound

[x] FP - Invalid restart position when all peers are down (see FPs/FP-GROUP-5.md)

**Test Executions**: 6 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.RuntimeException
	at org.apache.cassandra.gms.Gossiper.doShadowRound(Gossiper.java)
```

**Analysis**: Non-seed node cannot start when all peers are down. This is expected Cassandra behavior per CASSANDRA-13851 - non-seed nodes must contact at least one peer during shadow gossip round.

---

### Group 6: ConfigurationException in StorageService.prepareToJoin

[x] FP - Restart after decommission is invalid (see FPs/FP-GROUP-6.md)

**Test Executions**: 6 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.cassandra.exceptions.ConfigurationException
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java)
```

**Analysis**: Restart framework attempts to restart a node after it has been decommissioned. Cassandra correctly rejects this - a decommissioned node cannot rejoin without `-Dcassandra.override_decommission=true`. This is expected safety behavior.

---

### Group 17: RuntimeException in StorageService.prepareForReplacement

[x] FP - Shared JVM system properties in in-JVM test (see FPs/FP-GROUP-17.md)

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
Caused by: java.lang.RuntimeException
	at org.apache.cassandra.service.StorageService.prepareForReplacement(StorageService.java)
```

**Analysis**: The test sets `REPLACE_ADDRESS` system property for node 3's bootstrap, but when node 1 is restarted, it inherits this property because all nodes share the same JVM. Node 1 was never meant to be a replacement node, and since it already completed bootstrap, the check correctly fails. This is a limitation of the in-JVM test framework.

---

### Group 18: KeyspaceNotDefinedException

[x] FP - Restart framework stale reference refresh bug (see FPs/FP-GROUP-18.md)

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
org.apache.cassandra.db.KeyspaceNotDefinedException
	at org.apache.cassandra.schema.Schema.validateTable(Schema.java)
```

**Analysis**: Keyspace not found in schema. The restart framework's stale reference refresh mechanism incorrectly recreates node3 (via `cluster.bootstrap()` replay) when node2 is restarted. The newly created node3 doesn't have the test keyspace schema, causing the failure when `BootstrapTest.count(cluster)` queries it.

---

### Group 7: IllegalStateException in AbstractCluster.Wrapper.shutdown

[x] FP - Restart adapter attempts to shutdown already-shutdown node (see FPs/FP-GROUP-7.md)

**Test Executions**: 4 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.IllegalStateException
	at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.shutdown(AbstractCluster.java)
```

**Analysis**: The restart positions (`after_stop_all_2`, `after_stop_all`, `after_both_nodes_shutdown`) trigger after all nodes have already been shut down via `stopAll(cluster)`. When the restart adapter tries to gracefully restart a node, it attempts to call `shutdown()` on an already-shutdown instance, which correctly throws `IllegalStateException`. The adapter should check `isShutdown()` before attempting shutdown.

---

### Group 10: RuntimeException in DecommissionTest

[x] FP - Restart adapter shifts generation counter (see FPs/FP-GROUP-10.md)

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.RuntimeException
	at org.apache.cassandra.distributed.test.DecommissionTest$BB.prepareUnbootstrapStreaming(DecommissionTest.java)
```

**Analysis**: The test uses ByteBuddy to intercept `prepareUnbootstrapStreaming()` with generation-dependent logic. The restart adapter injects an extra restart that shifts the generation counter, causing the ByteBuddy interceptor to be installed again when it shouldn't be. This is a framework interaction issue, not a Cassandra bug.

---

## MEDIUM PRIORITY - Availability/Timeout Issues

### Group 4: ConditionTimeoutException (Awaitility)

[x] FP - Test await condition assumes counter never resets (see FPs/FP-GROUP-4.md)

**Test Executions**: 6 failures

**Generalized Stack Trace**:
```
org.awaitility.core.ConditionTimeoutException
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
```

**Analysis**: The test's `insertData()` method has an await condition (`totalHints.getCount() > 0`) that assumes the hint counter is never reset. After node restart, the counter resets to 0, and the persistent hint window feature correctly prevents new hints (because old hints are beyond max_hint_window). The await times out because no new hints are created and the counter stays at 0. This is expected Cassandra behavior - the test design is flawed.

---

### Group 8: CasWriteTimeoutException (Paxos)

[x] FP - In-memory ring state lost after restart (see FPs/FP-GROUP-8.md)

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
org.apache.cassandra.exceptions.CasWriteTimeoutException
	at org.apache.cassandra.service.paxos.Paxos$MaybeFailure.markAndThrowAsTimeoutOrFailure(Paxos.java)
```

**Analysis**: The test sets up specific in-memory ring topology via `addToRingBootstrapping()` to simulate node 4 in "bootstrapping" state. When the restart adapter restarts node 2, this in-memory state is lost and node 2 reverts to seeing node 4 as a full member. This ring view inconsistency causes the Paxos CAS operation to timeout because quorum calculations differ across nodes.

---

### Group 9: UnavailableException

[x] FP - Expected unavailability with RF=1 during node restart (see FPs/FP-GROUP-9.md)

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
org.apache.cassandra.exceptions.UnavailableException
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java)
```

**Analysis**: Counter mutations require a live replica. With RF=1 keyspace, when the single replica node is restarted and not yet detected as "alive" by the FailureDetector, UnavailableException is correctly thrown. This is expected Cassandra behavior - zero fault tolerance with RF=1.

---

### Group 13: TimeoutException in ExecutorUtils

[x] FP - Test uses CyclicBarrier to block queries (see FPs/FP-GROUP-13.md)

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.util.concurrent.TimeoutException
	at org.apache.cassandra.utils.ExecutorUtils.awaitTerminationUntil(ExecutorUtils.java)
```

**Analysis**: The test uses ByteBuddy to inject a `CyclicBarrier` that blocks queries waiting for a second party. The restart position "after_async_queries" triggers while queries are intentionally blocked. The SEPExecutor cannot terminate because worker threads are blocked on the barrier. This is a test-specific blocking scenario, not a normal Cassandra behavior.

---

### Group 14: InterruptedException

[x] FP - Test uses CountDownLatch to block queries (see FPs/FP-GROUP-14.md)

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.InterruptedException
	at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireSharedInterruptibly(AbstractQueuedSynchronizer.java)
```

**Analysis**: The test uses ByteBuddy to intercept `getCachedReplicas()` and block on a `CountDownLatch`. The restart position triggers while queries are intentionally blocked. When the restart adapter gracefully shuts down node 1, worker threads are interrupted, causing the blocked `latch.await()` to throw `InterruptedException`. This is expected JVM behavior when interrupting blocked threads, not a Cassandra bug.

---

## LOW PRIORITY - Connection Issues (Expected During Restart)

### Group 1: NoHostAvailableException

[x] FP - DataStax driver session becomes stale after restart (see FPs/FP-GROUP-1.md)

**Test Executions**: 20 failures

**Generalized Stack Trace**:
```
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException
	at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java)
```

**Raw Stack Trace Sample**:
```
com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
	at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:85)
	at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
	at com.datastax.driver.core.DefaultResultSetFuture.getUninterruptibly(DefaultResultSetFuture.java:295)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:60)
```

**Analysis**: DataStax Java driver session maintains its own host state machine. When the node is restarted, the driver marks the host as DOWN and schedules async reconnection (16-second backoff). Test tries to use the stale session before reconnection completes, resulting in NoHostAvailableException. This is expected driver behavior, not a Cassandra bug.

**Test Executions (Examples)**:

1. Test: `DisableBinaryTest_RestartInjected.testDisallowsNewRequests`
   - "position": "after_transport_stop"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "028-a478f20a"

2. Test: `OverloadTest_RestartInjected.applyClientBackpressure`
   - "position": "after_initial_query"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "019-535dae58"

3. Test: `PrepareBatchStatementsTest_RestartInjected.testPreparedBatch`
   - "position": "after_schema_creation"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "029-27008f8e"

---

### Group 12: TransportException

[x] FP - DataStax driver connection closed during restart (see FPs/FP-GROUP-12.md)

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: com.datastax.driver.core.exceptions.TransportException
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java)
```

**Analysis**: When a node is restarted while async queries are in-flight, the DataStax driver forcibly closes connections and throws `TransportException`. The test's exception handler only handles `OperationTimedOutException`, `ReadTimeoutException`, and `OverloadedException` - it doesn't handle `TransportException`. This is expected driver behavior during node restart, not a Cassandra bug.

---

## Summary Statistics

| Priority | Group IDs | Exception Category | Total Failures | Verdict |
|----------|-----------|-------------------|----------------|---------|
| HIGH | 2, 3, 11, 19 | NullPointerException | 25 | 2=FP, 3=TEST-BUG, 11=TEST-BUG, 19=BUG |
| HIGH | 15, 16 | IllegalState/ClassCircularity | 3 | 15=FP, 16=FP |
| MEDIUM-HIGH | 5, 6, 17, 18 | Configuration/State | 14 | 5=FP, 6=FP, 17=FP, 18=FP |
| MEDIUM | 4, 8, 9, 13, 14 | Timeout/Unavailable | 16 | 4=FP, 8=FP, 9=FP, 13=FP, 14=FP |
| MEDIUM | 7, 10 | Test/Framework | 6 | 7=FP, 10=FP |
| LOW | 1, 12 | Connection Issues | 22 | 1=FP, 12=FP |

**Total Groups**: 19
**Total Failures**: 81
