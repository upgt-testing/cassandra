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

[ ] Not started

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

[ ] Not started

**Test Executions**: 9 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at org.apache.cassandra.service.GCInspector.<init>(GCInspector.java)
```

**Analysis**: NPE in GCInspector initialization - another initialization order issue after restart.

---

### Group 11: NullPointerException in Objects.requireNonNull

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java)
```

**Analysis**: Null value passed where non-null expected - potential uninitialized state.

---

### Group 19: NullPointerException in CompactionManager

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at org.apache.cassandra.db.compaction.CompactionManager.submitMaximal(CompactionManager.java)
```

**Analysis**: NPE during compaction submission - potential uninitialized compaction state.

---

### Group 16: ClassCircularityError

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
java.lang.ClassCircularityError
	at java.base/java.security.AccessController.getInnocuousAcc(AccessController.java)
```

**Analysis**: Class loading circularity - potential classloader state issue during restart.

---

### Group 15: IllegalStateException in HintsService

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.IllegalStateException
	at org.apache.cassandra.hints.HintsService.write(HintsService.java)
```

**Analysis**: HintsService in invalid state - potential initialization/shutdown ordering issue.

---

## HIGH-MEDIUM PRIORITY - Configuration/State Issues

### Group 5: RuntimeException in Gossiper.doShadowRound

[ ] Not started

**Test Executions**: 6 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.RuntimeException
	at org.apache.cassandra.gms.Gossiper.doShadowRound(Gossiper.java)
```

**Analysis**: Gossip protocol shadow round failure - potential cluster state inconsistency.

---

### Group 6: ConfigurationException in StorageService.prepareToJoin

[ ] Not started

**Test Executions**: 6 failures

**Generalized Stack Trace**:
```
Caused by: org.apache.cassandra.exceptions.ConfigurationException
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java)
```

**Analysis**: Node cannot prepare to join cluster - configuration or state mismatch.

---

### Group 17: RuntimeException in StorageService.prepareForReplacement

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
Caused by: java.lang.RuntimeException
	at org.apache.cassandra.service.StorageService.prepareForReplacement(StorageService.java)
```

---

### Group 18: KeyspaceNotDefinedException

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
org.apache.cassandra.db.KeyspaceNotDefinedException
	at org.apache.cassandra.schema.Schema.validateTable(Schema.java)
```

**Analysis**: Keyspace not found in schema - potential schema sync issue after restart.

---

### Group 7: IllegalStateException in AbstractCluster.Wrapper.shutdown

[ ] Not started

**Test Executions**: 4 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.IllegalStateException
	at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.shutdown(AbstractCluster.java)
```

**Analysis**: Cluster wrapper in invalid state during shutdown - test framework issue.

---

### Group 10: RuntimeException in DecommissionTest

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.RuntimeException
	at org.apache.cassandra.distributed.test.DecommissionTest$BB.prepareUnbootstrapStreaming(DecommissionTest.java)
```

**Analysis**: Test-specific failure during decommission scenario.

---

## MEDIUM PRIORITY - Availability/Timeout Issues

### Group 4: ConditionTimeoutException (Awaitility)

[ ] Not started

**Test Executions**: 6 failures

**Generalized Stack Trace**:
```
org.awaitility.core.ConditionTimeoutException
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
```

**Analysis**: Test condition not met within timeout - could be expected during restart.

---

### Group 8: CasWriteTimeoutException (Paxos)

[ ] Not started

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
org.apache.cassandra.exceptions.CasWriteTimeoutException
	at org.apache.cassandra.service.paxos.Paxos$MaybeFailure.markAndThrowAsTimeoutOrFailure(Paxos.java)
```

**Analysis**: Paxos CAS write timeout - potential issue with consensus after restart.

---

### Group 9: UnavailableException

[ ] Not started

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
org.apache.cassandra.exceptions.UnavailableException
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java)
```

**Analysis**: Not enough replicas available - expected during restart but needs verification.

---

### Group 13: TimeoutException in ExecutorUtils

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.util.concurrent.TimeoutException
	at org.apache.cassandra.utils.ExecutorUtils.awaitTerminationUntil(ExecutorUtils.java)
```

---

### Group 14: InterruptedException

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.InterruptedException
	at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireSharedInterruptibly(AbstractQueuedSynchronizer.java)
```

---

## LOW PRIORITY - Connection Issues (Expected During Restart)

### Group 1: NoHostAvailableException

[ ] Not started

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

**Analysis**: No hosts available for query - **likely FALSE POSITIVE** during node restart.

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

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: com.datastax.driver.core.exceptions.TransportException
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java)
```

**Analysis**: Connection transport failure - expected during restart.

---

## Summary Statistics

| Priority | Group IDs | Exception Category | Total Failures | Verdict |
|----------|-----------|-------------------|----------------|---------|
| HIGH | 2, 3, 11, 19 | NullPointerException | 25 | Likely Bugs |
| HIGH | 15, 16 | IllegalState/ClassCircularity | 3 | Likely Bugs |
| MEDIUM-HIGH | 5, 6, 17, 18 | Configuration/State | 14 | State Issues |
| MEDIUM | 4, 8, 9, 13, 14 | Timeout/Unavailable | 16 | Need Inspection |
| MEDIUM | 7, 10 | Test/Framework | 6 | Test Issues |
| LOW | 1, 12 | Connection Issues | 22 | Likely FALSE POSITIVE |

**Total Groups**: 19
**Total Failures**: 81
