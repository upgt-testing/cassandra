# DEBUG TRACKER - Cassandra Dec20 Grouped Failures

This file tracks 19 failure groups ordered by priority for debugging.
Total failures: 1032 non-assertion failures, 208 assertion failures

Priority Legend:
- **HIGH PRIORITY**: NPE/IndexOutOfBounds from NON-restarttest code (likely actual bugs)
- **MEDIUM PRIORITY**: Other runtime exceptions from non-restarttest code
- **LOW PRIORITY**: Failures from restarttest modules, timeouts, configuration issues

---

## HIGH PRIORITY FAILURES (Likely Actual Bugs)

### [FP] Group 3: NullPointerException in UUID.fromString
**Priority: HIGHEST - NPE in core Java/Cassandra code**

**Root Cause:**
```
java.lang.NullPointerException: Cannot invoke "String.length()" because "name" is null
at java.base/java.util.UUID.fromString(UUID.java:237)
at org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected.doHintReappearData(HintDataReappearingTest_RestartInjected.java:279)
```

**Full Stack Trace:**
```
java.lang.NullPointerException: Cannot invoke "String.length()" because "name" is null
	at java.base/java.util.UUID.fromString(UUID.java:237)
	at org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected.doHintReappearData(HintDataReappearingTest_RestartInjected.java:279)
	at org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected.demonstrateHintCausesDataReappearanceWriteTimeout(HintDataReappearingTest_RestartInjected.java:90)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
```

**Example Test Executions (12 total):**
1. Test: `HintDataReappearingTest_RestartInjected.demonstrateHintCausesDataReappearanceWriteTimeout`
   - "position": "after_pause_hints"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "003-6ea8e9de"
2. Test: `HintDataReappearingTest_RestartInjected.demonstrateHintCausesDataReappearanceWriteTimeout`
   - "position": "after_insert_and_filter_reset"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "004-c3a515f0"
3. Test: `HintDataReappearingTest_RestartInjected.demonstrateHintCausesDataReappearanceWriteTimeout`
   - "position": "after_first_flush"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "1"
   - "executionDir": "005-d26ee023"

---

### [TEST-BUG] Group 5: NullPointerException in GCInspector
**Priority: HIGH - NPE in Cassandra core monitoring code**

**Root Cause:**
```
Caused by: java.lang.NullPointerException: Cannot invoke "java.util.Set.iterator()" because the return value of "org.apache.cassandra.utils.MBeanWrapper.queryNames(javax.management.ObjectName, javax.management.QueryExp)" is null
at org.apache.cassandra.service.GCInspector.<init>(GCInspector.java:145)
at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.connect(InternalNodeProbe.java:79)
```

**Full Stack Trace:**
```
org.restarttest.core.RestartException: Restart failed at position after_load_system_tables
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.metric.TableMetricTest_RestartInjected.systemTables(TableMetricTest_RestartInjected.java:83)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:185)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:53)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:31)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
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

**Example Test Executions (9 total):**
1. Test: `TableMetricTest_RestartInjected.systemTables`
   - "position": "after_load_system_tables"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "039-b1422257"
2. Test: `TableMetricTest_RestartInjected.userTables`
   - "position": "after_load_system_tables"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "002-30bec6a1"
3. Test: `TableMetricTest_RestartInjected.userTables`
   - "position": "after_table_create"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "003-334986c3"

---

### [TEST-BUG] Group 11: NullPointerException in ClusterUtils
**Priority: HIGH - NPE in cluster gossip code**

**Root Cause:**
```
Caused by: java.lang.NullPointerException
at java.base/java.util.Objects.requireNonNull(Objects.java:209)
at org.apache.cassandra.distributed.shared.ClusterUtils.parseGossipInfo(ClusterUtils.java:691)
at org.apache.cassandra.distributed.shared.ClusterUtils.gossipInfo(ClusterUtils.java:655)
at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossip(ClusterUtils.java:554)
```

**Full Stack Trace:**
```
org.restarttest.core.RestartException: Restart failed at position after_all_getters_test
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.jmx.JMXFeatureTest_RestartInjected.testShutDownAndRestartInstances(JMXFeatureTest_RestartInjected.java:221)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java:209)
	at org.apache.cassandra.distributed.shared.ClusterUtils.parseGossipInfo(ClusterUtils.java:691)
	at org.apache.cassandra.distributed.shared.ClusterUtils.gossipInfo(ClusterUtils.java:655)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossip(ClusterUtils.java:554)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossipSchemaMatch(ClusterUtils.java:600)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java:90)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java:31)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:116)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
```

**Example Test Executions (2 total):**
1. Test: `JMXFeatureTest_RestartInjected.testShutDownAndRestartInstances`
   - "position": "after_all_getters_test"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "1"
   - "executionDir": "010-901071ff"
2. Test: `JMXGetterCheckTest_RestartInjected.testGetters`
   - "position": "after_getters_test"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "037-acaf07f8"

---

## MEDIUM PRIORITY FAILURES (Runtime Exceptions in Core Code)

### [FP] Group 4: RuntimeException - Unable to gossip with peers
**Priority: MEDIUM - Gossip communication failure**

**Root Cause:**
```
Caused by: java.lang.RuntimeException: Unable to gossip with any peers
at org.apache.cassandra.gms.Gossiper.doShadowRound(Gossiper.java:2075)
at org.apache.cassandra.service.StorageService.checkForEndpointCollision(StorageService.java:866)
at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1181)
```

**Full Stack Trace:**
```
org.restarttest.core.RestartException: Restart failed at position after_node_removal
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.ring.NodeNotInRingTest_RestartInjected.nodeNotInRingTest(NodeNotInRingTest_RestartInjected.java:79)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 3
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:185)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:53)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:31)
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

**Example Test Executions (10 total):**
1. Test: `NodeNotInRingTest_RestartInjected.nodeNotInRingTest`
   - "position": "after_node_removal"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "2"
   - "executionDir": "015-cd6d2ba3"
2. Test: `GossipShutdownTest_RestartInjected.shutdownStayDownTest`
   - "position": "after_filter_setup"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "1"
   - "executionDir": "022-0fa9efae"
3. Test: `HostReplacementTest_RestartInjected.seedGoesDownBeforeDownHost`
   - "position": "after_node_stop"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "2"
   - "executionDir": "005-ba5ca057"

---

### [FP] Group 1: RejectedExecutionException in Netty event executor
**Priority: MEDIUM - Event executor terminated during shutdown**

**Root Cause:**
```
Caused by: java.util.concurrent.RejectedExecutionException: event executor terminated
at io.netty.util.concurrent.SingleThreadEventExecutor.reject(SingleThreadEventExecutor.java:934)
at io.netty.util.concurrent.SingleThreadEventExecutor.offerTask(SingleThreadEventExecutor.java:353)
```

**Full Stack Trace:**
```
org.restarttest.core.RestartException: Restart failed at position after_yaml_config_test
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.guardrails.GuardrailPartitionSizeTest_RestartInjected.testPartitionSize(GuardrailPartitionSizeTest_RestartInjected.java:87)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:185)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:53)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:31)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.util.concurrent.RejectedExecutionException: event executor terminated
	at io.netty.util.concurrent.SingleThreadEventExecutor.reject(SingleThreadEventExecutor.java:934)
	at io.netty.util.concurrent.SingleThreadEventExecutor.offerTask(SingleThreadEventExecutor.java:353)
	at io.netty.util.concurrent.SingleThreadEventExecutor.addTask(SingleThreadEventExecutor.java:346)
	at io.netty.util.concurrent.SingleThreadEventExecutor.execute(SingleThreadEventExecutor.java:836)
	at io.netty.util.concurrent.SingleThreadEventExecutor.lazyExecute0(SingleThreadEventExecutor.java:831)
	at io.netty.util.concurrent.SingleThreadEventExecutor.lazyExecute(SingleThreadEventExecutor.java:822)
	at io.netty.util.concurrent.AbstractScheduledEventExecutor.schedule(AbstractScheduledEventExecutor.java:292)
	at io.netty.util.concurrent.AbstractScheduledEventExecutor.scheduleAtFixedRate(AbstractScheduledEventExecutor.java:239)
	at org.apache.cassandra.net.OutboundConnection.setDisconnected(OutboundConnection.java:1406)
	at org.apache.cassandra.net.OutboundConnection.<init>(OutboundConnection.java:319)
	at org.apache.cassandra.net.OutboundConnections.<init>(OutboundConnections.java:86)
	at org.apache.cassandra.net.OutboundConnections.tryRegister(OutboundConnections.java:104)
	at org.apache.cassandra.net.MessagingService.getOutbound(MessagingService.java:666)
	at org.apache.cassandra.net.MessagingService.doSend(MessagingService.java:470)
	at org.apache.cassandra.net.OutboundSink.accept(OutboundSink.java:70)
	at org.apache.cassandra.net.MessagingService.send(MessagingService.java:462)
	at org.apache.cassandra.net.MessagingService.send(MessagingService.java:437)
	at org.apache.cassandra.gms.Gossiper.stop(Gossiper.java:2261)
	at org.apache.cassandra.gms.Gossiper.stopShutdownAndWait(Gossiper.java:2551)
	at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$20(Instance.java:883)
	at org.apache.cassandra.distributed.impl.Instance.lambda$parallelRun$52(Instance.java:1162)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)
```

**Example Test Executions (Many - same test pattern):**
1. Test: `GuardrailPartitionSizeTest_RestartInjected.testPartitionSize`
   - "position": "after_yaml_config_test"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "002-de0d183e"
2. Test: `GuardrailPartitionSizeTest_RestartInjected.testPartitionSize`
   - "position": "after_table_create"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "003-a10c3aea"
3. Test: `GuardrailPartitionSizeTest_RestartInjected.testPartitionSize`
   - "position": "after_table_create"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "004-a10c3aea5da2"

---

### [FP] Group 9: RejectedExecutionException - isolatedExecutor shutdown
**Priority: MEDIUM - Executor shutdown during test**

**Root Cause:**
```
java.util.concurrent.RejectedExecutionException: isolatedExecutor has shut down
at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java:49)
at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java:833)
```

**Full Stack Trace:**
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

**Example Test Executions (2 total):**
1. Test: `GroupByTest_RestartInjected.testGroupWithDeletesAndPaging`
   - "position": "after_inserts"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "030-899d8831"
2. Test: `HintsServiceMetricsTest_RestartInjected.testHintsServiceMetrics`
   - "position": "after_first_half_writes"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "012-7913c69e"

---

### [FP] Group 10: RuntimeException - Simulated decommission error
**Priority: MEDIUM - Test-induced error**

**Root Cause:**
```
java.lang.RuntimeException: simulated error in prepareUnbootstrapStreaming
at org.apache.cassandra.distributed.test.DecommissionTest$BB.prepareUnbootstrapStreaming(DecommissionTest.java:208)
at org.apache.cassandra.service.StorageService.prepareUnbootstrapStreaming(StorageService.java)
```

**Full Stack Trace:**
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

**Example Test Executions (2 total):**
1. Test: `DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart`
   - "position": "after_failed_decommission"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "025-a7a8df70"
2. Test: `DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart`
   - "position": "after_manual_restart"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "026-5be8ad0d"

---

### [FP] Group 14: InterruptedException during bootstrap reads
**Priority: MEDIUM - Thread interruption issue**

**Root Cause:**
```
Caused by: java.lang.InterruptedException
at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireSharedInterruptibly(AbstractQueuedSynchronizer.java:1048)
at java.base/java.util.concurrent.CountDownLatch.await(CountDownLatch.java:230)
at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected$BB.getCachedReplicas(ReadsDuringBootstrapTest_RestartInjected.java:170)
```

**Full Stack Trace:**
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

**Example Test Executions (2 total):**
1. Test: `ReadsDuringBootstrapTest_RestartInjected.readsDuringBootstrapTest`
   - "position": "after_bootstrap_and_join"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "013-25a41f1b"
2. Test: `ReadsDuringBootstrapTest_RestartInjected.readsDuringBootstrapTest`
   - "position": "after_cache_population"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "016-af60fba8"

---

### [FP] Group 17: RuntimeException - Cannot replace bootstrapped node
**Priority: MEDIUM - Configuration/state issue**

**Root Cause:**
```
Caused by: java.lang.RuntimeException: Cannot replace address with a node that is already bootstrapped
at org.apache.cassandra.service.StorageService.prepareForReplacement(StorageService.java:734)
at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1159)
```

**Full Stack Trace:**
```
org.restarttest.core.RestartException: Restart failed at position after_bootstrap
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.MigrationCoordinatorTest_RestartInjected.replaceNode(MigrationCoordinatorTest_RestartInjected.java:114)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:185)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:53)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:31)
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

**Example Test Executions (1 total):**
1. Test: `MigrationCoordinatorTest_RestartInjected.replaceNode`
   - "position": "after_bootstrap"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "027-3d9de488"

---

### [FP] Group 19: IllegalStateException - Instance class loader closed
**Priority: MEDIUM - Class loader lifecycle issue**

**Root Cause:**
```
java.lang.IllegalStateException: Can't load org.apache.cassandra.distributed.impl.FileLogAction$FileLineIterator. Instance class loader is already closed.
at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClassInternal(InstanceClassLoader.java:118)
at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClass(InstanceClassLoader.java:112)
```

**Full Stack Trace:**
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

**Example Test Executions (1 total):**
1. Test: `JVMDTestTest_RestartInjected.instanceLogs`
   - "position": "after_exception_trigger"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "1"
   - "executionDir": "034-7aca286b"

---

## LOW PRIORITY FAILURES (Expected/Configuration/Timeout Issues)

### [FP] Group 2: NoHostAvailableException - Transport unavailable
**Priority: LOW - Expected when transport is disabled**

**Root Cause:**
```
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java:285)
```

**Full Stack Trace:**
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

**Example Test Executions (20 total):**
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
3. Test: `OverloadTest_RestartInjected.applyClientBackpressure`
   - "position": "after_enable_slow_select"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "020-14c36077"

---

### [FP] Group 6: ConfigurationException - Decommissioned node rejoin
**Priority: LOW - Expected configuration check**

**Root Cause:**
```
Caused by: org.apache.cassandra.exceptions.ConfigurationException: This node was decommissioned and will not rejoin the ring unless -Dcassandra.override_decommission=true has been set, or all existing data is removed and the node is bootstrapped again
at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1144)
```

**Full Stack Trace:**
```
org.restarttest.core.RestartException: Restart failed at position after_decommission_tests
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected.testDecommission(DecommissionTest_RestartInjected.java:129)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:185)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:53)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:31)
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

**Example Test Executions (4 total):**
1. Test: `DecommissionTest_RestartInjected.testDecommission`
   - "position": "after_decommission_tests"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "037-dabc4255"
2. Test: `HintedHandoffAddRemoveNodesTest_RestartInjected.shouldAvoidHintTransferOnDecommission`
   - "position": "after_decommission"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "023-da6c8e70"
3. Test: `DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart`
   - "position": "after_successful_decommission"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "027-76bbfef7"

---

### [FP] Group 7: IllegalStateException - Shutdown instance delegate null
**Priority: LOW - Expected after shutdown**

**Root Cause:**
```
Caused by: java.lang.IllegalStateException: Can't use shutdown instances, delegate is null
at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.delegate(AbstractCluster.java:285)
at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.nodetoolResult(AbstractCluster.java:474)
```

**Full Stack Trace:**
```
org.restarttest.core.RestartException: Restart failed at position after_stop_all_2
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.hostreplacement.HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNodeAndOtherNodeStartsAfter(HostReplacementOfDownedClusterTest_RestartInjected.java:229)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:185)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:53)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:31)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.IllegalStateException: Can't use shutdown instances, delegate is null
	at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.delegate(AbstractCluster.java:285)
	at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.nodetoolResult(AbstractCluster.java:474)
	at org.apache.cassandra.distributed.api.IInstance.nodetoolResult(IInstance.java:74)
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:158)
```

**Example Test Executions (3 total):**
1. Test: `HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNodeAndOtherNodeStartsAfter`
   - "position": "after_stop_all_2"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "004-211b7df2"
2. Test: `HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNode`
   - "position": "after_stop_all"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "004-b9fd3ca8"
3. Test: `RestartTest_RestartInjected.test`
   - "position": "after_both_nodes_shutdown"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "020-6043a934"

---

### [FP] Group 8: ConditionTimeoutException - Hints window timeout
**Priority: LOW - Timeout waiting for condition**

**Root Cause:**
```
org.awaitility.core.ConditionTimeoutException: Condition with org.apache.cassandra.distributed.test.AbstractHintWindowTest was not fulfilled within 10 seconds.
at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:165)
```

**Full Stack Trace:**
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

**Example Test Executions (2 total):**
1. Test: `HintsPersistentWindowTest_RestartInjected.testPersistentHintWindow`
   - "position": "after_node_startup"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "009-b5edb33b"
2. Test: `HintsPersistentWindowTest_RestartInjected.testPersistentHintWindow`
   - "position": "after_hints_size_assertion"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "010-11b54bd5"

---

### [FP] Group 12: TransportException - Connection closed
**Priority: LOW - Connection closed during restart**

**Root Cause:**
```
Caused by: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java:1460)
```

**Full Stack Trace:**
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

**Example Test Executions (2 total):**
1. Test: `OverloadTest_RestartInjected.applyClientBackpressure`
   - "position": "after_queries_submitted"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "021-4a5e2787"
2. Test: `OverloadTest_RestartInjected.clientBackpressureDisabled`
   - "position": "after_queries_submitted"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "021-263631b1"

---

### [FP] Group 13: TimeoutException - Executor termination timeout
**Priority: LOW - Timeout during shutdown**

**Root Cause:**
```
Caused by: java.util.concurrent.TimeoutException: org.apache.cassandra.concurrent.SEPExecutor@170cb0e6 did not terminate on time
at org.apache.cassandra.utils.ExecutorUtils.awaitTerminationUntil(ExecutorUtils.java:111)
at org.apache.cassandra.utils.ExecutorUtils.awaitTermination(ExecutorUtils.java:100)
at org.apache.cassandra.concurrent.Stage.shutdownAndWait(Stage.java:193)
```

**Full Stack Trace:**
```
org.restarttest.core.RestartException: Restart failed at position after_async_queries
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.QueriesTableTest_RestartInjected.shouldExposeReadsAndWrites(QueriesTableTest_RestartInjected.java:105)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:185)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:53)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:31)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.RuntimeException: java.util.concurrent.TimeoutException: org.apache.cassandra.concurrent.SEPExecutor@170cb0e6 did not terminate on time
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
Caused by: java.util.concurrent.TimeoutException: org.apache.cassandra.concurrent.SEPExecutor@170cb0e6 did not terminate on time
	at org.apache.cassandra.utils.ExecutorUtils.awaitTerminationUntil(ExecutorUtils.java:111)
	at org.apache.cassandra.utils.ExecutorUtils.awaitTermination(ExecutorUtils.java:100)
	at org.apache.cassandra.concurrent.Stage.shutdownAndWait(Stage.java:193)
	at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$42(Instance.java:915)
	at org.apache.cassandra.distributed.impl.Instance.lambda$parallelRun$52(Instance.java:1162)
```

**Example Test Executions (2 total):**
1. Test: `QueriesTableTest_RestartInjected.shouldExposeReadsAndWrites`
   - "position": "after_async_queries"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "030-9491c198"
2. Test: `QueriesTableTest_RestartInjected.shouldExposeCAS`
   - "position": "cas_after_async_update"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "030-2c8171fc"

---

### [FP] Group 15: UnavailableException - Consistency level failure
**Priority: LOW - Expected consistency failure**

**Root Cause:**
```
org.apache.cassandra.exceptions.UnavailableException: Cannot achieve consistency level ALL
at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:37)
at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicas(ReplicaPlans.java:193)
```

**Full Stack Trace:**
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

**Example Test Executions (1 total):**
1. Test: `NodeNotInRingTest_RestartInjected.nodeNotInRingTest`
   - "position": "after_first_populate"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "1"
   - "executionDir": "017-9a74b21a"

---

### [FP] Group 16: TimeoutException - Streaming timeout
**Priority: LOW - Timeout waiting for stream**

**Root Cause:**
```
Caused by: java.util.concurrent.TimeoutException
at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:204)
at org.awaitility.core.Uninterruptibles.getUninterruptibly(Uninterruptibles.java:101)
```

**Full Stack Trace:**
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

**Example Test Executions (1 total):**
1. Test: `StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected.failureDueToSessionTimeout`
   - "position": "after_trigger_streaming"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "020-63709395"

---

### [FP] Group 18: WriteTimeoutException - Write timeout
**Priority: LOW - Timeout during write**

**Root Cause:**
```
Caused by: com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency LOCAL_ONE (1 replica were required but only 0 acknowledged the write)
at com.datastax.driver.core.Responses$Error$1.decode(Responses.java:91)
```

**Full Stack Trace:**
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

**Example Test Executions (1 total):**
1. Test: `QueriesTableTest_RestartInjected.shouldExposeReadsAndWrites`
   - "position": "after_table_create"
   - "target": "node"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "029-86bc4cdf"

---

## Summary

**Total Groups: 19**

**High Priority (Likely Bugs): 3 groups**
- Group 3: NullPointerException in UUID.fromString (12 executions)
- Group 5: NullPointerException in GCInspector (9 executions)
- Group 11: NullPointerException in ClusterUtils (2 executions)

**Medium Priority (Runtime Exceptions): 7 groups**
- Group 4: RuntimeException - Unable to gossip (10 executions)
- Group 1: RejectedExecutionException in Netty (many executions)
- Group 9: RejectedExecutionException - isolatedExecutor (2 executions)
- Group 10: RuntimeException - Simulated error (2 executions)
- Group 14: InterruptedException during bootstrap (2 executions)
- Group 17: RuntimeException - Cannot replace node (1 execution)
- Group 19: IllegalStateException - ClassLoader closed (1 execution)

**Low Priority (Expected/Timeouts/Config): 9 groups**
- Group 2: NoHostAvailableException (20 executions)
- Group 6: ConfigurationException - Decommissioned node (4 executions)
- Group 7: IllegalStateException - Shutdown delegate (3 executions)
- Group 8: ConditionTimeoutException (2 executions)
- Group 12: TransportException - Connection closed (2 executions)
- Group 13: TimeoutException - Executor termination (2 executions)
- Group 15: UnavailableException (1 execution)
- Group 16: TimeoutException - Streaming (1 execution)
- Group 18: WriteTimeoutException (1 execution)
